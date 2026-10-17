/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.orchard.mobile.playback

import android.content.ContextWrapper
import dev.sfg.orchard.mobile.auth.YouTubeSessionProvider
import dev.sfg.orchard.mobile.youtube.YouTubeProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAST-INV-4: the local stream server must answer with a 404 unless the request carries the exact
 * capability token and, when a receiver IP is pinned, comes from that IP. Nothing else may be
 * leaked. These tests exercise the gate over a real loopback socket without a network or a real
 * resolver.
 */
class ChromecastStreamServerTest {
    private val servers = mutableListOf<ChromecastStreamServer>()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        servers.clear()
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun token_is_32_hex_characters_without_dashes() {
        val server = newServer()
        assertEquals(32, server.token.length)
        assertTrue("token must be lowercase hex", server.token.matches(Regex("[0-9a-f]{32}")))
        assertFalse(server.token.contains('-'))
    }

    @Test
    fun invalid_token_returns_404() {
        val server = newServer()
        val status = request(server, "/cast/${"0".repeat(32)}/track-id")
        assertEquals(404, status)
    }

    @Test
    fun unknown_path_returns_404() {
        val server = newServer()
        val status = request(server, "/other/track-id")
        assertEquals(404, status)
    }

    @Test
    fun request_from_different_ip_returns_404() {
        val server = newServer(receiverIp = "192.168.1.99")
        val status = request(server, "/cast/${server.token}/track-id")
        assertEquals(404, status)
    }

    @Test
    fun valid_token_without_ip_filter_reaches_resolver() {
        val server = newServer(receiverIp = null)
        val status = request(server, "/cast/${server.token}/track-id")
        // A 502 (not 404) proves both CAST-INV-4 gates passed: the request reached the resolver,
        // whose downloads lookup throws before any provider or network call.
        assertEquals(502, status)
    }

    @Test
    fun valid_token_with_matching_ip_reaches_resolver() {
        val server = newServer(receiverIp = "127.0.0.1")
        val status = request(server, "/cast/${server.token}/track-id")
        assertEquals(502, status)
    }

    @Test
    fun close_stops_accepting_connections() {
        val server = newServer()
        val port = server.port
        server.close()
        val failure =
            runCatching {
                Socket("127.0.0.1", port).use { it.getInputStream().read() }
            }.isFailure
        assertTrue("server must refuse connections after close()", failure)
    }

    @Test
    fun close_drops_connections_waiting_for_a_request() {
        val server = newServer()
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 5_000
            // The server is blocked reading a request line that never arrives.
            Thread.sleep(200)
            server.close()
            val ended =
                try {
                    socket.getInputStream().read() == -1
                } catch (timeout: SocketTimeoutException) {
                    false
                } catch (reset: SocketException) {
                    true
                }
            assertTrue("close() must close in-flight sockets", ended)
        }
    }

    private fun newServer(receiverIp: String? = null): ChromecastStreamServer {
        val provider = YouTubeProvider(OkHttpClient(), tempDir(), { null })
        val resolver =
            YouTubeStreamResolver(
                provider,
                YouTubeSessionProvider { null },
                downloads = { error("resolver is offline in tests") },
            )
        return ChromecastStreamServer(ContextWrapper(null), OkHttpClient(), resolver, receiverIp)
            .also { servers += it }
    }

    private fun tempDir(): File =
        Files.createTempDirectory("orchard-cast-cache").toFile().also { tempDirs += it }

    private fun request(server: ChromecastStreamServer, path: String): Int {
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 10_000
            val request = "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.ISO_8859_1))
            socket.getOutputStream().flush()
            val text = readResponse(socket).toString(StandardCharsets.ISO_8859_1)
            val statusLine = text.lineSequence().firstOrNull() ?: "HTTP/1.1 000"
            return statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        }
    }

    // The IP gate answers without reading the request, so closing with unread input can make the
    // kernel send a reset after the response; keep whatever arrived before it.
    private fun readResponse(socket: Socket): ByteArray {
        val received = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = socket.getInputStream().read(buffer)
                if (read < 0) break
                received.write(buffer, 0, read)
            }
        } catch (reset: SocketException) {
            if (received.size() == 0) throw reset
        }
        return received.toByteArray()
    }
}
