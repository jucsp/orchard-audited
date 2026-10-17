/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * JVM tests for CastMediaChannel using an in-memory loopback receiver. No real
 * network access and no android.* imports.
 */
package dev.sfg.orchard.mobile.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val NS_MEDIA = CastMediaChannel.NS_MEDIA

class CastMediaChannelTest {

    @Test
    fun `load sends exact LOAD JSON with images`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-load", "transport-load")
            val load = receiver.readUntil(NS_MEDIA, "LOAD")!!
            val req = requestIdOf(load.payloadUtf8!!)
            assertEquals(
                """{"type":"LOAD","requestId":$req,"sessionId":"session-load","autoplay":true,"currentTime":0.0,"media":{"contentId":"http://example/audio.mp3","contentType":"audio/mp4","streamType":"BUFFERED","metadata":{"metadataType":3,"title":"Song","artist":"Artist","albumName":"Album","images":[{"url":"http://example/cover.jpg"}]}}}""",
                load.payloadUtf8
            )
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    NS_MEDIA,
                    mediaStatus(req, mediaSessionId = 42, playerState = "PLAYING", currentTime = 1.5, duration = 180.0)
                )
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            val status = media.load(
                CastMediaItem(
                    contentId = "http://example/audio.mp3",
                    contentType = "audio/mp4",
                    streamType = "BUFFERED",
                    metadata = CastMediaMetadata(
                        title = "Song",
                        artist = "Artist",
                        albumName = "Album",
                        imageUrl = "http://example/cover.jpg"
                    )
                ),
                autoplay = true,
                currentTime = 0.0
            ).get(3, TimeUnit.SECONDS)
            assertEquals(42L, status.mediaSessionId)
            assertEquals(CastPlayerState.PLAYING, status.playerState)
            assertEquals(1.5, status.currentTime, 0.001)
            assertEquals(180.0, status.duration ?: -1.0, 0.001)
            assertEquals(42L, media.currentStatus?.mediaSessionId ?: -1L)
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `load omits images when imageUrl is null`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-load2", "transport-load2")
            val load = receiver.readUntil(NS_MEDIA, "LOAD")!!
            val req = requestIdOf(load.payloadUtf8!!)
            assertFalse(load.payloadUtf8!!.contains("images"))
            assertEquals(
                """{"type":"LOAD","requestId":$req,"sessionId":"session-load2","autoplay":false,"currentTime":5.0,"media":{"contentId":"http://example/b.mp3","contentType":"audio/mp4","streamType":"BUFFERED","metadata":{"metadataType":3,"title":"B","artist":"","albumName":""}}}""",
                load.payloadUtf8
            )
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(req, mediaSessionId = 8, playerState = "BUFFERING")))
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            val status = media.load(
                CastMediaItem("http://example/b.mp3", metadata = CastMediaMetadata(title = "B")),
                autoplay = false,
                currentTime = 5.0
            ).get(3, TimeUnit.SECONDS)
            assertEquals(8L, status.mediaSessionId)
            assertEquals(CastPlayerState.BUFFERING, status.playerState)
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `PLAY PAUSE SEEK STOP include mediaSessionId and parse responses`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-cmd", "transport-cmd")
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 7, playerState = "PAUSED")))

            val play = receiver.readUntil(NS_MEDIA, "PLAY")!!
            assertEquals(7L, mediaSessionIdOf(play.payloadUtf8!!))
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, mediaStatus(requestIdOf(play.payloadUtf8!!), mediaSessionId = 7, playerState = "PLAYING"))
            )

            val pause = receiver.readUntil(NS_MEDIA, "PAUSE")!!
            assertEquals(7L, mediaSessionIdOf(pause.payloadUtf8!!))
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, mediaStatus(requestIdOf(pause.payloadUtf8!!), mediaSessionId = 7, playerState = "PAUSED"))
            )

            val seek = receiver.readUntil(NS_MEDIA, "SEEK")!!
            assertEquals(7L, mediaSessionIdOf(seek.payloadUtf8!!))
            assertTrue(seek.payloadUtf8!!.contains("\"currentTime\":12.5"))
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, mediaStatus(requestIdOf(seek.payloadUtf8!!), mediaSessionId = 7, playerState = "PLAYING", currentTime = 12.5))
            )

            val stop = receiver.readUntil(NS_MEDIA, "STOP")!!
            assertEquals(7L, mediaSessionIdOf(stop.payloadUtf8!!))
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, mediaStatus(requestIdOf(stop.payloadUtf8!!), mediaSessionId = 7, playerState = "IDLE", idleReason = "CANCELLED"))
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            assertTrue(awaitCondition { media.currentStatus?.mediaSessionId == 7L })

            assertEquals(CastPlayerState.PLAYING, media.play().get(3, TimeUnit.SECONDS).playerState)
            assertEquals(CastPlayerState.PAUSED, media.pause().get(3, TimeUnit.SECONDS).playerState)
            val seekStatus = media.seek(12.5).get(3, TimeUnit.SECONDS)
            assertEquals(12.5, seekStatus.currentTime, 0.001)
            assertEquals(CastPlayerState.IDLE, media.stop().get(3, TimeUnit.SECONDS).playerState)
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `MEDIA_STATUS broadcasts update state position and duration`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-parse", "transport-parse")
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 5, playerState = "IDLE", duration = 100.0)))
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 5, playerState = "BUFFERING", currentTime = 1.0, duration = 100.0)))
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 5, playerState = "PLAYING", currentTime = 2.5, duration = 100.0)))
            receiver.sendMessage(receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 5, playerState = "PAUSED", currentTime = 3.0, duration = 100.0)))
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        val observed = CopyOnWriteArrayList<CastMediaStatus>()
        media.listeners.add { observed.add(it) }
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition { observed.size >= 4 })
            assertEquals(
                listOf(CastPlayerState.IDLE, CastPlayerState.BUFFERING, CastPlayerState.PLAYING, CastPlayerState.PAUSED),
                observed.map { it.playerState }
            )
            assertEquals(3.0, media.currentStatus?.currentTime ?: -1.0, 0.001)
            assertEquals(100.0, media.currentStatus?.duration ?: -1.0, 0.001)
            assertEquals(5L, media.currentStatus?.mediaSessionId ?: -1L)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `MEDIA_STATUS with a single status object is parsed`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-obj", "transport-obj")
            receiver.sendMessage(
                receiverMessage(
                    "sender-0",
                    NS_MEDIA,
                    """{"type":"MEDIA_STATUS","requestId":0,"status":{"mediaSessionId":11,"playerState":"PAUSED","currentTime":4.0,"playbackRate":1.0}}"""
                )
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition { media.currentStatus?.mediaSessionId == 11L })
            assertEquals(CastPlayerState.PAUSED, media.currentStatus?.playerState)
            assertEquals(4.0, media.currentStatus?.currentTime ?: -1.0, 0.001)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `IDLE with FINISHED invokes onItemFinished`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-fin", "transport-fin")
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, mediaStatus(0, mediaSessionId = 9, playerState = "IDLE", idleReason = "FINISHED"))
            )
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        val finished = AtomicBoolean(false)
        media.onItemFinished = { finished.set(true) }
        try {
            session.connectAndLaunch()
            receiverThread.join(2000)
            assertTrue(awaitCondition { finished.get() })
            assertEquals(CastPlayerState.IDLE, media.currentStatus?.playerState)
            assertEquals(CastIdleReason.FINISHED, media.currentStatus?.idleReason)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `commands without active mediaSessionId fail with IllegalStateException`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-noid", "transport-noid")
            Thread.sleep(150)
        }
        val session = mediaTestSession(receiver)
        val media = CastMediaChannel(session)
        try {
            session.connectAndLaunch()
            receiverThread.join(500)
            assertFutureFails(media.play(), IllegalStateException::class.java)
            assertFutureFails(media.pause(), IllegalStateException::class.java)
            assertFutureFails(media.seek(1.0), IllegalStateException::class.java)
            assertFutureFails(media.stop(), IllegalStateException::class.java)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `LOAD_FAILED fails the future with CastProtocolException`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-lf", "transport-lf")
            val load = receiver.readUntil(NS_MEDIA, "LOAD")!!
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, """{"type":"LOAD_FAILED","requestId":${requestIdOf(load.payloadUtf8!!)}}""")
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
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }

    @Test
    fun `INVALID_REQUEST fails the future with CastProtocolException`() {
        val receiver = FakeCastReceiver()
        val receiverThread = runReceiverScript(receiver) {
            receiver.handshakeWithRunningApp("session-ir", "transport-ir")
            val load = receiver.readUntil(NS_MEDIA, "LOAD")!!
            receiver.sendMessage(
                receiverMessage("sender-0", NS_MEDIA, """{"type":"INVALID_REQUEST","requestId":${requestIdOf(load.payloadUtf8!!)}}""")
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
            receiverThread.join(2000)
        } finally {
            media.close()
            session.close()
            receiver.close()
        }
        receiver.error?.let { throw AssertionError("Receiver script failed", it) }
    }
}
