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

/** SHA-256 of raw bytes as an uppercase hex string (used for TLS and device-key pins). */
fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte ->
        "%02X".format(byte.toInt() and 0xff)
    }
}

/** Short form (8 hex chars) shown in trust dialogs so full fingerprints stay out of the UI. */
fun shortPin(pin: String?): String? = pin?.take(8)?.uppercase()

/**
 * Security exception raised when the trust policy for a Cast device is violated
 * (for example a certificate fingerprint mismatch after the first use).
 */
open class CastSecurityException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)

/** Raised when the device key pinned for a Cast device changed or can no longer be proven. */
class CastDeviceKeyChangedException(
    val previousPinShort: String?,
    val newPinShort: String?,
) : CastSecurityException(
    "Cast device key changed (previous ${previousPinShort ?: "unknown"}, new ${newPinShort ?: "unknown"})"
)

/** Raised when `deviceauth` verification failed and no pinned device key could be confirmed. */
class CastDeviceAuthFailedException(
    val reason: String,
) : CastSecurityException("Cast device authentication failed: $reason")

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

    fun getDeviceKeyPin(deviceId: String): String? = null
    fun setDeviceKeyPin(deviceId: String, pinDk: String) = Unit
    fun getLegacyPin(deviceId: String): String? = getFingerprint(deviceId)
    fun setLegacyPin(deviceId: String, pin: String) = setFingerprint(deviceId, pin)

    /** Atomically promotes a v1 TLS pin to a v2 device-key pin; implementations should drop the v1 pin. */
    fun migrateV1ToV2(deviceId: String, pinDk: String) = setDeviceKeyPin(deviceId, pinDk)

    /** Removes every pin (v1 and v2) for the device, restoring first-use behavior. */
    fun forget(deviceId: String) = Unit
}

/**
 * In-memory implementation suitable for a single process (and for unit tests).
 */
class InMemoryCastTrustStore : CastTrustStore {
    private val fingerprints = ConcurrentHashMap<String, String>()
    private val deviceKeyPins = ConcurrentHashMap<String, String>()

    override fun getFingerprint(deviceId: String): String? = fingerprints[deviceId]

    override fun setFingerprint(deviceId: String, sha256Fingerprint: String) {
        fingerprints[deviceId] = sha256Fingerprint
    }

    override fun getDeviceKeyPin(deviceId: String): String? = deviceKeyPins[deviceId]

    override fun setDeviceKeyPin(deviceId: String, pinDk: String) {
        deviceKeyPins[deviceId] = pinDk
    }

    override fun migrateV1ToV2(deviceId: String, pinDk: String) {
        deviceKeyPins[deviceId] = pinDk
        fingerprints.remove(deviceId)
    }

    override fun forget(deviceId: String) {
        fingerprints.remove(deviceId)
        deviceKeyPins.remove(deviceId)
    }
}

/**
 * X509TrustManager that never consults platform CAs. It accepts the TLS leaf and
 * exposes its DER for the `deviceauth` exchange; the actual trust decision is
 * applied later by [evaluateDeviceAuth], which pins the device key (CAST-ROT-INV-1).
 */
class CastTrustManager(
    private val deviceId: String,
    private val trustStore: CastTrustStore
) : X509TrustManager {

    /** DER of the TLS leaf accepted in [checkServerTrusted], exposed for `deviceauth` verification. */
    var peerLeafCertificateDer: ByteArray? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // The Cast device acts as the TLS server; client certificates are unused.
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate: X509Certificate = chain?.firstOrNull()
            ?: throw CastSecurityException("Empty certificate chain for device $deviceId")
        peerLeafCertificateDer = certificate.encoded
    }
}

/**
 * Applies the `deviceauth` decision to the store following the five rows of
 * `SDD-castv2-tofu-rotation.md` §6.2 plus the failure path of §6.2 step 5.
 * Throws [CastDeviceKeyChangedException] or [CastDeviceAuthFailedException]
 * instead of writing when the device must be re-trusted.
 */
fun evaluateDeviceAuth(
    deviceId: String,
    trustStore: CastTrustStore,
    tlsLeafDer: ByteArray,
    authResult: CastDeviceAuthResult,
) {
    when (authResult) {
        is CastDeviceAuthResult.Verified -> applyVerified(deviceId, trustStore, authResult.pinDk)
        is CastDeviceAuthResult.Failed -> applyFailed(deviceId, trustStore, tlsLeafDer, authResult.reason)
    }
}

/** Records the user-confirmed v1 fallback after a `deviceauth` failure (CAST-ROT-INV-3). */
fun recordLegacyPinFallback(deviceId: String, trustStore: CastTrustStore, tlsLeafDer: ByteArray) {
    trustStore.setLegacyPin(deviceId, sha256Hex(tlsLeafDer))
}

private fun applyVerified(deviceId: String, trustStore: CastTrustStore, pinDk: String) {
    val storedV2 = trustStore.getDeviceKeyPin(deviceId)
    when {
        storedV2 != null -> {
            if (!storedV2.equals(pinDk, ignoreCase = true)) {
                throw CastDeviceKeyChangedException(shortPin(storedV2), shortPin(pinDk))
            }
            // Same device key: TLS rotation is transparent.
        }
        trustStore.getLegacyPin(deviceId) != null -> trustStore.migrateV1ToV2(deviceId, pinDk)
        else -> trustStore.setDeviceKeyPin(deviceId, pinDk)
    }
}

private fun applyFailed(
    deviceId: String,
    trustStore: CastTrustStore,
    tlsLeafDer: ByteArray,
    reason: String,
) {
    // A pinned device key that can no longer prove itself is an auth failure, never a silent accept.
    if (trustStore.getDeviceKeyPin(deviceId) != null) throw CastDeviceAuthFailedException(reason)
    val storedV1 = trustStore.getLegacyPin(deviceId)
    if (storedV1 != null && storedV1.equals(sha256Hex(tlsLeafDer), ignoreCase = true)) {
        return // Legacy v1 device: the current TLS still matches the fallback pin; no write.
    }
    throw CastDeviceAuthFailedException(reason)
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
