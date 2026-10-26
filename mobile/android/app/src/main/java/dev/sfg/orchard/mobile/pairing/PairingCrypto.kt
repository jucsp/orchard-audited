// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocol-level failure for the `orchard-pair-v1` handshake and crypto primitives. */
/** [errorCode] is the peer's protocol error (`bad_payload`, `bad_token`...) when one was returned. */
class PairingProtocolException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String? = null,
) : Exception(message, cause)

/** An ephemeral ECDH P-256 key pair; the public half travels as its X.509 SubjectPublicKeyInfo. */
class PairingKeyPair(
    val privateKey: PrivateKey,
    val publicKey: ByteArray,
)

/**
 * Self-contained ECDH P-256 + HKDF-SHA256 + AES-256-GCM used by the TV ↔ phone pairing flow.
 * No Android types, so every primitive below runs in plain JVM unit tests (PAIR-INV-3).
 */
object PairingCrypto {
    private const val KEY_INFO = "orchard-pair-v1 key phone->tv"
    private const val SAS_INFO = "orchard-pair-v1 sas"
    private const val HASH_LENGTH = 32
    private const val SAS_MODULUS = 1_000_000L

    fun generateKeyPair(): PairingKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return PairingKeyPair(pair.private, pair.public.encoded)
    }

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** HKDF-SHA256 (RFC 5869): extract with `salt`, then expand to `length` bytes of `info`. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * HASH_LENGTH) { "HKDF output must be 1..8160 bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var block = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val count = minOf(block.size, length - offset)
            System.arraycopy(block, 0, out, offset, count)
            offset += count
            counter++
        }
        return out
    }

    /**
     * The handshake transcript `th`: SHA-256 over every public field, each prefixed with its
     * 2-byte big-endian length. `T` is the QR token (empty in SAS mode, where there is no QR).
     */
    fun transcriptHash(
        mode: String,
        tvPublicKey: ByteArray,
        phonePublicKey: ByteArray,
        tvNonce: ByteArray,
        phoneNonce: ByteArray,
        token: ByteArray,
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        appendField(digest, "orchard-pair-v1".toByteArray(Charsets.UTF_8))
        appendField(digest, mode.toByteArray(Charsets.UTF_8))
        appendField(digest, tvPublicKey)
        appendField(digest, phonePublicKey)
        appendField(digest, tvNonce)
        appendField(digest, phoneNonce)
        appendField(digest, token)
        return digest.digest()
    }

    /** Throws [PairingProtocolException] when the peer key is not a valid P-256 point. */
    fun ecdh(privateKey: PrivateKey, peerPublicKey: ByteArray): ByteArray = try {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicKey))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        agreement.generateSecret()
    } catch (error: java.security.GeneralSecurityException) {
        throw PairingProtocolException("invalid peer public key", error)
    }

    fun deriveKey(sharedSecret: ByteArray, transcript: ByteArray): ByteArray =
        hkdf(sharedSecret, transcript, KEY_INFO.toByteArray(Charsets.UTF_8), 32)

    /** Short Authenticated String, `ddd-ddd`, derived from the same key as the session key. */
    fun deriveSas(sharedSecret: ByteArray, transcript: ByteArray): String {
        val bytes = hkdf(sharedSecret, transcript, SAS_INFO.toByteArray(Charsets.UTF_8), 4)
        val value = ((bytes[0].toLong() and 0xffL) shl 24) or
            ((bytes[1].toLong() and 0xffL) shl 16) or
            ((bytes[2].toLong() and 0xffL) shl 8) or
            (bytes[3].toLong() and 0xffL)
        val code = (value % SAS_MODULUS).toInt()
        return "%03d-%03d".format(code / 1000, code % 1000)
    }

    /** AES-256-GCM seal; returns `iv (12 bytes) || ciphertext || tag (16 bytes)`. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    /** AES-256-GCM open; throws [PairingProtocolException] on any tag or AAD mismatch. */
    fun open(key: ByteArray, ivAndCiphertext: ByteArray, aad: ByteArray): ByteArray {
        if (ivAndCiphertext.size < 12 + 16) throw PairingProtocolException("sealed payload too short")
        val iv = ivAndCiphertext.copyOfRange(0, 12)
        val ciphertext = ivAndCiphertext.copyOfRange(12, ivAndCiphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw PairingProtocolException("sealed payload failed authentication", error)
        }
    }

    fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean =
        MessageDigest.isEqual(first, second)

    /** Overwrites key material in place (PAIR-INV-7). Safe on null. */
    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    private fun appendField(digest: MessageDigest, value: ByteArray) {
        require(value.size <= 0xffff) { "transcript field too long" }
        digest.update(byteArrayOf((value.size ushr 8).toByte(), value.size.toByte()))
        digest.update(value)
    }
}

/** Base64url without padding, used for binary fields in JSON and the QR deep link. */
object PairingCodec {
    fun encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(value: String): ByteArray =
        java.util.Base64.getUrlDecoder().decode(value)
}
