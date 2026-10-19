// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PairingCryptoTest {

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private class Side(val keys: PairingKeyPair, val nonce: ByteArray = PairingCrypto.randomBytes(16))

    private fun handshake(tv: Side, phone: Side, mode: String, token: ByteArray): Triple<ByteArray, ByteArray, String> {
        val th = PairingCrypto.transcriptHash(mode, tv.keys.publicKey, phone.keys.publicKey, tv.nonce, phone.nonce, token)
        val shared = PairingCrypto.ecdh(tv.keys.privateKey, phone.keys.publicKey)
        return Triple(th, PairingCrypto.deriveKey(shared, th), PairingCrypto.deriveSas(shared, th))
    }

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val okm = PairingCrypto.hkdf(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm,
        )
    }

    @Test
    fun bothSidesDeriveTheSameKeyAndSas() {
        val tv = Side(PairingCrypto.generateKeyPair())
        val phone = Side(PairingCrypto.generateKeyPair())
        val token = PairingCrypto.randomBytes(16)
        val th = PairingCrypto.transcriptHash(PairingMessages.MODE_QR, tv.keys.publicKey, phone.keys.publicKey, tv.nonce, phone.nonce, token)
        val tvShared = PairingCrypto.ecdh(tv.keys.privateKey, phone.keys.publicKey)
        val phoneShared = PairingCrypto.ecdh(phone.keys.privateKey, tv.keys.publicKey)
        assertArrayEquals(tvShared, phoneShared)
        assertArrayEquals(PairingCrypto.deriveKey(tvShared, th), PairingCrypto.deriveKey(phoneShared, th))
        assertEquals(PairingCrypto.deriveSas(tvShared, th), PairingCrypto.deriveSas(phoneShared, th))
        assertEquals(32, PairingCrypto.deriveKey(tvShared, th).size)
    }

    @Test
    fun sasIsSixDigitsWithDash() {
        repeat(20) {
            val (_, _, sas) = handshake(Side(PairingCrypto.generateKeyPair()), Side(PairingCrypto.generateKeyPair()), PairingMessages.MODE_SAS, ByteArray(0))
            assertTrue(sas, Regex("\\d{3}-\\d{3}").matches(sas))
        }
    }

    @Test
    fun transcriptChangesWithEveryField() {
        val a = PairingCrypto.randomBytes(91)
        val b = PairingCrypto.randomBytes(91)
        val n1 = PairingCrypto.randomBytes(16)
        val n2 = PairingCrypto.randomBytes(16)
        val t = PairingCrypto.randomBytes(16)
        val base = PairingCrypto.transcriptHash("qr", a, b, n1, n2, t)
        val variants = listOf(
            PairingCrypto.transcriptHash("sas", a, b, n1, n2, t),
            PairingCrypto.transcriptHash("qr", b, b, n1, n2, t),
            PairingCrypto.transcriptHash("qr", a, a, n1, n2, t),
            PairingCrypto.transcriptHash("qr", a, b, n2, n2, t),
            PairingCrypto.transcriptHash("qr", a, b, n1, n1, t),
            PairingCrypto.transcriptHash("qr", a, b, n1, n2, ByteArray(0)),
        )
        variants.forEach { assertFalse(base.contentEquals(it)) }
        // Length prefixes keep field boundaries unambiguous.
        assertFalse(
            PairingCrypto.transcriptHash("qr", byteArrayOf(1, 2), byteArrayOf(3), n1, n2, t)
                .contentEquals(PairingCrypto.transcriptHash("qr", byteArrayOf(1), byteArrayOf(2, 3), n1, n2, t)),
        )
    }

    @Test
    fun gcmRoundTripAndTamperDetection() {
        val key = PairingCrypto.randomBytes(32)
        val aad = PairingCrypto.randomBytes(32)
        val plaintext = "secret session".toByteArray()
        val sealed = PairingCrypto.seal(key, plaintext, aad)
        assertArrayEquals(plaintext, PairingCrypto.open(key, sealed, aad))

        expectProtocolError { PairingCrypto.open(key, sealed, PairingCrypto.randomBytes(32)) }
        expectProtocolError { PairingCrypto.open(PairingCrypto.randomBytes(32), sealed, aad) }
        val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        expectProtocolError { PairingCrypto.open(key, flipped, aad) }
        val otherIv = sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        expectProtocolError { PairingCrypto.open(key, otherIv, aad) }
        expectProtocolError { PairingCrypto.open(key, ByteArray(10), aad) }
    }

    @Test
    fun sealUsesFreshIvEachTime() {
        val key = PairingCrypto.randomBytes(32)
        val first = PairingCrypto.seal(key, byteArrayOf(1), ByteArray(0))
        val second = PairingCrypto.seal(key, byteArrayOf(1), ByteArray(0))
        assertFalse(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
    }

    @Test
    fun mitmProducesDifferentSasOnEachSide() {
        // The attacker runs one handshake with the phone and another with the TV.
        var collisions = 0
        repeat(50) {
            val tv = Side(PairingCrypto.generateKeyPair())
            val phone = Side(PairingCrypto.generateKeyPair())
            val attacker = Side(PairingCrypto.generateKeyPair())
            val (_, kPhone, sasPhone) = handshake(attacker, phone, PairingMessages.MODE_SAS, ByteArray(0))
            val (_, kTv, sasTv) = handshake(tv, attacker, PairingMessages.MODE_SAS, ByteArray(0))
            assertFalse(kPhone.contentEquals(kTv))
            if (sasPhone == sasTv) collisions++
        }
        assertTrue("SAS collided $collisions times", collisions <= 1)
    }

    @Test
    fun invalidPeerKeyIsAProtocolError() {
        val keys = PairingCrypto.generateKeyPair()
        expectProtocolError { PairingCrypto.ecdh(keys.privateKey, PairingCrypto.randomBytes(91)) }
    }

    @Test
    fun wipeZeroesInPlace() {
        val bytes = PairingCrypto.randomBytes(32).also { it[0] = 7 }
        PairingCrypto.wipe(bytes)
        assertArrayEquals(ByteArray(32), bytes)
        PairingCrypto.wipe(null)
    }

    @Test
    fun codecIsUrlSafeWithoutPadding() {
        val bytes = byteArrayOf(-5, -1, -2, 0)
        val encoded = PairingCodec.encode(bytes)
        assertFalse(encoded.contains('=') || encoded.contains('+') || encoded.contains('/'))
        assertArrayEquals(bytes, PairingCodec.decode(encoded))
        assertNotEquals(PairingCodec.encode(byteArrayOf(1)), PairingCodec.encode(byteArrayOf(2)))
    }
}

internal inline fun expectProtocolError(block: () -> Unit) {
    try {
        block()
        fail("expected PairingProtocolException")
    } catch (expected: PairingProtocolException) {
        // expected
    }
}
