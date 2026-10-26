// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

/** Raised when a `deviceauth` message cannot be parsed or verified. */
class CastDeviceAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SignatureAlgorithms {
    const val RSASSA_PKCS1V15 = 1
    const val RSASSA_PSS = 2
}

object HashAlgorithms {
    const val SHA1 = 0
    const val SHA256 = 1
}

data class AuthChallenge(
    val signatureAlgorithm: Int = SignatureAlgorithms.RSASSA_PKCS1V15,
    val senderNonce: ByteArray = ByteArray(0),
    val hashAlgorithm: Int = HashAlgorithms.SHA256,
)

data class AuthResponse(
    val signature: ByteArray = ByteArray(0),
    val clientAuthCertificate: ByteArray = ByteArray(0),
    val intermediateCertificates: List<ByteArray> = emptyList(),
    val signatureAlgorithm: Int = SignatureAlgorithms.RSASSA_PKCS1V15,
    val senderNonce: ByteArray = ByteArray(0),
    val hashAlgorithm: Int = HashAlgorithms.SHA256,
    val crl: ByteArray = ByteArray(0),
)

data class AuthError(val errorType: Int = 0)

data class DeviceAuthMessage(
    val challenge: AuthChallenge? = null,
    val response: AuthResponse? = null,
    val error: AuthError? = null,
)

/** Outcome of a `deviceauth` verification, consumed by [CastTrust.evaluateDeviceAuth]. */
sealed class CastDeviceAuthResult {
    data class Verified(val pinDk: String) : CastDeviceAuthResult()
    data class Failed(val reason: String) : CastDeviceAuthResult()
}

object CastDeviceAuth {
    const val NAMESPACE = CastMessageCodec.NAMESPACE_DEVICEAUTH
    const val MAX_MESSAGE_BYTES = 16384
    const val MAX_CERTIFICATE_BYTES = 4096
    const val MAX_INTERMEDIATE_CERTIFICATES = 3

    private const val NONCE_BYTES = 16
    private const val MIN_RSA_BITS = 2048

    fun generateNonce(): ByteArray = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }

    fun buildChallenge(nonce: ByteArray = generateNonce()): ByteArray {
        if (nonce.size != NONCE_BYTES) {
            throw CastDeviceAuthException("sender_nonce must be $NONCE_BYTES bytes")
        }
        return DeviceAuthCodec.encode(
            DeviceAuthMessage(
                challenge = AuthChallenge(
                    signatureAlgorithm = SignatureAlgorithms.RSASSA_PKCS1V15,
                    senderNonce = nonce,
                    hashAlgorithm = HashAlgorithms.SHA256,
                )
            )
        )
    }

    fun verifyAuthResponse(responseBytes: ByteArray, expectedNonce: ByteArray, tlsLeafDer: ByteArray): String {
        val message = DeviceAuthCodec.decode(responseBytes)
        val response = message.response ?: throw CastDeviceAuthException("AuthResponse missing")

        // CAST-ROT-INV-2: an absent nonce is rejected like a different one.
        if (!response.senderNonce.contentEquals(expectedNonce)) {
            throw CastDeviceAuthException("sender_nonce missing or mismatched")
        }
        if (response.signatureAlgorithm != SignatureAlgorithms.RSASSA_PKCS1V15) {
            throw CastDeviceAuthException("unsupported signature_algorithm ${response.signatureAlgorithm}")
        }

        val certificate = parseX509(response.clientAuthCertificate)
        val publicKey = certificate.publicKey as? RSAPublicKey
            ?: throw CastDeviceAuthException("device key is not RSA")
        if (publicKey.modulus.bitLength() < MIN_RSA_BITS) {
            throw CastDeviceAuthException(
                "device RSA key is ${publicKey.modulus.bitLength()} bits, below $MIN_RSA_BITS"
            )
        }

        val algorithm = when (response.hashAlgorithm) {
            HashAlgorithms.SHA256 -> "SHA256withRSA"
            HashAlgorithms.SHA1 -> "SHA1withRSA"
            else -> throw CastDeviceAuthException("unsupported hash_algorithm ${response.hashAlgorithm}")
        }

        val verifier = Signature.getInstance(algorithm)
        verifier.initVerify(publicKey)
        verifier.update(expectedNonce)
        verifier.update(tlsLeafDer)
        if (!verifier.verify(response.signature)) {
            throw CastDeviceAuthException("signature verification failed")
        }

        return sha256Hex(publicKey.encoded ?: throw CastDeviceAuthException("device key SPKI is unavailable"))
    }

    private fun parseX509(der: ByteArray): X509Certificate {
        if (der.isEmpty()) throw CastDeviceAuthException("client_auth_certificate is empty")
        return try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        } catch (e: Exception) {
            throw CastDeviceAuthException("client_auth_certificate is not valid X.509", e)
        }
    }
}

