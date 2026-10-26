// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.OrchardAccountProvisioning
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/** The derived keying material a phone needs to send the encrypted session (kept off logs). */
data class PairingKeyExchange(
    val transcript: ByteArray,
    val key: ByteArray,
    val sas: String,
) {
    override fun toString(): String = "PairingKeyExchange(key=${key.size}B, th=${transcript.size}B)"
}

/**
 * A completed handshake waiting for the user's confirmation (PAIR-INV-2). Nothing secret has left
 * the phone yet; [sas] is what both screens show in SAS mode.
 */
class PreparedPairing internal constructor(
    val host: String,
    val port: Int,
    val mode: String,
    val tvName: String,
    internal val sessionId: ByteArray,
    internal val exchange: PairingKeyExchange,
) {
    val sas: String get() = exchange.sas

    @Volatile private var wiped = false

    /** Binds an Orchard Connect provisioning code to this pairing's transcript (PAIR-CONN-INV-1). */
    fun provisionBinding(): ByteArray {
        check(!wiped) { "pairing already sent or abandoned" }
        return OrchardAccountProvisioning.computeProvisionBinding(exchange.transcript)
    }

    /** Drops the session key once the pairing is sent or abandoned (PAIR-INV-7). */
    fun wipe() {
        wiped = true
        PairingCrypto.wipe(exchange.key)
        PairingCrypto.wipe(exchange.transcript)
    }

    override fun toString(): String = "PreparedPairing(host=$host, port=$port, mode=$mode, tv=$tvName)"
}

/**
 * Minimal HTTP/1.1 client for the phone side of `orchard-pair-v1`. One connection per request.
 * The socket factory is injectable so tests run against a loopback [TvPairingServer].
 */
