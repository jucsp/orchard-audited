// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.math.BigInteger
import java.security.MessageDigest
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CastTrustTest {

    // --- isPrivateIp ---

    @Test
    fun `private rfc1918 and link-local ips are accepted`() {
        val valid = listOf(
            "10.0.0.1",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.1",
            "169.254.1.1"
        )
        valid.forEach { ip -> assertTrue("expected private: $ip", isPrivateIp(ip)) }
    }

    @Test
    fun `public cgnat loopback multicast and broadcast ips are rejected`() {
        val invalid = listOf(
            "8.8.8.8",
            "100.64.0.1",
            "100.127.255.255",
            "127.0.0.1",
            "1.1.1.1",
            "224.0.0.1",
            "255.255.255.255"
        )
        invalid.forEach { ip -> assertFalse("expected rejected: $ip", isPrivateIp(ip)) }
    }

    @Test
    fun `non ip strings are rejected`() {
        val invalid = listOf(
            "",
            "not-an-ip",
            "10.0.0",
            "10.0.0.0.1",
            "999.999.999.999",
            "256.0.0.1",
            "a.b.c.d"
        )
        invalid.forEach { ip -> assertFalse("expected rejected: $ip", isPrivateIp(ip)) }
    }

    @Test
    fun `range boundaries are exact`() {
        listOf("10.255.255.255", "172.16.0.0", "172.31.0.1", "192.168.0.0", "169.254.255.255")
            .forEach { ip -> assertTrue("expected private: $ip", isPrivateIp(ip)) }
        listOf("9.255.255.255", "11.0.0.1", "172.15.255.255", "172.32.0.1", "192.167.1.1",
            "192.169.1.1", "169.253.1.1", "169.255.1.1", "100.63.255.255", "0.0.0.0")
            .forEach { ip -> assertFalse("expected rejected: $ip", isPrivateIp(ip)) }
    }

    @Test
    fun `non canonical literals are rejected`() {
        listOf("010.0.0.1", "192.168.01.1", "10.0.0.01", " 10.0.0.1", "10.0.0.1 ", "+10.0.0.1",
            "10.0.0.1/8", "0xa.0.0.1", "10.0.0.1.", "[10.0.0.1]")
            .forEach { ip -> assertFalse("expected rejected: $ip", isPrivateIp(ip)) }
    }

    @Test
    fun `parsed address matches the literal`() {
        val address = parsePrivateIpv4("192.168.1.20")
        assertNotNull(address)
        assertEquals(listOf(192, 168, 1, 20), address!!.address.map { it.toInt() and 0xff })
    }

    // --- InMemoryCastTrustStore ---

    @Test
    fun `in memory store persists and overwrites fingerprints`() {
        val store = InMemoryCastTrustStore()
        assertNull(store.getFingerprint("device-1"))
        store.setFingerprint("device-1", "AA")
        assertEquals("AA", store.getFingerprint("device-1"))
        store.setFingerprint("device-1", "BB")
        assertEquals("BB", store.getFingerprint("device-1"))
        assertNull(store.getFingerprint("device-2"))
    }

    // --- CastTrustManager TOFU ---

    @Test
    fun `first use stores certificate fingerprint`() {
        val store = InMemoryCastTrustStore()
        val manager = CastTrustManager("device-1", store)
        val certificate = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-device")

        manager.checkServerTrusted(arrayOf(certificate), "TLS")

        val stored = store.getFingerprint("device-1")
        assertNotNull(stored)
        assertEquals(fingerprintOf(certificate), stored)
        assertTrue(stored!!.matches(Regex("[0-9A-F]{64}")))
    }

    @Test
    fun `second use with same certificate passes`() {
        val store = InMemoryCastTrustStore()
        val manager = CastTrustManager("device-1", store)
        val certificate = FakeX509Certificate(byteArrayOf(10, 20, 30), 1L, "CN=cast-device")

        manager.checkServerTrusted(arrayOf(certificate), "TLS")
        manager.checkServerTrusted(arrayOf(certificate), "TLS")

        assertEquals(fingerprintOf(certificate), store.getFingerprint("device-1"))
    }

    @Test
    fun `second use with different certificate throws security exception`() {
        val store = InMemoryCastTrustStore()
        val manager = CastTrustManager("device-1", store)
        val first = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-device")
        val second = FakeX509Certificate(byteArrayOf(5, 6, 7, 8), 2L, "CN=cast-device")

        manager.checkServerTrusted(arrayOf(first), "TLS")
        try {
            manager.checkServerTrusted(arrayOf(second), "TLS")
            fail("expected CastSecurityException")
        } catch (expected: CastSecurityException) {
            assertTrue(expected.message!!.contains("mismatch"))
        }
    }

    @Test
    fun `pins are isolated per device id`() {
        val store = InMemoryCastTrustStore()
        val first = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-a")
        val second = FakeX509Certificate(byteArrayOf(5, 6, 7, 8), 2L, "CN=cast-b")

        CastTrustManager("device-a", store).checkServerTrusted(arrayOf(first), "TLS")
        CastTrustManager("device-b", store).checkServerTrusted(arrayOf(second), "TLS")

        assertEquals(fingerprintOf(first), store.getFingerprint("device-a"))
        assertEquals(fingerprintOf(second), store.getFingerprint("device-b"))
    }

    @Test
    fun `mismatch keeps the original pin`() {
        val store = InMemoryCastTrustStore()
        val manager = CastTrustManager("device-1", store)
        val first = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-device")
        val second = FakeX509Certificate(byteArrayOf(9, 9, 9, 9), 2L, "CN=cast-device")

        manager.checkServerTrusted(arrayOf(first), "TLS")
        try {
            manager.checkServerTrusted(arrayOf(second), "TLS")
            fail("expected CastSecurityException")
        } catch (_: CastSecurityException) {
        }
        assertEquals(fingerprintOf(first), store.getFingerprint("device-1"))
    }

    @Test
    fun `ssl context uses tls`() {
        assertEquals("TLS", createSslContext("device-1", InMemoryCastTrustStore()).protocol)
    }

    @Test
    fun `empty certificate chain is rejected`() {
        val manager = CastTrustManager("device-1", InMemoryCastTrustStore())
        try {
            manager.checkServerTrusted(emptyArray(), "TLS")
            fail("expected CastSecurityException")
        } catch (expected: CastSecurityException) {
            assertTrue(expected.message!!.contains("Empty certificate chain"))
        }
    }

    private fun fingerprintOf(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(separator = "") { byte ->
            "%02X".format(byte.toInt() and 0xff)
        }
    }
}

