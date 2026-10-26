// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CastDeviceAuthTest {

    private val keyPair = CastCryptoTestSupport.rsaKeyPair()
    private val nonce = CastDeviceAuth.generateNonce()
    private val tlsLeaf = CastCryptoTestSupport.tlsLeafDer(1)

    @Test
    fun `valid sha256 response verifies and returns the SPKI pin`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        val pin = CastDeviceAuth.verifyAuthResponse(response, nonce, tlsLeaf)
        assertEquals(CastCryptoTestSupport.pinDkFor(keyPair), pin)
    }

    @Test
    fun `rotated TLS leaf with same device key verifies`() {
        val rotated = CastCryptoTestSupport.tlsLeafDer(9)
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, rotated)
        val pin = CastDeviceAuth.verifyAuthResponse(response, nonce, rotated)
        assertEquals(CastCryptoTestSupport.pinDkFor(keyPair), pin)
    }

    @Test
    fun `declared sha1 response verifies`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf, hashAlgorithm = HashAlgorithms.SHA1)
        val pin = CastDeviceAuth.verifyAuthResponse(response, nonce, tlsLeaf)
        assertEquals(CastCryptoTestSupport.pinDkFor(keyPair), pin)
    }

    @Test
    fun `sha1 signature declared as sha256 fails`() {
        val sha1Sig = CastCryptoTestSupport.signSha1(keyPair, nonce, tlsLeaf)
        val response = CastCryptoTestSupport.buildAuthResponse(
            keyPair, nonce, tlsLeaf,
            hashAlgorithm = HashAlgorithms.SHA256,
            signatureOverride = sha1Sig,
        )
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(response, nonce, tlsLeaf) }
    }

    @Test
    fun `flipped bit in signature fails`() {
        val good = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        val decoded = DeviceAuthCodec.decode(good).response!!
        val bad = DeviceAuthCodec.encode(DeviceAuthMessage(response = decoded.copy(signature = flipBit(decoded.signature, 0))))
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(bad, nonce, tlsLeaf) }
    }

    @Test
    fun `flipped bit in signed nonce fails`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        val flippedNonce = flipBit(nonce, 0)
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(response, flippedNonce, tlsLeaf) }
    }

    @Test
    fun `flipped bit in TLS leaf fails`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        val flippedTls = flipBit(tlsLeaf, 0)
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(response, nonce, flippedTls) }
    }

    @Test
    fun `different sender nonce is rejected`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        val decoded = DeviceAuthCodec.decode(response).response!!
        val otherNonce = CastDeviceAuth.generateNonce()
        val bad = DeviceAuthCodec.encode(DeviceAuthMessage(response = decoded.copy(senderNonce = otherNonce)))
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(bad, nonce, tlsLeaf) }
    }

    @Test
    fun `absent sender nonce is rejected even with a valid signature`() {
        val response = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf, includeSenderNonce = false)
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(response, nonce, tlsLeaf) }
    }

    @Test
    fun `unknown hash algorithm is rejected`() {
        val decoded = DeviceAuthCodec.decode(CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)).response!!
        val bad = DeviceAuthCodec.encode(DeviceAuthMessage(response = decoded.copy(hashAlgorithm = 7)))
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(bad, nonce, tlsLeaf) }
    }

    @Test
    fun `auth error without response is rejected`() {
        val error = DeviceAuthCodec.encode(DeviceAuthMessage(error = AuthError(errorType = 1)))
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(error, nonce, tlsLeaf) }
    }

    @Test
    fun `message above 16 KiB is rejected`() {
        val big = ByteArray(CastDeviceAuth.MAX_MESSAGE_BYTES + 1)
        assertFailsWithDeviceAuth { DeviceAuthCodec.decode(big) }
    }

    @Test
    fun `truncated proto fails closed`() {
        val good = CastCryptoTestSupport.buildAuthResponse(keyPair, nonce, tlsLeaf)
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(good.copyOf(good.size - 3), nonce, tlsLeaf) }
    }

    @Test
    fun `rsa below 2048 bits is rejected`() {
        val weak = CastCryptoTestSupport.rsaKeyPair(bits = 1024)
        val response = CastCryptoTestSupport.buildAuthResponse(weak, nonce, tlsLeaf)
        assertFailsWithDeviceAuth { CastDeviceAuth.verifyAuthResponse(response, nonce, tlsLeaf) }
    }

    private fun flipBit(bytes: ByteArray, index: Int): ByteArray =
        bytes.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }

    private fun assertFailsWithDeviceAuth(block: () -> Unit) {
        try {
            block()
            fail("expected CastDeviceAuthException")
        } catch (expected: CastDeviceAuthException) {
            // expected
        }
    }
}
