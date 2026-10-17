/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * In-memory loopback Cast receiver for CastSession tests. Plain TCP on
 * 127.0.0.1, no TLS and no android.* imports.
 */
package dev.sfg.orchard.mobile.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.SocketFactory

internal class FakeCastReceiver {
    private val server = ServerSocket(0)
    val port: Int = server.localPort
    @Volatile var error: Throwable? = null
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    fun connectedSocketFactory(): SocketFactory {
        val serverPort = port
        return object : SocketFactory() {
            override fun createSocket(): Socket = Socket("127.0.0.1", serverPort)
            override fun createSocket(host: String, port: Int): Socket = Socket("127.0.0.1", serverPort)
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
                Socket("127.0.0.1", serverPort)
            override fun createSocket(host: InetAddress, port: Int): Socket = Socket("127.0.0.1", serverPort)
            override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
                Socket("127.0.0.1", serverPort)
        }
    }

    /** Accepts the next sender connection, dropping the previous one. */
    fun accept() {
        val accepted = server.accept()
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        accepted.soTimeout = 2000
        socket = accepted
        input = DataInputStream(accepted.getInputStream())
        output = DataOutputStream(accepted.getOutputStream())
    }

    fun readMessage(timeoutMs: Int = 3000): CastMessage? {
        val s = socket ?: return null
        s.soTimeout = timeoutMs
        return try {
            val header = ByteArray(4)
            readFully(header)
            val length = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            val body = ByteArray(length)
            readFully(body)
            CastMessageCodec.decodeFrame(header + body)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    /** Reads until a message on [namespace] whose payload contains [type], skipping heartbeat noise. */
    fun readUntil(namespace: String, type: String, timeoutMs: Int = 3000): CastMessage? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) return null
            val message = readMessage(remaining) ?: return null
            if (message.namespace == namespace && message.payloadUtf8?.contains("\"$type\"") == true) {
                return message
            }
        }
    }

    fun sendMessage(message: CastMessage) {
        output!!.write(CastMessageCodec.encodeFrame(message))
        output!!.flush()
    }

    /** Runs CONNECT + GET_STATUS with CC1AD845 already running, then consumes the transport CONNECT. */
    fun handshakeWithRunningApp(sessionId: String, transportId: String) {
        accept()
        readMessage() // CONNECT to receiver-0
        val getStatus = readMessage()!!
        val req = requestIdOf(getStatus.payloadUtf8!!)
        sendMessage(
            receiverMessage(
                "sender-0",
                CastSession.NS_RECEIVER,
                receiverStatus(req, apps = listOf(appJson("CC1AD845", sessionId, transportId)))
            )
        )
        readMessage() // CONNECT to transportId
    }

    /** Refuses new connections while keeping the current one open. */
    fun stopAccepting() {
        server.close()
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            server.close()
        } catch (_: Exception) {
        }
    }

    private fun readFully(buffer: ByteArray) {
        val din = input ?: throw IllegalStateException("receiver not accepted")
        var off = 0
        while (off < buffer.size) {
            val n = din.read(buffer, off, buffer.size - off)
            if (n < 0) throw EOFException("stream closed")
            off += n
        }
    }
}

internal fun receiverMessage(destinationId: String, namespace: String, payload: String, sourceId: String = "receiver-0"): CastMessage =
    CastMessage(
        sourceId = sourceId,
        destinationId = destinationId,
        namespace = namespace,
        payloadType = CastPayloadType.STRING,
        payloadUtf8 = payload
    )

internal fun receiverStatus(
    requestId: Int,
    apps: List<String> = emptyList(),
    level: Float = 1.0f,
    muted: Boolean = false
): String {
    val appsJson = apps.joinToString(",")
    return """{"type":"RECEIVER_STATUS","requestId":$requestId,"status":{"applications":[$appsJson],"volume":{"level":$level,"muted":$muted}}}"""
}

internal fun appJson(appId: String, sessionId: String, transportId: String): String =
    """{"appId":"$appId","sessionId":"$sessionId","transportId":"$transportId","statusText":""}"""

internal fun requestIdOf(payload: String): Int =
    Regex(""""requestId"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)?.toInt() ?: 0

/** Polls [condition] until true or [timeoutMs] elapses. */
internal fun awaitCondition(timeoutMs: Long = 3000, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        Thread.sleep(20)
    }
    return condition()
}
