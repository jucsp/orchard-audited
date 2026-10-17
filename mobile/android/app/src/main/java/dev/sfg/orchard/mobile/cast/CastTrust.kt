// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Security exception raised when the trust policy for a Cast device is violated
 * (for example a certificate fingerprint mismatch after the first use).
 */
class CastSecurityException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)

private val IPV4_PATTERN: Regex = Regex("(0|[1-9]\\d{0,2})(\\.(0|[1-9]\\d{0,2})){3}")

/**
 * Strict private IP filter for the Cast transport (CAST-INV-7).
 *
 * Only canonical dotted-quad literals in RFC 1918 or link-local IPv4 ranges are
 * accepted. Public IPs, CGNAT (100.64.0.0/10), loopback and multicast are
 * rejected. Leading zeros are rejected because some resolvers read them as
 * octal. The returned address is built from raw bytes, so no DNS lookup happens.
 */
fun parsePrivateIpv4(ip: String): Inet4Address? {
    if (!IPV4_PATTERN.matches(ip)) return null
    val octets: List<Int> = ip.split('.').map { it.toInt() }
    if (octets.any { it > 255 }) return null

    val first = octets[0]
    val second = octets[1]
    val inPrivateRange = when {
        first == 10 -> true
        first == 172 && second in 16..31 -> true
        first == 192 && second == 168 -> true
        first == 169 && second == 254 -> true
        else -> false
    }
    if (!inPrivateRange) return null
    return InetAddress.getByAddress(octets.map { it.toByte() }.toByteArray()) as? Inet4Address
}

fun isPrivateIp(ip: String): Boolean = parsePrivateIpv4(ip) != null

/**
 * Pluggable persistence for the Trust On First Use (TOFU) pin map.
 */
interface CastTrustStore {
    fun getFingerprint(deviceId: String): String?
    fun setFingerprint(deviceId: String, sha256Fingerprint: String)
}

/**
 * In-memory implementation suitable for a single process (and for unit tests).
 */
class InMemoryCastTrustStore : CastTrustStore {
    private val fingerprints = ConcurrentHashMap<String, String>()

    override fun getFingerprint(deviceId: String): String? = fingerprints[deviceId]

    override fun setFingerprint(deviceId: String, sha256Fingerprint: String) {
        fingerprints[deviceId] = sha256Fingerprint
    }
}

/**
 * X509TrustManager that never consults platform CAs. Instead it pins the
 * SHA-256 fingerprint of the self-signed certificate presented by a Cast
 * device (Trust On First Use). If the fingerprint changes after the first
 * use, the handshake is aborted with [CastSecurityException].
 */
class CastTrustManager(
    private val deviceId: String,
    private val trustStore: CastTrustStore
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // The Cast device acts as the TLS server; client certificates are unused.
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate: X509Certificate = chain?.firstOrNull()
            ?: throw CastSecurityException("Empty certificate chain for device $deviceId")

        val fingerprint: String = sha256Fingerprint(certificate)
        val stored: String? = trustStore.getFingerprint(deviceId)

        when {
            stored == null -> trustStore.setFingerprint(deviceId, fingerprint)
            stored.equals(fingerprint, ignoreCase = true) -> Unit
            else -> throw CastSecurityException(
                "Certificate fingerprint mismatch for device $deviceId"
            )
        }
    }

    private fun sha256Fingerprint(certificate: X509Certificate): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
        return digest.joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xff)
        }
    }
}

/**
 * Builds a TLS [SSLContext] initialized with [CastTrustManager] and a fresh
 * [SecureRandom]. The context deliberately contains no CA trust store.
 */
fun createSslContext(deviceId: String, trustStore: CastTrustStore): SSLContext {
    val context: SSLContext = SSLContext.getInstance("TLS")
    context.init(
        null,
        arrayOf<TrustManager>(CastTrustManager(deviceId, trustStore)),
        SecureRandom()
    )
    return context
}
