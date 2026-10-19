// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TvPairingServerTest {
    private val servers = mutableListOf<TvPairingServer>()
    private val client = PairingClient()

    private fun start(listener: RecordingListener, clock: () -> Long = System::currentTimeMillis): TvPairingServer =
        TvPairingServer(LOOPBACK, "Orchard TV Test", listener, now = clock).also { servers += it }

    @After
    fun tearDown() = servers.forEach { it.close() }

    private fun sasHello(): ByteArray = PairingMessages.helloRequestJson(
        PairingMessages.MODE_SAS, null, PairingCrypto.randomBytes(32),
    ).toByteArray()

    @Test
    fun qrFlowDeliversSessionAndStopsListening() {
        val listener = RecordingListener()
        val server = start(listener)
        val prepared = client.prepare("127.0.0.1", server.port, PairingMessages.MODE_QR, server.token, server.fingerprint)
        assertEquals("Orchard TV Test", prepared.tvName)
        assertTrue(listener.sasCodes.isEmpty())

        val status = client.send(prepared, PairingSessionPayload.from(testSession(), "spdc"))
        assertEquals("pending_tv_confirm", status)
        assertTrue(listener.received.await(5, TimeUnit.SECONDS))
        assertEquals(testSession(), listener.payloads.single().toYouTubeSession())
        assertEquals("spdc", listener.payloads.single().spotify?.spDc)
        assertEquals(PairingServerState.Received, server.state)

        // Single use: the listening socket is gone after a valid session.
        assertTrue(waitUntil { runCatching { rawPost(server.port, "/v1/hello", sasHello()) }.isFailure })
    }

    @Test
    fun sasFlowShowsTheSameCodeOnBothSides() {
        val listener = RecordingListener()
        val server = start(listener)
        val prepared = client.prepare("127.0.0.1", server.port, PairingMessages.MODE_SAS)
        assertTrue(listener.sas.await(5, TimeUnit.SECONDS))
        assertEquals(prepared.sas, listener.sasCodes.single())
        client.send(prepared, PairingSessionPayload.from(testSession(), null))
        assertTrue(listener.received.await(5, TimeUnit.SECONDS))
        assertNull(listener.payloads.single().spotify)
    }

    @Test
    fun wrongTokenIsForbiddenAndFiveFailuresCloseTheServer() {
        val listener = RecordingListener()
        val server = start(listener)
        repeat(TvPairingServer.MAX_FAILURES) { attempt ->
            val body = PairingMessages.helloRequestJson(PairingMessages.MODE_QR, PairingCrypto.randomBytes(16), PairingCrypto.randomBytes(32))
            val response = rawPost(server.port, "/v1/hello", body.toByteArray())
            assertEquals("attempt $attempt", 403, response.code)
            assertEquals("bad_token", PairingMessages.parseErrorCode(response.body))
        }
        assertTrue(listener.awaitClosed())
        assertEquals(PairingServerState.Closed, server.state)
    }

    @Test
    fun secondPhoneGets409WithoutCountingAsFailure() {
        val listener = RecordingListener()
        val server = start(listener)
        assertEquals(200, rawPost(server.port, "/v1/hello", sasHello()).code)
        repeat(TvPairingServer.MAX_FAILURES + 1) {
            assertEquals(409, rawPost(server.port, "/v1/hello", sasHello()).code)
        }
        assertEquals(PairingServerState.Bound, server.state)
    }

    @Test
    fun oversizedBodyIsDroppedAndCounted() {
        val listener = RecordingListener()
        val server = start(listener)
        repeat(TvPairingServer.MAX_FAILURES) {
            val response = rawPost(server.port, "/v1/hello", ByteArray(0), declaredLength = 65 * 1024)
            assertNull(response.code)
        }
        assertTrue(listener.awaitClosed())
    }

    @Test
    fun malformedJsonAndUnknownVersionAreBadRequests() {
        val listener = RecordingListener()
        val server = start(listener)
        assertEquals(400, rawPost(server.port, "/v1/hello", "{not json".toByteArray()).code)
        val v2 = JSONObject(String(sasHello())).put("v", 2).toString()
        assertEquals(400, rawPost(server.port, "/v1/hello", v2.toByteArray()).code)
        assertEquals(PairingServerState.Waiting, server.state)
    }

    @Test
    fun commitmentMismatchAbortsTheBinding() {
        val listener = RecordingListener()
        val server = start(listener)
        val hello = PairingMessages.parseHelloResponse(rawPost(server.port, "/v1/hello", sasHello()).body)
        val other = PairingCrypto.generateKeyPair()
        val key = PairingMessages.keyRequestJson(hello.sessionId, other.publicKey, PairingCrypto.randomBytes(16))
        val response = rawPost(server.port, "/v1/key", key.toByteArray())
        assertEquals(403, response.code)
        assertEquals("bad_commitment", PairingMessages.parseErrorCode(response.body))
        assertEquals(PairingServerState.Waiting, server.state)
    }

    @Test
    fun tamperedSessionIsRejected() {
        val listener = RecordingListener()
        val server = start(listener)
        val prepared = client.prepare("127.0.0.1", server.port, PairingMessages.MODE_SAS)
        val sealed = PairingCrypto.seal(PairingCrypto.randomBytes(32), "{}".toByteArray(), ByteArray(32))
        val body = PairingMessages.sessionRequestJson(server.sessionId, sealed)
        val response = rawPost(server.port, "/v1/session", body.toByteArray())
        assertEquals(403, response.code)
        assertEquals("bad_seal", PairingMessages.parseErrorCode(response.body))
        assertTrue(listener.payloads.isEmpty())
        prepared.wipe()
    }

    @Test
    fun expiresWithoutAnyConnection() {
        val clock = AtomicLong(1_000_000L)
        val listener = RecordingListener()
        val server = start(listener) { clock.get() }
        assertEquals(PairingServerState.Waiting, server.state)
        clock.addAndGet(TvPairingServer.MAX_LIFETIME_MS + 1)
        assertTrue(listener.awaitClosed())
        assertEquals(PairingServerState.Closed, server.state)
    }

    @Test
    fun closeIsIdempotentAndReleasesThePort() {
        val listener = RecordingListener()
        val server = start(listener)
        val port = server.port
        server.close()
        server.close()
        assertEquals(1, listener.states.count { it == PairingServerState.Closed })
        try {
            rawPost(port, "/v1/hello", sasHello()).also { if (it.code != null) fail("server still answering") }
        } catch (expected: java.io.IOException) {
            // connection refused
        }
        ServerSocket(port, 1, LOOPBACK).close()
    }

    @Test
    fun eachServerHasFreshMaterial() {
        val first = start(RecordingListener())
        val second = start(RecordingListener())
        assertTrue(!first.token.contentEquals(second.token))
        assertTrue(!first.fingerprint.contentEquals(second.fingerprint))
        assertTrue(!first.sessionId.contentEquals(second.sessionId))
        assertEquals(16, first.token.size)
    }
}