object DeviceAuthCodec {
    fun encode(message: DeviceAuthMessage): ByteArray {
        val out = ByteArrayOutputStream()
        message.challenge?.let { writeBytesField(1, encodeChallenge(it), out) }
        message.response?.let { writeBytesField(2, encodeResponse(it), out) }
        message.error?.let { writeBytesField(3, encodeError(it), out) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DeviceAuthMessage {
        if (bytes.size > CastDeviceAuth.MAX_MESSAGE_BYTES) {
            throw CastDeviceAuthException(
                "deviceauth message is ${bytes.size} bytes, above ${CastDeviceAuth.MAX_MESSAGE_BYTES}"
            )
        }
        val reader = ProtoReader(bytes)
        var challenge: AuthChallenge? = null
        var response: AuthResponse? = null
        var error: AuthError? = null
        while (!reader.atEnd()) {
            val (number, wire) = reader.readTag()
            when (number) {
                1 -> if (wire == WIRE_LENGTH_DELIMITED) challenge = decodeChallenge(reader.readBytes()) else reader.skip(wire)
                2 -> if (wire == WIRE_LENGTH_DELIMITED) response = decodeResponse(reader.readBytes()) else reader.skip(wire)
                3 -> if (wire == WIRE_LENGTH_DELIMITED) error = decodeError(reader.readBytes()) else reader.skip(wire)
                else -> reader.skip(wire)
            }
        }
        return DeviceAuthMessage(challenge, response, error)
    }

    private fun decodeChallenge(bytes: ByteArray): AuthChallenge {
        val reader = ProtoReader(bytes)
        var signatureAlgorithm = SignatureAlgorithms.RSASSA_PKCS1V15
        var senderNonce = ByteArray(0)
        var hashAlgorithm = HashAlgorithms.SHA256
        while (!reader.atEnd()) {
            val (number, wire) = reader.readTag()
            when (number) {
                1 -> if (wire == WIRE_VARINT) signatureAlgorithm = reader.readVarint().toInt() else reader.skip(wire)
                2 -> if (wire == WIRE_LENGTH_DELIMITED) senderNonce = reader.readBytes() else reader.skip(wire)
                3 -> if (wire == WIRE_VARINT) hashAlgorithm = reader.readVarint().toInt() else reader.skip(wire)
                else -> reader.skip(wire)
            }
        }
        return AuthChallenge(signatureAlgorithm, senderNonce, hashAlgorithm)
    }

    private fun decodeResponse(bytes: ByteArray): AuthResponse {
        val reader = ProtoReader(bytes)
        var signature = ByteArray(0)
        var clientAuthCertificate = ByteArray(0)
        val intermediates = mutableListOf<ByteArray>()
        var signatureAlgorithm = SignatureAlgorithms.RSASSA_PKCS1V15
        var senderNonce = ByteArray(0)
        var hashAlgorithm = HashAlgorithms.SHA256
        var crl = ByteArray(0)
        while (!reader.atEnd()) {
            val (number, wire) = reader.readTag()
            when (number) {
                1 -> if (wire == WIRE_LENGTH_DELIMITED) signature = reader.readBytes() else reader.skip(wire)
                2 -> if (wire == WIRE_LENGTH_DELIMITED) clientAuthCertificate = reader.readBytes() else reader.skip(wire)
                3 -> if (wire == WIRE_LENGTH_DELIMITED) {
                    if (intermediates.size >= CastDeviceAuth.MAX_INTERMEDIATE_CERTIFICATES) {
                        throw CastDeviceAuthException("too many intermediate_certificate fields")
                    }
                    intermediates.add(reader.readBytes())
                } else reader.skip(wire)
                4 -> if (wire == WIRE_VARINT) signatureAlgorithm = reader.readVarint().toInt() else reader.skip(wire)
                5 -> if (wire == WIRE_LENGTH_DELIMITED) senderNonce = reader.readBytes() else reader.skip(wire)
                6 -> if (wire == WIRE_VARINT) hashAlgorithm = reader.readVarint().toInt() else reader.skip(wire)
                7 -> if (wire == WIRE_LENGTH_DELIMITED) crl = reader.readBytes() else reader.skip(wire)
                else -> reader.skip(wire)
            }
        }
        if (clientAuthCertificate.size > CastDeviceAuth.MAX_CERTIFICATE_BYTES) {
            throw CastDeviceAuthException("client_auth_certificate exceeds ${CastDeviceAuth.MAX_CERTIFICATE_BYTES} bytes")
        }
        if (intermediates.any { it.size > CastDeviceAuth.MAX_CERTIFICATE_BYTES }) {
            throw CastDeviceAuthException("intermediate_certificate exceeds ${CastDeviceAuth.MAX_CERTIFICATE_BYTES} bytes")
        }
        return AuthResponse(signature, clientAuthCertificate, intermediates, signatureAlgorithm, senderNonce, hashAlgorithm, crl)
    }

    private fun decodeError(bytes: ByteArray): AuthError {
        val reader = ProtoReader(bytes)
        var errorType = 0
        while (!reader.atEnd()) {
            val (number, wire) = reader.readTag()
            if (number == 1 && wire == WIRE_VARINT) errorType = reader.readVarint().toInt() else reader.skip(wire)
        }
        return AuthError(errorType)
    }

    private fun encodeChallenge(challenge: AuthChallenge): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarintField(1, challenge.signatureAlgorithm.toLong(), out)
        if (challenge.senderNonce.isNotEmpty()) writeBytesField(2, challenge.senderNonce, out)
        writeVarintField(3, challenge.hashAlgorithm.toLong(), out)
        return out.toByteArray()
    }

    private fun encodeResponse(response: AuthResponse): ByteArray {
        val out = ByteArrayOutputStream()
        if (response.signature.isNotEmpty()) writeBytesField(1, response.signature, out)
        if (response.clientAuthCertificate.isNotEmpty()) writeBytesField(2, response.clientAuthCertificate, out)
        response.intermediateCertificates.forEach { writeBytesField(3, it, out) }
        writeVarintField(4, response.signatureAlgorithm.toLong(), out)
        if (response.senderNonce.isNotEmpty()) writeBytesField(5, response.senderNonce, out)
        writeVarintField(6, response.hashAlgorithm.toLong(), out)
        if (response.crl.isNotEmpty()) writeBytesField(7, response.crl, out)
        return out.toByteArray()
    }

    private fun encodeError(error: AuthError): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarintField(1, error.errorType.toLong(), out)
        return out.toByteArray()
    }

