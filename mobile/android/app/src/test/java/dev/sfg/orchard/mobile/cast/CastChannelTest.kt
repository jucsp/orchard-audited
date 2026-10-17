// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

class CastChannelTest {

    @Test
    fun rejectsPublicHost() {
        val channel = CastChannel("8.8.8.8", 8009, "device-1", InMemoryCastTrustStore())
        val ex = expectCastSecurity { channel.open() }
        assertTrue(ex.message!!.contains("8.8.8.8"))
    }

    @Test
    fun rejectsCgnatHost() {
        val channel = CastChannel("100.64.0.1", 8009, "device-1", InMemoryCastTrustStore())
        expectCastSecurity { channel.open() }
    }

    @Test
    fun writesEncodedFrameToSocket() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "192.168.1.50", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        try {
            channel.open()
            val message = CastMessage(
                sourceId = "sender-0",
                destinationId = "receiver-0",
                namespace = "urn:x-cast:com.example",
                payloadType = CastPayloadType.STRING,
                payloadUtf8 = "{\"type\":\"PING\"}"
            )
            channel.send(message)
            val decoded = readFrame(server.getInputStream())
            assertEquals(message, decoded)
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun readsFrameAndDispatchesBroadcast() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "10.0.0.8", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        val latch = CountDownLatch(1)
        val received = AtomicReference<CastMessage>()
        channel.addMessageListener { msg ->
            received.set(msg)
            latch.countDown()
        }
        try {
            channel.open()
            val message = CastMessage(
                sourceId = "receiver-0",
                destinationId = "sender-0",
                namespace = "urn:x-cast:com.example",
                payloadType = CastPayloadType.STRING,
                payloadUtf8 = "{\"type\":\"MEDIA_STATUS\"}"
            )
            writeFrame(server.getOutputStream(), message)
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals(message, received.get())
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun sendRequestCorrelatesByRequestId() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "192.168.1.50", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        try {
            channel.open()
            val future = channel.sendRequest(
                "urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"PING\"}"
            )
            val request = readFrame(server.getInputStream())
            val id = requestIdOf(request.payloadUtf8!!)
            assertTrue(id > 0)

            val response = CastMessage(
                sourceId = "receiver-0",
                destinationId = "sender-0",
                namespace = "urn:x-cast:com.example",
                payloadType = CastPayloadType.STRING,
                payloadUtf8 = "{\"requestId\":$id,\"type\":\"PONG\"}"
            )
            writeFrame(server.getOutputStream(), response)

            val result = future.get(5, TimeUnit.SECONDS)
            assertEquals("{\"requestId\":$id,\"type\":\"PONG\"}", result.payloadUtf8)
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun requestIdsIncreaseMonotonically() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "10.1.2.3", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        try {
            channel.open()
            channel.sendRequest("urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"A\"}")
            val id1 = requestIdOf(readFrame(server.getInputStream()).payloadUtf8!!)
            channel.sendRequest("urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"B\"}")
            val id2 = requestIdOf(readFrame(server.getInputStream()).payloadUtf8!!)
            assertTrue(id1 >= 1)
            assertEquals(id1 + 1, id2)
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun sendRequestTimesOut() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "10.0.0.8", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client),
            requestTimeoutMs = 150L
        )
        try {
            channel.open()
            val future = channel.sendRequest(
                "urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"PING\"}"
            )
            // Consumir la petición para dejar el stream limpio; no se responde.
            readFrame(server.getInputStream())
            val cause = awaitExceptionally(future)
            assertTrue(cause is TimeoutException)
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun closeCancelsPendingRequests() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "192.168.1.50", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        try {
            channel.open()
            val future = channel.sendRequest(
                "urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"PING\"}"
            )
            readFrame(server.getInputStream())
            channel.close()
            val cause = awaitExceptionally(future)
            assertTrue(cause is IOException)
            assertTrue(cause.message!!.contains("Channel closed"))
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun oversizedFrameClosesChannelAndFailsPending() {
        assertHeaderClosesChannel(byteArrayOf(0x00, 0x01, 0x00, 0x01))
    }

    @Test
    fun highBitLengthClosesChannelAndFailsPending() {
        assertHeaderClosesChannel(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x10))
    }

    @Test
    fun malformedBodyClosesChannel() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "192.168.1.50", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        val closedWith = CompletableFuture<Throwable?>()
        channel.onClose = { closedWith.complete(it) }
        try {
            channel.open()
            // Field 2 declares 100 bytes but the body ends after 1.
            server.getOutputStream().apply { write(byteArrayOf(0, 0, 0, 3, 0x12, 100, 0x41)); flush() }
            assertTrue(closedWith.get(5, TimeUnit.SECONDS) is CastProtocolException)
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun peerDisconnectInvokesOnCloseOnce() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "10.0.0.8", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val closedWith = CompletableFuture<Throwable?>()
        channel.onClose = { calls.incrementAndGet(); closedWith.complete(it) }
        channel.open()
        server.close()
        assertTrue(closedWith.get(5, TimeUnit.SECONDS) is IOException)
        channel.close()
        assertEquals(1, calls.get())
        assertTrue(awaitExceptionally(channel.sendRequest("ns", "receiver-0", payloadJson = "{}")) is IOException)
    }

    @Test
    fun throwingListenerDoesNotStopReader() {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "10.0.0.8", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        val latch = CountDownLatch(2)
        channel.addMessageListener { throw IllegalStateException("boom") }
        channel.addMessageListener { latch.countDown() }
        try {
            channel.open()
            val message = CastMessage(
                sourceId = "receiver-0",
                destinationId = "sender-0",
                namespace = "urn:x-cast:com.example",
                payloadType = CastPayloadType.STRING,
                payloadUtf8 = "{\"type\":\"MEDIA_STATUS\",\"requestId\":0}"
            )
            writeFrame(server.getOutputStream(), message)
            writeFrame(server.getOutputStream(), message)
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        } finally {
            channel.close()
            server.close()
        }
    }

    @Test
    fun hostWithLeadingZerosIsRejectedBeforeConnecting() {
        val factory = object : SocketFactory() {
            override fun createSocket(): Socket = throw AssertionError("socket must not be created")
            override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException()
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
                throw UnsupportedOperationException()
            override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException()
            override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
                throw UnsupportedOperationException()
        }
        val channel = CastChannel("010.0.0.1", 8009, "device-1", InMemoryCastTrustStore(), factory)
        expectCastSecurity { channel.open() }
    }

    @Test
    fun nonObjectPayloadIsRejected() {
        val channel = CastChannel("10.0.0.8", 0, "device-1", InMemoryCastTrustStore())
        try {
            channel.sendRequest("urn:x-cast:com.example", "receiver-0", payloadJson = "[1]")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun assertHeaderClosesChannel(header: ByteArray) {
        val (client, server) = loopbackPair()
        val channel = CastChannel(
            "192.168.1.50", 0, "device-1", InMemoryCastTrustStore(), SingleSocketFactory(client)
        )
        val closedWith = CompletableFuture<Throwable?>()
        channel.onClose = { closedWith.complete(it) }
        try {
            channel.open()
            val future = channel.sendRequest(
                "urn:x-cast:com.example", "receiver-0", payloadJson = "{\"type\":\"PING\"}"
            )
            readFrame(server.getInputStream())
            server.getOutputStream().apply { write(header); flush() }
            assertTrue(closedWith.get(5, TimeUnit.SECONDS) is CastProtocolException)
            assertTrue(awaitExceptionally(future) is CastProtocolException)
            assertTrue(client.isClosed)
        } finally {
            channel.close()
            server.close()
        }
    }

    // ------------------------------------------------------------------ helpers

    private class SingleSocketFactory(private val socket: Socket) : SocketFactory() {
        override fun createSocket(): Socket = socket
        override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            throw UnsupportedOperationException()
        override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            throw UnsupportedOperationException()
    }

    private fun loopbackPair(): Pair<Socket, Socket> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val client = Socket("127.0.0.1", server.localPort)
        val serverSide = server.accept()
        server.close()
        return Pair(client, serverSide)
    }

    private fun readFrame(input: InputStream): CastMessage {
        val header = ByteArray(4)
        readFully(input, header)
        val length = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
        val body = ByteArray(length)
        readFully(input, body)
        return CastMessageCodec.decode(body)
    }

    private fun writeFrame(output: OutputStream, message: CastMessage) {
        output.write(CastMessageCodec.encodeFrame(message))
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) {
                fail("Unexpected end of stream")
            }
            offset += read
        }
    }

    private fun requestIdOf(json: String): Int =
        Regex("\"requestId\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toInt()

    private fun expectCastSecurity(body: () -> Unit): CastSecurityException {
        try {
            body()
        } catch (e: CastSecurityException) {
            return e
        }
        fail("Expected CastSecurityException")
        throw AssertionError("unreachable")
    }

    private fun awaitExceptionally(future: CompletableFuture<*>, timeoutMs: Long = 5000): Throwable {
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            return e.cause ?: e
        } catch (e: TimeoutException) {
            fail("Timed out waiting for future to complete exceptionally")
        }
        fail("Expected future to complete exceptionally")
        throw AssertionError("unreachable")
    }
}
