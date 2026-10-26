// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket

/**
 * TLS channel to a Cast device on a private IPv4 address.
 *
 * Handles 4-byte big-endian framing, `requestId` correlation and dispatch of
 * unsolicited messages to listeners. Any read error, malformed frame or
 * oversized length closes the channel and fails every pending request.
 * Tests inject a plain [SocketFactory]; production uses the TOFU pinned TLS context.
 */
class CastChannel(
    val host: String,
    val port: Int,
    val deviceId: String,
    val trustStore: CastTrustStore,
    internal val socketFactory: SocketFactory? = null,
    val connectionTimeoutMs: Int = 5000,
    val requestTimeoutMs: Long = 10000L
) : AutoCloseable {

    /** Called once when the channel closes; the cause is null for a local [close]. */
    var onClose: ((Throwable?) -> Unit)? = null

    /**
     * DER of the TLS leaf captured after the handshake, exposed for the
     * `deviceauth` exchange. Null on plain-TCP transports (tests) unless set by
     * the caller before the session authenticates.
     */
    var peerLeafCertificateDer: ByteArray? = null

    private val writeLock = Any()
    private val closed = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<CastMessage>>()
    private val listeners = CopyOnWriteArrayList<(CastMessage) -> Unit>()
    private val nextRequestId = AtomicInteger(1)

    @kotlin.jvm.Volatile
    private var socket: Socket? = null

    @kotlin.jvm.Volatile
    private var output: OutputStream? = null

    @kotlin.jvm.Volatile
    private var scheduler: ScheduledExecutorService? = null

    @Synchronized
    fun open() {
        if (closed.get()) throw IOException("Channel closed")
        if (socket != null) return
        val address = parsePrivateIpv4(host)
            ?: throw CastSecurityException("Non-private address rejected: $host")

        val factory: SocketFactory = socketFactory
            ?: createSslContext(deviceId, trustStore).socketFactory
        val newSocket = factory.createSocket()
        val newOutput: OutputStream = try {
            if (!newSocket.isConnected) {
                newSocket.connect(InetSocketAddress(address, port), connectionTimeoutMs)
            }
            if (newSocket is SSLSocket) {
                // Bounded handshake so a pin mismatch surfaces here instead of on the reader.
                newSocket.soTimeout = connectionTimeoutMs
                newSocket.startHandshake()
                newSocket.soTimeout = 0
                peerLeafCertificateDer = newSocket.session.peerCertificates
                    ?.filterIsInstance<X509Certificate>()
                    ?.firstOrNull()
                    ?.encoded
            }
            newSocket.getOutputStream()
        } catch (e: Exception) {
            closeQuietly(newSocket)
            // The TLS stack wraps the TrustManager failure; callers need the pin error itself.
            throw generateSequence<Throwable>(e) { it.cause }
                .firstOrNull { it is CastSecurityException } ?: e
        }
        output = newOutput
        socket = newSocket

        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "cast-channel-timer").apply { isDaemon = true }
        }
        if (closed.get()) {
            // close() raced with open(): it ran before socket and scheduler existed.
            closeQuietly(newSocket)
            scheduler?.shutdownNow()
            throw IOException("Channel closed")
        }

        val reader = Thread({ runReaderLoop(newSocket) }, "cast-channel-reader")
        reader.isDaemon = true
        reader.start()
    }

    fun addMessageListener(listener: (CastMessage) -> Unit) {
        listeners.add(listener)
    }

    fun removeMessageListener(listener: (CastMessage) -> Unit) {
        listeners.remove(listener)
    }

    /** Writes one frame (length header + serialized CastMessage). */
    fun send(message: CastMessage) {
        val out = output ?: throw IOException("Channel not open")
        val frame = CastMessageCodec.encodeFrame(message)
        synchronized(writeLock) {
            out.write(frame)
            out.flush()
        }
    }

    /**
     * Sends a JSON payload with a fresh `requestId`. The future completes with the
     * correlated response or fails with [TimeoutException] after [requestTimeoutMs].
     */
    fun sendRequest(
        namespace: String,
        destinationId: String,
        sourceId: String = "sender-0",
        payloadJson: String
    ): CompletableFuture<CastMessage> {
        if (closed.get()) {
            return failedFuture(IOException("Channel closed"))
        }
        val requestId = nextRequestId.getAndIncrement()
        val json = injectRequestId(payloadJson, requestId)
        val message = CastMessage(
            sourceId = sourceId,
            destinationId = destinationId,
            namespace = namespace,
            payloadType = CastPayloadType.STRING,
            payloadUtf8 = json
        )

        val future = CompletableFuture<CastMessage>()
        pending[requestId] = future
        try {
            send(message)
        } catch (e: Exception) {
            pending.remove(requestId)
            future.completeExceptionally(e)
            return future
        }

        val sched = scheduler
        if (sched == null) {
            pending.remove(requestId)
            future.completeExceptionally(IOException("Channel not open"))
            return future
        }
        try {
            sched.schedule({
                if (pending.remove(requestId) != null) {
                    future.completeExceptionally(
                        TimeoutException("No response for requestId $requestId within ${requestTimeoutMs}ms")
                    )
                }
            }, requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            pending.remove(requestId)
            future.completeExceptionally(IOException("Channel closed"))
        }
        return future
    }

    override fun close() {
        terminate(null, IOException("Channel closed"))
    }

    private fun runReaderLoop(activeSocket: Socket) {
        try {
            val input = activeSocket.getInputStream()
            val header = ByteArray(4)
            while (!closed.get()) {
                readFully(input, header)
                val length = ((header[0].toInt() and 0xff) shl 24) or
                        ((header[1].toInt() and 0xff) shl 16) or
                        ((header[2].toInt() and 0xff) shl 8) or
                        (header[3].toInt() and 0xff)
                // A set high bit means a length above Int.MAX_VALUE, also out of range.
                if (length < 0 || length > CastMessageCodec.MAX_MESSAGE_SIZE) {
                    throw CastProtocolException("Frame length ${length.toUInt()} exceeds ${CastMessageCodec.MAX_MESSAGE_SIZE}")
                }
                val body = ByteArray(length)
                readFully(input, body)
                dispatch(CastMessageCodec.decode(body))
            }
        } catch (e: Exception) {
            if (!closed.get()) {
                terminate(e, e)
            }
        }
    }

    private fun dispatch(message: CastMessage) {
        val requestId = extractRequestId(message.payloadUtf8)
        if (requestId != null && requestId > 0) {
            val future = pending.remove(requestId)
            if (future != null) {
                future.complete(message)
                return
            }
        }
        for (listener in listeners) {
            try {
                listener(message)
            } catch (_: Exception) {
                // A failing listener must not stop the reader thread.
            }
        }
    }

    private fun terminate(cause: Throwable?, pendingCause: Throwable?) {
        if (!closed.compareAndSet(false, true)) return
        if (pendingCause != null) {
            cancelPending(pendingCause)
        }
        socket?.let { closeQuietly(it) }
        scheduler?.shutdownNow()
        onClose?.invoke(cause)
    }

    private fun cancelPending(cause: Throwable) {
        val futures = pending.values.toList()
        pending.clear()
        for (future in futures) {
            future.completeExceptionally(cause)
        }
    }

    private fun failedFuture(cause: Throwable): CompletableFuture<CastMessage> {
        val future = CompletableFuture<CastMessage>()
        future.completeExceptionally(cause)
        return future
    }

    private fun closeQuietly(target: Socket) {
        try {
            target.close()
        } catch (_: IOException) {
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                throw EOFException("Unexpected end of stream")
            }
            offset += read
        }
    }

    companion object {
        /** First numeric `"requestId"` value in [json], or null if absent. */
        private fun extractRequestId(json: String?): Int? {
            if (json == null) return null
            val key = "\"requestId\""
            var searchFrom = 0
            while (true) {
                val idx = json.indexOf(key, searchFrom)
                if (idx < 0) return null
                var i = idx + key.length
                while (i < json.length && (json[i] == ' ' || json[i] == '\t' || json[i] == '\n' || json[i] == '\r')) i++
                if (i >= json.length || json[i] != ':') {
                    searchFrom = idx + 1
                    continue
                }
                i++
                while (i < json.length && (json[i] == ' ' || json[i] == '\t' || json[i] == '\n' || json[i] == '\r')) i++
                if (i >= json.length || json[i] !in '0'..'9') {
                    searchFrom = idx + 1
                    continue
                }
                var j = i
                while (j < json.length && json[j] in '0'..'9') j++
                return json.substring(i, j).toIntOrNull()
            }
        }

        /** Replaces an existing numeric `"requestId"` or appends one to the top-level object. */
        private fun injectRequestId(json: String, requestId: Int): String {
            val key = "\"requestId\""
            val idx = json.indexOf(key)
            if (idx >= 0) {
                val colon = json.indexOf(':', idx + key.length)
                if (colon >= 0) {
                    var start = colon + 1
                    while (start < json.length && (json[start] == ' ' || json[start] == '\t' || json[start] == '\n' || json[start] == '\r')) start++
                    var end = start
                    while (end < json.length && json[end] in '0'..'9') end++
                    if (end > start) {
                        return json.substring(0, start) + requestId.toString() + json.substring(end)
                    }
                }
            }
            val trimmed = json.trim()
            require(trimmed.startsWith("{") && trimmed.endsWith("}")) { "payload must be a JSON object" }
            val inner = trimmed.substring(0, trimmed.length - 1).trimEnd()
            return if (inner == "{") {
                "{\"requestId\":$requestId}"
            } else {
                "$inner,\"requestId\":$requestId}"
            }
        }
    }
}
