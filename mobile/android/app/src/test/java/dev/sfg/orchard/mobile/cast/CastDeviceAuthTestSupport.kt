// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

/** Shared RSA/X.509 helpers for `deviceauth` JVM tests. No android.* imports. */
internal object CastCryptoTestSupport {
    fun rsaKeyPair(bits: Int = 2048): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    fun tlsLeafDer(seed: Byte = 1): ByteArray = ByteArray(64) { seed }

    fun signSha256(keyPair: KeyPair, nonce: ByteArray, tlsLeafDer: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(nonce)
        signer.update(tlsLeafDer)
        return signer.sign()
    }

    fun signSha1(keyPair: KeyPair, nonce: ByteArray, tlsLeafDer: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keyPair.private)
        signer.update(nonce)
        signer.update(tlsLeafDer)
        return signer.sign()
    }

    fun pinDkFor(keyPair: KeyPair): String = sha256Hex((keyPair.public as RSAPublicKey).encoded!!)

    /** Minimal DER self-signed X.509 v1 certificate parseable by `CertificateFactory`. */
    fun selfSignedCertificateDer(keyPair: KeyPair): ByteArray {
        val public = keyPair.public as RSAPublicKey
        val rsaAlg = seq(oid(intArrayOf(1, 2, 840, 113549, 1, 1, 1)), nullValue())
        val rsaKey = seq(integer(public.modulus), integer(public.publicExponent))
        val spki = seq(rsaAlg, bitString(rsaKey))

        val sigAlg = seq(oid(intArrayOf(1, 2, 840, 113549, 1, 1, 11)), nullValue())
        val name = seq(set(seq(oid(intArrayOf(2, 5, 4, 3)), printableString("orchard-cast-test"))))

        val tbs = seq(
            integer(BigInteger.ONE),
            sigAlg,
            name,
            seq(utcTime("260101000000Z"), utcTime("491231235959Z")),
            name,
            spki,
        )
        return seq(tbs, sigAlg, bitString(ByteArray(256)))
    }

    fun buildAuthResponse(
        keyPair: KeyPair,
        nonce: ByteArray,
        tlsLeafDer: ByteArray,
        hashAlgorithm: Int = HashAlgorithms.SHA256,
        includeSenderNonce: Boolean = true,
        signatureOverride: ByteArray? = null,
    ): ByteArray {
        val signature = signatureOverride ?: when (hashAlgorithm) {
            HashAlgorithms.SHA256 -> signSha256(keyPair, nonce, tlsLeafDer)
            else -> signSha1(keyPair, nonce, tlsLeafDer)
        }
        return DeviceAuthCodec.encode(
            DeviceAuthMessage(
                response = AuthResponse(
                    signature = signature,
                    clientAuthCertificate = selfSignedCertificateDer(keyPair),
                    signatureAlgorithm = SignatureAlgorithms.RSASSA_PKCS1V15,
                    senderNonce = if (includeSenderNonce) nonce else ByteArray(0),
                    hashAlgorithm = hashAlgorithm,
                )
            )
        )
    }

    // --- minimal DER encoder ---

    private fun seq(vararg items: ByteArray): ByteArray =
        tag(0x30, items.fold(ByteArray(0)) { a, b -> a + b })

    private fun set(vararg items: ByteArray): ByteArray =
        tag(0x31, items.fold(ByteArray(0)) { a, b -> a + b })

    private fun integer(value: BigInteger): ByteArray {
        var bytes = value.toByteArray()
        if (bytes[0].toInt() < 0) bytes = byteArrayOf(0x00) + bytes
        return tag(0x02, bytes)
    }

    private fun oid(values: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(values[0] * 40 + values[1])
        for (i in 2 until values.size) {
            var v = values[i]
            val parts = mutableListOf<Int>(v and 0x7f)
            v = v ushr 7
            while (v > 0) {
                parts.add((v and 0x7f) or 0x80)
                v = v ushr 7
            }
            for (part in parts.asReversed()) out.write(part)
        }
        return tag(0x06, out.toByteArray())
    }

    private fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun bitString(bytes: ByteArray): ByteArray = tag(0x03, byteArrayOf(0x00) + bytes)

    private fun printableString(value: String): ByteArray = tag(0x13, value.toByteArray(Charsets.US_ASCII))

    private fun utcTime(value: String): ByteArray = tag(0x17, value.toByteArray(Charsets.US_ASCII))

    private fun tag(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + length(content.size) + content

    private fun length(size: Int): ByteArray {
        if (size < 0x80) return byteArrayOf(size.toByte())
        var n = size
        val bytes = ByteArrayOutputStream()
        while (n > 0) {
            bytes.write(n and 0xff)
            n = n ushr 8
        }
        val encoded = bytes.toByteArray().reversedArray()
        return byteArrayOf((0x80 or encoded.size).toByte()) + encoded
    }
}