    private fun writeBytesField(field: Int, value: ByteArray, out: ByteArrayOutputStream) {
        writeTag(field, WIRE_LENGTH_DELIMITED, out)
        writeVarint(value.size.toLong(), out)
        out.write(value)
    }

    private fun writeVarintField(field: Int, value: Long, out: ByteArrayOutputStream) {
        writeTag(field, WIRE_VARINT, out)
        writeVarint(value, out)
    }

    private fun writeTag(field: Int, wire: Int, out: ByteArrayOutputStream) {
        writeVarint(((field shl 3) or wire).toLong(), out)
    }

    private fun writeVarint(value: Long, out: ByteArrayOutputStream) {
        if (value < 0) throw CastDeviceAuthException("negative varint")
        var remaining = value
        do {
            val next = (remaining and 0x7f).toInt()
            remaining = remaining ushr 7
            out.write(if (remaining != 0L) next or 0x80 else next)
        } while (remaining != 0L)
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5

private class ProtoReader(private val bytes: ByteArray) {
    private var position = 0

    fun atEnd(): Boolean = position >= bytes.size

    fun readTag(): Pair<Int, Int> {
        val tag = readVarint()
        if (tag == 0L) throw CastDeviceAuthException("zero tag")
        val number = tag ushr 3
        if (number <= 0L || number > 0x1fffffffL) {
            throw CastDeviceAuthException("invalid field number $number")
        }
        return number.toInt() to (tag and 0x7).toInt()
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        repeat(10) {
            val next = readByte()
            result = result or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        throw CastDeviceAuthException("varint exceeds 10 bytes")
    }

    fun readBytes(): ByteArray {
        val length = readVarint()
        if (length < 0L || length > CastDeviceAuth.MAX_MESSAGE_BYTES.toLong()) {
            throw CastDeviceAuthException("length-delimited field too large: $length")
        }
        val size = length.toInt()
        if (size > bytes.size - position) {
            throw CastDeviceAuthException("truncated length-delimited field")
        }
        val result = bytes.copyOfRange(position, position + size)
        position += size
        return result
    }

    fun skip(wire: Int) {
        when (wire) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> skipBytes(8)
            WIRE_LENGTH_DELIMITED -> readBytes()
            WIRE_FIXED32 -> skipBytes(4)
            else -> throw CastDeviceAuthException("unsupported wire type $wire")
        }
    }

    private fun readByte(): Int {
        if (position >= bytes.size) throw CastDeviceAuthException("truncated deviceauth message")
        return bytes[position++].toInt() and 0xff
    }

    private fun skipBytes(count: Int) {
        if (count < 0 || position + count > bytes.size) {
            throw CastDeviceAuthException("truncated deviceauth message")
        }
        position += count
    }
}