internal class FakeX509Certificate(
    private val encoded: ByteArray,
    val serial: Long,
    val subject: String
) : X509Certificate() {

    override fun checkValidity() = Unit

    override fun checkValidity(date: Date) = Unit

    override fun getVersion(): Int = 2

    override fun getSerialNumber(): BigInteger = BigInteger.valueOf(serial)

    override fun getIssuerDN(): Principal = X500Principal(subject)

    override fun getSubjectDN(): Principal = X500Principal(subject)

    override fun getNotBefore(): Date = Date(0L)

    override fun getNotAfter(): Date = Date(Long.MAX_VALUE)

    override fun getSigAlgName(): String = "SHA256withRSA"

    override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"

    override fun getSigAlgParams(): ByteArray? = null

    override fun getIssuerUniqueID(): BooleanArray? = null

    override fun getSubjectUniqueID(): BooleanArray? = null

    override fun getKeyUsage(): BooleanArray? = null

    override fun getBasicConstraints(): Int = -1

    override fun getExtendedKeyUsage(): List<String>? = null

    override fun getTBSCertificate(): ByteArray = encoded

    override fun getSignature(): ByteArray = ByteArray(0)

    override fun getEncoded(): ByteArray = encoded

    override fun verify(key: PublicKey) {
        throw UnsupportedOperationException("not supported in tests")
    }

    override fun verify(key: PublicKey, sigProvider: String) {
        throw UnsupportedOperationException("not supported in tests")
    }

    override fun getPublicKey(): PublicKey = DummyPublicKey()

    override fun hasUnsupportedCriticalExtension(): Boolean = false

    override fun getCriticalExtensionOIDs(): MutableSet<String>? = null

    override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null

    override fun getExtensionValue(oid: String?): ByteArray? = null

    override fun toString(): String = "FakeX509Certificate($subject)"
}

private class DummyPublicKey : PublicKey {
    override fun getAlgorithm(): String = "RSA"

    override fun getFormat(): String = "X.509"

    override fun getEncoded(): ByteArray = ByteArray(0)
}
