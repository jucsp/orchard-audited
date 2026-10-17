/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * JVM tests for CastSession using an in-memory loopback receiver. No real
 * network access and no android.* imports.
 */
package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class CastSessionTest {

    @Test
    fun `default media receiver app id is fixed`() {
        assertEquals("CC1AD845", CastSession.APP_ID_DEFAULT_MEDIA_RECEIVER)
    }

    @Test
    fun `connectAndLaunch launches CC1AD845 and reaches READY`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.accept()
            val connect = receiver.readMessage()!!
            assertEquals(CastSession.NS_CONNECTION, connect.namespace)
            assertEquals("receiver-0", connect.destinationId)
            assertEquals("""{"type":"CONNECT"}""", connect.payloadUtf8)

            val getStatus = receiver.readMessage()!!
            assertEquals(CastSession.NS_RECEIVER, getStatus.namespace)
            assertTrue(getStatus.payloadUtf8!!.contains("GET_STATUS"))
            val getReq = requestIdOf(getStatus.payloadUtf8!!)
            receiver.sendMessage(receiverMessage("sender-0", CastSession.NS_RECEIVER, receiverStatus(getReq)))

            val launch = receiver.readMessage()!!
            assertTrue(launch.payloadUtf8!!.contains("LAUNCH"))
            assertTrue(launch.payloadUtf8!!.contains("CC1AD845"))
            val launchReq = requestIdOf(launch.payloadUtf8!!)
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    CastSession.NS_RECEIVER,
                    receiverStatus(launchReq, apps = listOf(appJson("CC1AD845", "session-1", "transport-1")), level = 0.8f, muted = true)
                )
            )

            val connectTransport = receiver.readMessage()!!
            assertEquals(CastSession.NS_CONNECTION, connectTransport.namespace)
            assertEquals("transport-1", connectTransport.destinationId)
        }
        val session = sessionFor(receiver)
        val states = CopyOnWriteArrayList<CastSessionState>()
        session.addStateListener { states.add(it) }
        try {
            session.connectAndLaunch()
            assertEquals(CastSessionState.READY, session.state)
            assertEquals("session-1", session.sessionId)
            assertEquals("transport-1", session.transportId)
            assertEquals(0.8, (session.receiverVolume?.level ?: -1.0f).toDouble(), 0.001)
            assertTrue(session.receiverVolume?.muted ?: false)
            assertEquals(
                listOf(CastSessionState.CONNECTING, CastSessionState.CONNECTED, CastSessionState.LAUNCHING, CastSessionState.READY),
                states.toList()
            )
        } finally {
            receiverThread.join(2000)
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `reuses CC1AD845 when already running without relaunching`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.accept()
            receiver.readMessage() // CONNECT to receiver-0
            val getStatus = receiver.readMessage()!!
            val req = requestIdOf(getStatus.payloadUtf8!!)
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    CastSession.NS_RECEIVER,
                    receiverStatus(req, apps = listOf(appJson("CC1AD845", "session-2", "transport-2")))
                )
            )

            val connectTransport = receiver.readMessage()!!
            assertEquals(CastSession.NS_CONNECTION, connectTransport.namespace)
            assertEquals("transport-2", connectTransport.destinationId)
        }
        val session = sessionFor(receiver)
        try {
            session.connectAndLaunch()
            assertEquals(CastSessionState.READY, session.state)
            assertEquals("session-2", session.sessionId)
            assertEquals("transport-2", session.transportId)
        } finally {
            receiverThread.join(2000)
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `sends periodic PING and answers inbound PING with PONG`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.handshakeWithRunningApp("session-3", "transport-3")

            val ping = receiver.readMessage(2000)!!
            assertEquals(CastSession.NS_HEARTBEAT, ping.namespace)
            assertEquals("""{"type":"PING"}""", ping.payloadUtf8)
            assertEquals("receiver-0", ping.destinationId)

            receiver.sendMessage(receiverMessage("sender-0", CastSession.NS_HEARTBEAT, """{"type":"PING"}"""))
            // Periodic PINGs may interleave; skip them until the PONG shows up.
            val pong = receiver.readUntil(CastSession.NS_HEARTBEAT, "PONG", 2000)!!
            assertEquals("""{"type":"PONG"}""", pong.payloadUtf8)
            assertEquals("receiver-0", pong.destinationId)
        }
        val session = sessionFor(receiver, heartbeatIntervalMs = 300, heartbeatTimeoutMs = 10000)
        try {
            session.connectAndLaunch()
            assertEquals(CastSessionState.READY, session.state)
            receiverThread.join(5000)
        } finally {
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `volume and mute are sent as separate SET_VOLUME requests`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.accept()
            receiver.readMessage() // CONNECT to receiver-0
            val getStatus = receiver.readMessage()!!
            val req = requestIdOf(getStatus.payloadUtf8!!)
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    CastSession.NS_RECEIVER,
                    receiverStatus(req, apps = listOf(appJson("CC1AD845", "session-4", "transport-4")), level = 0.5f, muted = false)
                )
            )
            receiver.readMessage() // CONNECT to transport-4

            val setVolume = receiver.readUntil(CastSession.NS_RECEIVER, "SET_VOLUME")!!
            assertTrue(setVolume.payloadUtf8!!.contains("\"level\":0.3"))
            assertFalse(setVolume.payloadUtf8!!.contains("muted"))
            val volReq = requestIdOf(setVolume.payloadUtf8!!)
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    CastSession.NS_RECEIVER,
                    receiverStatus(volReq, apps = listOf(appJson("CC1AD845", "session-4", "transport-4")), level = 0.3f, muted = false)
                )
            )

            val setMuted = receiver.readUntil(CastSession.NS_RECEIVER, "SET_VOLUME")!!
            assertTrue(setMuted.payloadUtf8!!.contains("\"muted\":true"))
            assertFalse(setMuted.payloadUtf8!!.contains("level"))
        }
        val session = sessionFor(receiver)
        try {
            session.connectAndLaunch()
            assertEquals(0.5, (session.receiverVolume?.level ?: -1.0f).toDouble(), 0.001)
            session.setDeviceVolume(0.3f)
            assertTrue(awaitCondition { (session.receiverVolume?.level ?: -1.0f) == 0.3f })
            session.setDeviceMuted(true)
            receiverThread.join(3000)
            assertEquals(CastSessionState.READY, session.state)
        } finally {
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `close sends CLOSE and transitions to DISCONNECTED`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.handshakeWithRunningApp("session-5", "transport-5")

            val closeTransport = receiver.readMessage(2000)!!
            assertEquals(CastSession.NS_CONNECTION, closeTransport.namespace)
            assertEquals("transport-5", closeTransport.destinationId)
            assertEquals("""{"type":"CLOSE"}""", closeTransport.payloadUtf8)

            val closeReceiver = receiver.readMessage(2000)!!
            assertEquals(CastSession.NS_CONNECTION, closeReceiver.namespace)
            assertEquals("receiver-0", closeReceiver.destinationId)
        }
        val session = sessionFor(receiver)
        session.connectAndLaunch()
        assertEquals(CastSessionState.READY, session.state)
        session.close()
        assertEquals(CastSessionState.DISCONNECTED, session.state)
        receiverThread.join(2000)
        receiver.close()
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `inbound CLOSE from the app ends the session`() {
        assertEndsSession { receiver ->
            receiver.sendMessage(
                receiverMessage("sender-0", CastSession.NS_CONNECTION, """{"type":"CLOSE"}""", sourceId = "transport-6")
            )
        }
    }

    @Test
    fun `invalid JSON payload closes the channel`() {
        assertEndsSession { receiver ->
            receiver.sendMessage(receiverMessage("sender-0", CastSession.NS_RECEIVER, """{"type":"""))
        }
    }

    @Test
    fun `RECEIVER_STATUS without the session app ends the session`() {
        assertEndsSession { receiver ->
            receiver.sendMessage(receiverMessage("sender-0", CastSession.NS_RECEIVER, receiverStatus(0)))
        }
    }

    @Test
    fun `heartbeat timeout reconnects once and reaches READY again`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.handshakeWithRunningApp("session-7", "transport-7")
            // Stay silent: the session must drop this socket and open a new one.
            receiver.handshakeWithRunningApp("session-7b", "transport-7b")
        }
        val session = sessionFor(receiver, heartbeatIntervalMs = 100, heartbeatTimeoutMs = 400)
        val states = CopyOnWriteArrayList<CastSessionState>()
        session.addStateListener { states.add(it) }
        try {
            session.connectAndLaunch()
            assertTrue(awaitCondition(5000) { states.count { it == CastSessionState.READY } >= 2 })
            val snapshot = states.toList()
            assertFalse(snapshot.contains(CastSessionState.DISCONNECTED))
            assertEquals("session-7b", session.sessionId)
            assertEquals("transport-7b", session.transportId)
        } finally {
            receiverThread.join(2000)
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `failed reconnection after heartbeat timeout ends in DISCONNECTED`() {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.handshakeWithRunningApp("session-8", "transport-8")
            receiver.stopAccepting()
        }
        val session = sessionFor(receiver, heartbeatIntervalMs = 100, heartbeatTimeoutMs = 400)
        val states = CopyOnWriteArrayList<CastSessionState>()
        session.addStateListener { states.add(it) }
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition(5000) { session.state == CastSessionState.DISCONNECTED })
            val afterReady = states.toList().dropWhile { it != CastSessionState.READY }
            assertEquals(CastSessionState.CONNECTING, afterReady.getOrNull(1))
        } finally {
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    private fun assertEndsSession(trigger: (FakeCastReceiver) -> Unit) {
        val receiver = FakeCastReceiver()
        val receiverThread = script(receiver) {
            receiver.handshakeWithRunningApp("session-6", "transport-6")
        }
        val session = sessionFor(receiver, heartbeatIntervalMs = 5000, heartbeatTimeoutMs = 15000)
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            // Triggered from the test thread so the session is already READY.
            trigger(receiver)
            assertTrue(awaitCondition { session.state == CastSessionState.DISCONNECTED })
        } finally {
            receiverThread.join(2000)
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    private fun script(receiver: FakeCastReceiver, body: () -> Unit): Thread =
        Thread {
            try {
                body()
            } catch (t: Throwable) {
                receiver.error = t
            }
        }.apply { start() }

    private fun sessionFor(
        receiver: FakeCastReceiver,
        heartbeatIntervalMs: Long = 5000,
        heartbeatTimeoutMs: Long = 15000
    ): CastSession {
        val channel = CastChannel("10.0.0.8", receiver.port, "test-device", InMemoryCastTrustStore(), receiver.connectedSocketFactory())
        return CastSession(channel, heartbeatIntervalMs = heartbeatIntervalMs, heartbeatTimeoutMs = heartbeatTimeoutMs)
    }
}