class PairingClient(
    private val openSocket: (InetSocketAddress) -> Socket = { address ->
        Socket().apply { connect(address, CONNECT_TIMEOUT_MS) }
    },
) : AutoCloseable {

    fun hello(
        host: String,
        port: Int,
        mode: String,
        token: ByteArray?,
        phonePublicKey: ByteArray,
        expectedFingerprint: ByteArray?,
    ): HelloResponse {
        val commitment = PairingCrypto.sha256(phonePublicKey)
        val body = PairingMessages.helloRequestJson(mode, token, commitment)
        val response = PairingMessages.parseHelloResponse(postJson(host, port, PATH_HELLO, body))
        if (expectedFingerprint != null) {
            val actual = PairingCrypto.sha256(response.tvPublicKey)
            if (!PairingCrypto.constantTimeEquals(actual, expectedFingerprint)) {
                throw PairingProtocolException("TV fingerprint does not match the QR code")
            }
        }
        return response
    }

    fun key(
        host: String,
        port: Int,
        phoneKeyPair: PairingKeyPair,
        phoneNonce: ByteArray,
        mode: String,
        token: ByteArray?,
        hello: HelloResponse,
    ): PairingKeyExchange {
        val body = PairingMessages.keyRequestJson(hello.sessionId, phoneKeyPair.publicKey, phoneNonce)
        PairingMessages.parseKeyResponse(postJson(host, port, PATH_KEY, body))
        val shared = PairingCrypto.ecdh(phoneKeyPair.privateKey, hello.tvPublicKey)
        val transcript = PairingCrypto.transcriptHash(
            mode,
            hello.tvPublicKey,
            phoneKeyPair.publicKey,
            hello.tvNonce,
            phoneNonce,
            token ?: ByteArray(0),
        )
        val key = PairingCrypto.deriveKey(shared, transcript)
        val sas = PairingCrypto.deriveSas(shared, transcript)
        PairingCrypto.wipe(shared)
        return PairingKeyExchange(transcript, key, sas)
    }

    fun sendSession(
        host: String,
        port: Int,
        sessionId: ByteArray,
        exchange: PairingKeyExchange,
        payload: PairingSessionPayload,
    ): String {
        val plaintext = payload.toJson().toByteArray(StandardCharsets.UTF_8)
        val sealed = try {
            if (plaintext.size > PairingMessages.MAX_PAYLOAD_BYTES) {
                throw PairingProtocolException("payload exceeds ${PairingMessages.MAX_PAYLOAD_BYTES} bytes")
            }
            PairingCrypto.seal(exchange.key, plaintext, exchange.transcript)
        } finally {
            PairingCrypto.wipe(plaintext)
        }
        val body = PairingMessages.sessionRequestJson(sessionId, sealed)
        return PairingMessages.parseSessionResponse(postJson(host, port, PATH_SESSION, body))
    }

    /**
     * Runs `hello` and `key` with a fresh ECDH pair. In QR mode [expectedFingerprint] must match
     * the TV key or the handshake aborts before `/v1/key` (PAIR-INV-5).
     */
    fun prepare(
        host: String,
        port: Int,
        mode: String,
        token: ByteArray? = null,
        expectedFingerprint: ByteArray? = null,
    ): PreparedPairing {
        if (mode == PairingMessages.MODE_QR && (token == null || expectedFingerprint == null)) {
            throw PairingProtocolException("QR mode requires token and fingerprint")
        }
        val keyPair = PairingCrypto.generateKeyPair()
        val nonce = PairingCrypto.randomBytes(16)
        val hello = hello(host, port, mode, token, keyPair.publicKey, expectedFingerprint)
        val exchange = key(host, port, keyPair, nonce, mode, token, hello)
        return PreparedPairing(host, port, mode, hello.name, hello.sessionId, exchange)
    }

    /** Sends the sealed session. Call only after the user confirmed on the phone. */
    fun send(prepared: PreparedPairing, payload: PairingSessionPayload): String = try {
        sendSession(prepared.host, prepared.port, prepared.sessionId, prepared.exchange, payload)
    } finally {
        prepared.wipe()
    }

    override fun close() = Unit

    private fun postJson(host: String, port: Int, path: String, json: String): String {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val socket = openSocket(InetSocketAddress(host, port))
        socket.use {
            it.soTimeout = READ_TIMEOUT_MS
            val output = BufferedOutputStream(it.getOutputStream())
            writeLine(output, "POST $path HTTP/1.1")
            writeLine(output, "Host: $host:$port")
            writeLine(output, "Content-Type: application/json")
            writeLine(output, "Content-Length: ${body.size}")
            writeLine(output, "Connection: close")
            writeLine(output, "")
            output.write(body)
            output.flush()

            val input = BufferedInputStream(it.getInputStream())
            val statusLine = readLine(input) ?: throw PairingProtocolException("empty response")
            val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw PairingProtocolException("malformed status line")
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0 && line.substring(0, separator).trim().equals("content-length", ignoreCase = true)) {
                    contentLength = line.substring(separator + 1).trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength < 0 || contentLength > MAX_RESPONSE_BYTES) throw PairingProtocolException("bad content length")
            val responseBody = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val count = input.read(responseBody, read, contentLength - read)
                if (count < 0) break
                read += count
            }
            if (code !in 200..299) {
                val error = PairingMessages.parseErrorCode(responseBody.toString(StandardCharsets.UTF_8))
                throw PairingProtocolException(
                    "pairing request failed with HTTP $code${error?.let { " ($it)" } ?: ""}",
                    errorCode = error,
                )
            }
            return responseBody.toString(StandardCharsets.UTF_8)
        }
    }

    private fun writeLine(output: BufferedOutputStream, line: String) {
        output.write("$line\r\n".toByteArray(StandardCharsets.ISO_8859_1))
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size < MAX_RESPONSE_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.ISO_8859_1)
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes += next.toByte()
        }
        return bytes.toByteArray().toString(Charsets.ISO_8859_1)
    }

    companion object {
        /** A TV without payload `v=2` answers this; the phone asks to update it (PAIR-CONN-INV-4). */
        const val ERROR_BAD_PAYLOAD = "bad_payload"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val MAX_RESPONSE_HEADER_BYTES = 8 * 1024
        private const val PATH_HELLO = "/v1/hello"
        private const val PATH_KEY = "/v1/key"
        private const val PATH_SESSION = "/v1/session"
    }
}
