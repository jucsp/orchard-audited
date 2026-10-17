/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * JVM tests for CastMediaChannel edge cases: repeated FINISHED, partial
 * MEDIA_STATUS, empty status, invalid JSON, non-finite positions and reconnection.
 */
package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CastMediaChannelEdgeTest {

    private val nsMedia = CastMediaChannel.NS_MEDIA

    @Test
    fun `onItemFinished fires once per mediaSessionId`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-f2", "transport-f2")
            repeat(2) {
                receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 9, "IDLE", idleReason = "FINISHED")))
            }
            receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 10, "IDLE", idleReason = "FINISHED")))
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        val finished = AtomicInteger(0)
        media.onItemFinished = { finished.incrementAndGet() }
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition { media.currentStatus?.mediaSessionId == 10L })
            assertEquals(2, finished.get())
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `duration is kept when MEDIA_STATUS omits media for the same session`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-dur", "transport-dur")
            receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 5, "PLAYING", duration = 200.0)))
            receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 5, "PLAYING", currentTime = 3.0)))
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition { media.currentStatus?.currentTime == 3.0 })
            assertEquals(200.0, media.currentStatus?.duration ?: -1.0, 0.001)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `GET_STATUS with empty status resolves null and clears the media session`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-empty", "transport-empty")
            receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 4, "PLAYING")))
            val get = receiver.readUntil(nsMedia, "GET_STATUS")!!
            assertEquals(4L, mediaSessionIdOf(get.payloadUtf8!!))
            receiver.sendMessage(
                receiverMessage("sender-0", nsMedia, """{"type":"MEDIA_STATUS","requestId":${requestIdOf(get.payloadUtf8!!)},"status":[]}""")
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            assertTrue(awaitCondition { media.currentStatus?.mediaSessionId == 4L })
            assertNull(media.getStatus().get(3, TimeUnit.SECONDS))
            assertNull(media.currentStatus)
            assertFutureFails(media.play(), IllegalStateException::class.java)
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `invalid JSON in a media response ends the session`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-bad", "transport-bad")
            val load = receiver.readUntil(nsMedia, "LOAD")!!
            receiver.sendMessage(
                receiverMessage("sender-0", nsMedia, """{"type":"MEDIA_STATUS","requestId":${requestIdOf(load.payloadUtf8!!)},""")
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            assertFutureFails(
                media.load(CastMediaItem("http://x/a.mp3", metadata = CastMediaMetadata("T"))),
                CastProtocolException::class.java
            )
            assertTrue(awaitCondition { session.state == CastSessionState.DISCONNECTED })
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `non-finite positions are rejected before sending`() {
        val receiver = FakeCastReceiver()
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            assertFutureFails(media.seek(Double.NaN), IllegalArgumentException::class.java)
            assertFutureFails(
                media.load(CastMediaItem("http://x/a.mp3", metadata = CastMediaMetadata("T")), currentTime = Double.POSITIVE_INFINITY),
                IllegalArgumentException::class.java
            )
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
    }

    @Test
    fun `media commands and broadcasts follow the session after a reconnection`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-r1", "transport-r1")
            // Stay silent so the heartbeat timeout opens a new channel.
            receiver.handshakeWithRunningApp("session-r2", "transport-r2")
            receiver.sendMessage(receiverMessage("sender-0", nsMedia, mediaStatus(0, 3, "PAUSED"), sourceId = "transport-r2"))
            val play = receiver.readUntil(nsMedia, "PLAY")!!
            assertEquals("transport-r2", play.destinationId)
            assertEquals(3L, mediaSessionIdOf(play.payloadUtf8!!))
            receiver.sendMessage(
                receiverMessage("sender-0", nsMedia, mediaStatus(requestIdOf(play.payloadUtf8!!), 3, "PLAYING"), sourceId = "transport-r2")
            )
        }
        val session = mediaTestSession(receiver, heartbeatIntervalMs = 100, heartbeatTimeoutMs = 800)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            assertTrue(awaitCondition(5000) { media.currentStatus?.mediaSessionId == 3L && session.state == CastSessionState.READY })
            assertEquals(CastPlayerState.PLAYING, media.play().get(3, TimeUnit.SECONDS).playerState)
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }
}
