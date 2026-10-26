// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CastTrustTest {

    private val pinDk1 = "11".repeat(32)
    private val pinDk2 = "22".repeat(32)
    private val tlsLeaf1 = byteArrayOf(1, 2, 3, 4)
    private val tlsLeaf2 = byteArrayOf(5, 6, 7, 8)
    private val tls1Hex = sha256Hex(tlsLeaf1)

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

    @Test
    fun `in memory store migrates and forgets device key pins`() {
        val store = InMemoryCastTrustStore()
        store.setLegacyPin("device-1", "AA")
        store.migrateV1ToV2("device-1", "BB")
        assertNull(store.getLegacyPin("device-1"))
        assertEquals("BB", store.getDeviceKeyPin("device-1"))
        store.forget("device-1")
        assertNull(store.getDeviceKeyPin("device-1"))
    }

    // --- CastTrustManager captures the leaf ---

    @Test
    fun `checkServerTrusted captures the leaf DER`() {
        val manager = CastTrustManager("device-1", InMemoryCastTrustStore())
        val certificate = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-device")
        manager.checkServerTrusted(arrayOf(certificate), "TLS")
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), manager.peerLeafCertificateDer)
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

    // --- evaluateDeviceAuth: the five rows of SDD-castv2-tofu-rotation.md section 6.2 ---

    @Test
    fun `matching device key pin accepts without writing`() {
        val store = InMemoryCastTrustStore()
        store.setDeviceKeyPin("d1", pinDk1)
        evaluateDeviceAuth("d1", store, tlsLeaf2, CastDeviceAuthResult.Verified(pinDk1))
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
        assertNull(store.getLegacyPin("d1"))
    }

    @Test
    fun `changed device key pin throws and does not write`() {
        val store = InMemoryCastTrustStore()
        store.setDeviceKeyPin("d1", pinDk1)
        try {
            evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Verified(pinDk2))
            fail("expected CastDeviceKeyChangedException")
        } catch (expected: CastDeviceKeyChangedException) {
            assertEquals(shortPin(pinDk1), expected.previousPinShort)
            assertEquals(shortPin(pinDk2), expected.newPinShort)
        }
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
    }

    @Test
    fun `legacy pin with matching TLS migrates to device key pin`() {
        val store = InMemoryCastTrustStore()
        store.setLegacyPin("d1", tls1Hex)
        evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Verified(pinDk1))
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
        assertNull(store.getLegacyPin("d1"))
    }

    @Test
    fun `legacy pin with rotated TLS migrates without blocking`() {
        val store = InMemoryCastTrustStore()
        store.setLegacyPin("d1", tls1Hex)
        evaluateDeviceAuth("d1", store, tlsLeaf2, CastDeviceAuthResult.Verified(pinDk1))
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
        assertNull(store.getLegacyPin("d1"))
    }

    @Test
    fun `first use stores the device key pin`() {
        val store = InMemoryCastTrustStore()
        evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Verified(pinDk1))
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
        assertNull(store.getLegacyPin("d1"))
    }

    // --- evaluateDeviceAuth: failure path (section 6.2 step 5) ---

    @Test
    fun `deviceauth failure with a device key pin throws auth failed without writing`() {
        val store = InMemoryCastTrustStore()
        store.setDeviceKeyPin("d1", pinDk1)
        store.setLegacyPin("d1", tls1Hex)
        try {
            evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Failed("bad signature"))
            fail("expected CastDeviceAuthFailedException")
        } catch (expected: CastDeviceAuthFailedException) {
            assertEquals("bad signature", expected.reason)
        }
        assertEquals(pinDk1, store.getDeviceKeyPin("d1"))
        assertEquals(tls1Hex, store.getLegacyPin("d1"))
    }

    @Test
    fun `deviceauth failure with matching legacy pin accepts`() {
        val store = InMemoryCastTrustStore()
        store.setLegacyPin("d1", tls1Hex)
        evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Failed("bad signature"))
        assertEquals(tls1Hex, store.getLegacyPin("d1"))
        assertNull(store.getDeviceKeyPin("d1"))
    }

    @Test
    fun `deviceauth failure without pins throws auth failed`() {
        val store = InMemoryCastTrustStore()
        try {
            evaluateDeviceAuth("d1", store, tlsLeaf1, CastDeviceAuthResult.Failed("bad signature"))
            fail("expected CastDeviceAuthFailedException")
        } catch (expected: CastDeviceAuthFailedException) {
            assertEquals("bad signature", expected.reason)
        }
    }

    @Test
    fun `legacy fallback records the current TLS pin`() {
        val store = InMemoryCastTrustStore()
        recordLegacyPinFallback("d1", store, tlsLeaf1)
        assertEquals(tls1Hex, store.getLegacyPin("d1"))
    }

    @Test
    fun `ssl context uses tls`() {
        assertEquals("TLS", createSslContext("device-1", InMemoryCastTrustStore()).protocol)
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
