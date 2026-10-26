// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

internal class RecordingListener : TvPairingServer.Listener {
    val states = CopyOnWriteArrayList<PairingServerState>()
    val sasCodes = CopyOnWriteArrayList<String>()
    val payloads = CopyOnWriteArrayList<PairingSessionPayload>()
    val provisionBindings = CopyOnWriteArrayList<ByteArray>()
    val received = CountDownLatch(1)
    val closed = CountDownLatch(1)
    val sas = CountDownLatch(1)

    override fun onState(state: PairingServerState) {
        states += state
        if (state == PairingServerState.Closed) closed.countDown()
    }

    override fun onSas(sas: String) {
        sasCodes += sas
        this.sas.countDown()
    }

    override fun onSessionReceived(payload: PairingSessionPayload, provisionBinding: ByteArray) {
        payloads += payload
        provisionBindings += provisionBinding
        received.countDown()
    }

    fun awaitClosed(seconds: Long = 5) = closed.await(seconds, TimeUnit.SECONDS)
}

internal data class RawResponse(val code: Int?, val body: String)

/** Sends one raw HTTP request and returns the status code, or null when the server hung up. */
internal fun rawPost(port: Int, path: String, body: ByteArray, declaredLength: Int = body.size): RawResponse {
    Socket(LOOPBACK, port).use { socket ->
        socket.soTimeout = 5_000
        val out = socket.getOutputStream()
        val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n" +
            "Content-Length: $declaredLength\r\nConnection: close\r\n\r\n"
        runCatching {
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
        }
        val bytes = ByteArrayOutputStream()
        runCatching { socket.getInputStream().copyTo(bytes) }
        val text = bytes.toString(Charsets.ISO_8859_1.name())
        if (text.isEmpty()) return RawResponse(null, "")
        val code = text.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull()
        return RawResponse(code, text.substringAfter("\r\n\r\n", ""))
    }
}

internal fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        Thread.sleep(20)
    }
    return condition()
}
