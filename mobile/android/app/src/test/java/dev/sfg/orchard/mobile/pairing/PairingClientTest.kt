// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class PairingClientTest {
    private val servers = mutableListOf<TvPairingServer>()
    private val connections = CopyOnWriteArrayList<Int>()
    private val client = PairingClient { address ->
        connections += address.port
        Socket().apply { connect(address, 2_000) }
    }

    private fun start(listener: RecordingListener) =
        TvPairingServer(LOOPBACK, "Orchard TV Test", listener).also { servers += it }

    @After
    fun tearDown() = servers.forEach { it.close() }

    @Test
    fun fingerprintMismatchAbortsBeforeKeyExchange() {
        val listener = RecordingListener()
        val server = start(listener)
        expectProtocolError {
            client.prepare("127.0.0.1", server.port, PairingMessages.MODE_QR, server.token, PairingCrypto.randomBytes(32))
        }
        assertEquals("only /v1/hello was sent", 1, connections.size)
        assertTrue(listener.sasCodes.isEmpty())
        assertTrue(listener.payloads.isEmpty())
    }

    @Test
    fun nothingIsSentWithoutConfirmation() {
        val listener = RecordingListener()
        val server = start(listener)
        val prepared = client.prepare("127.0.0.1", server.port, PairingMessages.MODE_QR, server.token, server.fingerprint)
        assertEquals("hello and key only", 2, connections.size)
        Thread.sleep(200)
        assertTrue(listener.payloads.isEmpty())
        assertEquals(PairingServerState.Bound, server.state)

        prepared.wipe()
        assertArrayEquals(ByteArray(32), prepared.exchange.key)
    }

    @Test
    fun qrModeNeedsTokenAndFingerprintBeforeConnecting() {
        expectProtocolError { client.prepare("127.0.0.1", 40000, PairingMessages.MODE_QR, null, PairingCrypto.randomBytes(32)) }
        expectProtocolError { client.prepare("127.0.0.1", 40000, PairingMessages.MODE_QR, PairingCrypto.randomBytes(16), null) }
        assertTrue(connections.isEmpty())
    }

    @Test
    fun sendWipesTheSessionKey() {
        val listener = RecordingListener()
        val server = start(listener)
        val prepared = client.prepare("127.0.0.1", server.port, PairingMessages.MODE_SAS)
        client.send(prepared, PairingSessionPayload.from(testSession(), null))
        assertEquals(3, connections.size)
        assertArrayEquals(ByteArray(32), prepared.exchange.key)
    }

    @Test
    fun serverErrorsSurfaceAsProtocolErrors() {
        val listener = RecordingListener()
        val server = start(listener)
        expectProtocolError {
            client.prepare("127.0.0.1", server.port, PairingMessages.MODE_QR, PairingCrypto.randomBytes(16), server.fingerprint)
        }
    }
}
