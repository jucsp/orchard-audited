// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.OrchardAccountProvisioning
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class PairingServerState { Waiting, Bound, Received, Closed }

/**
 * Single-use LAN pairing server for the TV (PAIR-INV-4). It listens on an ephemeral port bound to
 * the TV's active IPv4 (never `0.0.0.0`) and accepts at most one bound phone. The bind address
 * and clock are injected so the whole state machine runs on loopback in JVM tests.
 */
class TvPairingServer(
    private val bindAddress: InetAddress,
    private val tvName: String,
    private val listener: Listener,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxLifetimeMs: Long = MAX_LIFETIME_MS,
) : AutoCloseable {

    interface Listener {
        fun onState(state: PairingServerState)
        fun onSas(sas: String)
        /** [provisionBinding] ties an Orchard Connect code to this pairing; the transcript is already wiped. */
        fun onSessionReceived(payload: PairingSessionPayload, provisionBinding: ByteArray)
    }

    // Fresh session material; the screen regenerates the whole server for a new code (D1/D4).
    private val keyPair = PairingCrypto.generateKeyPair()
    val publicKey: ByteArray get() = keyPair.publicKey
    val fingerprint: ByteArray = PairingCrypto.sha256(keyPair.publicKey)
    val token: ByteArray = PairingCrypto.randomBytes(16)
    val sessionId: ByteArray = PairingCrypto.randomBytes(8)
    val tvNonce: ByteArray = PairingCrypto.randomBytes(16)

    // The accept timeout lets the loop notice expiry without an incoming connection.
    private val server = ServerSocket(0, 2, bindAddress).apply { soTimeout = ACCEPT_POLL_MS }
    val port: Int get() = server.localPort
    private val startedAt = now()

    private val running = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val shutdownAfterReply = AtomicBoolean(false)
    private val stateRef = AtomicReference(PairingServerState.Waiting)
    private val failures = AtomicInteger(0)
    private val lock = Any()
    private var phoneCommitment: ByteArray? = null
    private var transcript: ByteArray? = null
    private var sessionKey: ByteArray? = null

    @Volatile private var boundMode: String? = null
    @Volatile private var boundToken: ByteArray? = null

    private val acceptor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "orchard-pair-accept").apply { isDaemon = true }
    }
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "orchard-pair-client").apply { isDaemon = true }
    }
    private val connections = ConcurrentHashMap.newKeySet<Socket>()
    private val concurrency = Semaphore(2)

    val state: PairingServerState get() = stateRef.get()

    init {
        listener.onState(PairingServerState.Waiting)
        acceptor.execute { acceptLoop() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        runCatching { server.close() }
        acceptor.shutdownNow()
        workers.shutdownNow()
        connections.forEach { runCatching { it.close() } }
        connections.clear()
        wipeHandshake()
        stateRef.set(PairingServerState.Closed)
        listener.onState(PairingServerState.Closed)
    }

    private fun acceptLoop() {
        while (running.get()) {
            try {
                val socket = server.accept()
                if (expired()) {
                    runCatching { socket.close() }
                    close()
                    break
                }
                if (!concurrency.tryAcquire()) {
                    runCatching { socket.close() }
                    continue
                }
                connections += socket
                runCatching {
                        workers.execute {
                            try {
                                socket.use { serve(it) }
                            } finally {
                                connections -= socket
                                concurrency.release()
                                // Deferred so the request that hit the limit still gets its reply.
                                if (shutdownAfterReply.get()) close()
                            }
                        }
                    }
                    .onFailure {
                        connections -= socket
                        concurrency.release()
                        runCatching { socket.close() }
                    }
            } catch (error: Exception) {
                if (running.get() && expired()) close()
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        try {
            val request = readRequest(input)
            if (request == null) {
                recordFailure()
                return
            }
            if (expired()) {
                close()
                return
            }
            when (request.path) {
                PATH_HELLO -> handleHello(request, output)
                PATH_KEY -> handleKey(request, output)
                PATH_SESSION -> handleSession(request, output)
                else -> writeJson(output, 404, PairingMessages.errorJson("not_found"))
            }
            output.flush()
        } catch (error: SocketTimeoutException) {
            recordFailure()
        } catch (error: Exception) {
            // Malformed JSON surfaces as org.json's JSONException, a plain RuntimeException.
            if (error is PairingProtocolException || error is RuntimeException) {
                recordFailure()
                runCatching {
                    writeJson(output, 400, PairingMessages.errorJson("bad_request"))
                    output.flush()
                }
            }
        }
    }

    private fun handleHello(request: Request, output: BufferedOutputStream) {
        val hello = PairingMessages.parseHelloRequest(request.body.toString(StandardCharsets.UTF_8))
        if (hello.mode == PairingMessages.MODE_QR) {
            val expected = hello.token ?: throw PairingProtocolException("missing token")
            if (!PairingCrypto.constantTimeEquals(expected, token)) {
                recordFailure()
                writeJson(output, 403, PairingMessages.errorJson("bad_token"))
                return
            }
        }
        if (stateRef.get() != PairingServerState.Waiting) {
            writeJson(output, 409, PairingMessages.errorJson("already_bound"))
            return
        }
        synchronized(lock) { phoneCommitment = hello.commitment }
        if (!transition(PairingServerState.Waiting, PairingServerState.Bound)) {
            writeJson(output, 409, PairingMessages.errorJson("already_bound"))
            return
        }
        boundMode = hello.mode
        boundToken = hello.token
        writeJson(output, 200, PairingMessages.helloResponseJson(sessionId, publicKey, tvNonce, tvName))
    }

    private fun handleKey(request: Request, output: BufferedOutputStream) {
        val key = PairingMessages.parseKeyRequest(request.body.toString(StandardCharsets.UTF_8))
        if (stateRef.get() != PairingServerState.Bound) {
            writeJson(output, 409, PairingMessages.errorJson("not_bound"))
            return
        }
        if (!PairingCrypto.constantTimeEquals(key.sessionId, sessionId)) {
            recordFailure()
            writeJson(output, 403, PairingMessages.errorJson("bad_sid"))
            return
        }
        val commitment = synchronized(lock) { phoneCommitment } ?: run {
            writeJson(output, 409, PairingMessages.errorJson("not_bound"))
            return
        }
        if (!PairingCrypto.constantTimeEquals(PairingCrypto.sha256(key.phonePublicKey), commitment)) {
            recordFailure()
            abortBinding()
            writeJson(output, 403, PairingMessages.errorJson("bad_commitment"))
            return
        }
        val mode = boundMode ?: PairingMessages.MODE_QR
        val token = boundToken ?: ByteArray(0)
        val shared = try {
            PairingCrypto.ecdh(keyPair.privateKey, key.phonePublicKey)
        } catch (error: PairingProtocolException) {
            recordFailure()
            abortBinding()
            writeJson(output, 400, PairingMessages.errorJson("bad_key"))
            return
        }
        val th = PairingCrypto.transcriptHash(mode, publicKey, key.phonePublicKey, tvNonce, key.phoneNonce, token)
        val derivedKey = PairingCrypto.deriveKey(shared, th)
        synchronized(lock) {
            transcript = th
            sessionKey = derivedKey
        }
        if (mode == PairingMessages.MODE_SAS) {
            listener.onSas(PairingCrypto.deriveSas(shared, th))
        }
        PairingCrypto.wipe(shared)
        writeJson(output, 200, PairingMessages.keyResponseJson())
    }

    private fun handleSession(request: Request, output: BufferedOutputStream) {
        val session = PairingMessages.parseSessionRequest(request.body.toString(StandardCharsets.UTF_8))
        if (stateRef.get() != PairingServerState.Bound) {
            writeJson(output, 409, PairingMessages.errorJson("not_bound"))
            return
        }
        if (!PairingCrypto.constantTimeEquals(session.sessionId, sessionId)) {
            recordFailure()
            writeJson(output, 403, PairingMessages.errorJson("bad_sid"))
            return
        }
        val (key, th) = synchronized(lock) {
            val currentKey = sessionKey ?: return@synchronized null
            val currentTh = transcript ?: return@synchronized null
            currentKey to currentTh
        } ?: run {
            writeJson(output, 409, PairingMessages.errorJson("not_bound"))
            return
        }
        val plaintext = try {
            PairingCrypto.open(key, session.ivAndCiphertext, th)
        } catch (error: PairingProtocolException) {
            recordFailure()
            abortBinding()
            writeJson(output, 403, PairingMessages.errorJson("bad_seal"))
            return
        }
        val payload = try {
            PairingSessionPayload.fromJson(plaintext.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            // org.json errors are RuntimeExceptions; both mean an unreadable payload.
            recordFailure()
            writeJson(output, 400, PairingMessages.errorJson("bad_payload"))
            return
        } finally {
            PairingCrypto.wipe(plaintext)
        }
        // Computed before deliverSession wipes the transcript.
        val binding = OrchardAccountProvisioning.computeProvisionBinding(th)
        writeJson(output, 200, PairingMessages.sessionResponseJson("pending_tv_confirm"))
        deliverSession(payload, binding)
    }

    private fun deliverSession(payload: PairingSessionPayload, binding: ByteArray) {
        if (!transition(PairingServerState.Bound, PairingServerState.Received)) return
        running.set(false)
        runCatching { server.close() }
        acceptor.shutdownNow()
        wipeHandshake()
        listener.onSessionReceived(payload, binding)
    }

    private fun expired(): Boolean = now() - startedAt > maxLifetimeMs

    private fun recordFailure() {
        if (!running.get()) return
        if (failures.incrementAndGet() >= MAX_FAILURES) {
            running.set(false)
            shutdownAfterReply.set(true)
        }
    }

    private fun abortBinding() {
        synchronized(lock) { phoneCommitment = null }
        wipeHandshake()
        boundMode = null
        boundToken = null
        if (stateRef.compareAndSet(PairingServerState.Bound, PairingServerState.Waiting)) {
            listener.onState(PairingServerState.Waiting)
        }
    }

    private fun wipeHandshake() {
        synchronized(lock) {
            transcript?.let(PairingCrypto::wipe)
            transcript = null
            sessionKey?.let(PairingCrypto::wipe)
            sessionKey = null
        }
    }

    private fun transition(from: PairingServerState, to: PairingServerState): Boolean {
        if (!stateRef.compareAndSet(from, to)) return false
        listener.onState(to)
        return true
    }

    private data class Request(val path: String, val body: ByteArray)

    private fun readRequest(input: BufferedInputStream): Request? {
        val requestLine = readLine(input, MAX_HEADER_BYTES) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2 || parts[0] != "POST") return null
        val path = parts[1].substringBefore('?')
        var total = requestLine.length + 2
        var contentLength = -1
        while (true) {
            if (total > MAX_HEADER_BYTES) return null
            val line = readLine(input, MAX_HEADER_BYTES - total) ?: return null
            total += line.length + 2
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).trim().equals("content-length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toIntOrNull() ?: return null
            }
        }
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) return null
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(body, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        if (read != contentLength) return null
        return Request(path, body)
    }

    private fun readLine(input: BufferedInputStream, max: Int): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size < max) {
            val next = input.read()
            if (next < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.ISO_8859_1)
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes += next.toByte()
        }
        return bytes.toByteArray().toString(Charsets.ISO_8859_1)
    }

    private fun writeJson(output: BufferedOutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            else -> "Error"
        }
        output.write("HTTP/1.1 $code $reason\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Type: application/json\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Connection: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write(bytes)
    }

    companion object {
        const val MAX_LIFETIME_MS = 300_000L
        const val MAX_FAILURES = 5
        const val MAX_BODY_BYTES = 64 * 1024
        const val MAX_HEADER_BYTES = 8 * 1024
        const val READ_TIMEOUT_MS = 10_000
        private const val ACCEPT_POLL_MS = 500
        private const val PATH_HELLO = "/v1/hello"
        private const val PATH_KEY = "/v1/key"
        private const val PATH_SESSION = "/v1/session"
    }
}
