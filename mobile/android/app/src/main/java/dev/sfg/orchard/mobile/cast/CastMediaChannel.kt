/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * CastV2 media control channel. Sends LOAD/PLAY/PAUSE/SEEK/STOP commands on
 * urn:x-cast:com.google.cast.media and parses MEDIA_STATUS (both correlated
 * responses and unsolicited broadcasts) so the phone can advance the queue
 * when a track finishes. Pure Kotlin/JVM, no android.* imports.
 */
package dev.sfg.orchard.mobile.cast

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

enum class CastPlayerState {
    IDLE, BUFFERING, PLAYING, PAUSED, UNKNOWN;

    companion object {
        fun fromWire(value: String?): CastPlayerState = when (value) {
            "IDLE" -> IDLE
            "BUFFERING" -> BUFFERING
            "PLAYING" -> PLAYING
            "PAUSED" -> PAUSED
            else -> UNKNOWN
        }
    }
}

enum class CastIdleReason {
    FINISHED, CANCELLED, INTERRUPTED, ERROR, NONE;

    companion object {
        fun fromWire(value: String?): CastIdleReason = when (value) {
            "FINISHED" -> FINISHED
            "CANCELLED" -> CANCELLED
            "INTERRUPTED" -> INTERRUPTED
            "ERROR" -> ERROR
            else -> NONE
        }
    }
}

data class CastMediaMetadata(
    val title: String,
    val artist: String = "",
    val albumName: String = "",
    val imageUrl: String? = null
)

data class CastMediaItem(
    val contentId: String,
    val contentType: String = "audio/mp4",
    val streamType: String = "BUFFERED",
    val metadata: CastMediaMetadata
)

data class CastMediaStatus(
    val mediaSessionId: Long,
    val playerState: CastPlayerState,
    val idleReason: CastIdleReason,
    val currentTime: Double,
    val duration: Double?,
    val playbackRate: Double = 1.0
)

/**
 * Media sub-channel for a READY [CastSession]. Commands go to the application
 * `transportId` on the session's active channel, so the channel keeps working
 * after a heartbeat reconnection. PLAY/PAUSE/SEEK/STOP without an active
 * `mediaSessionId` fail with [IllegalStateException].
 *
 * [listeners] and [onItemFinished] run on the channel reader thread and must not
 * block on a command future; [onItemFinished] fires once per `mediaSessionId`.
 */
class CastMediaChannel(val session: CastSession) : AutoCloseable {

    companion object {
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
    }

    /** Last MEDIA_STATUS, or null when the receiver reports no media session. */
    @Volatile
    var currentStatus: CastMediaStatus? = null
        private set

    val listeners = CopyOnWriteArrayList<(CastMediaStatus) -> Unit>()

    @Volatile
    var onItemFinished: (() -> Unit)? = null

    @Volatile
    private var finishedMediaSessionId = 0L

    private val messageListener: (CastMessage) -> Unit = ::onMessage

    init {
        session.addMessageListener(messageListener)
    }

    override fun close() {
        session.removeMessageListener(messageListener)
        listeners.clear()
        onItemFinished = null
    }

    /**
     * LOADs [item] into the Default Media Receiver. The returned future resolves
     * with the resulting MEDIA_STATUS and fails with [CastProtocolException] on
     * LOAD_FAILED / INVALID_REQUEST / LOAD_CANCELLED.
     */
    fun load(
        item: CastMediaItem,
        autoplay: Boolean = true,
        currentTime: Double = 0.0
    ): CompletableFuture<CastMediaStatus> {
        if (!currentTime.isFinite()) return failedFuture(IllegalArgumentException("currentTime must be finite"))
        if (session.state != CastSessionState.READY) {
            return failedFuture(IllegalStateException("CastSession is not READY"))
        }
        val sessionId = session.sessionId
            ?: return failedFuture(IllegalStateException("No active sessionId"))
        return requireStatus(command(buildLoadPayload(item, autoplay, currentTime, sessionId)))
    }

    fun play(): CompletableFuture<CastMediaStatus> =
        withMediaSessionId { """{"type":"PLAY","mediaSessionId":$it}""" }

    fun pause(): CompletableFuture<CastMediaStatus> =
        withMediaSessionId { """{"type":"PAUSE","mediaSessionId":$it}""" }

    fun seek(currentTime: Double): CompletableFuture<CastMediaStatus> {
        if (!currentTime.isFinite()) return failedFuture(IllegalArgumentException("currentTime must be finite"))
        return withMediaSessionId { """{"type":"SEEK","mediaSessionId":$it,"currentTime":$currentTime}""" }
    }

    fun stop(): CompletableFuture<CastMediaStatus> =
        withMediaSessionId { """{"type":"STOP","mediaSessionId":$it}""" }

    /**
     * GET_STATUS includes the active mediaSessionId only when one exists. Resolves
     * with null when the receiver has no media session.
     */
    fun getStatus(): CompletableFuture<CastMediaStatus?> {
        val mediaSessionId = currentStatus?.mediaSessionId ?: 0L
        val payload = if (mediaSessionId > 0L) {
            """{"type":"GET_STATUS","mediaSessionId":$mediaSessionId}"""
        } else {
            """{"type":"GET_STATUS"}"""
        }
        return command(payload)
    }

    private fun withMediaSessionId(payload: (Long) -> String): CompletableFuture<CastMediaStatus> {
        val mediaSessionId = currentStatus?.mediaSessionId ?: 0L
        if (mediaSessionId <= 0L) {
            return failedFuture(IllegalStateException("No active mediaSessionId"))
        }
        return requireStatus(command(payload(mediaSessionId)))
    }

    private fun command(payloadJson: String): CompletableFuture<CastMediaStatus?> {
        if (session.state != CastSessionState.READY) {
            return failedFuture(IllegalStateException("CastSession is not READY"))
        }
        val destination = session.transportId
            ?: return failedFuture(IllegalStateException("No active transportId"))
        val result = CompletableFuture<CastMediaStatus?>()
        session.sendRequest(NS_MEDIA, destination, payloadJson).whenComplete { message, error ->
            if (error != null) {
                result.completeExceptionally(error)
                return@whenComplete
            }
            try {
                result.complete(handleMediaResponse(message))
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    private fun requireStatus(future: CompletableFuture<CastMediaStatus?>): CompletableFuture<CastMediaStatus> {
        val result = CompletableFuture<CastMediaStatus>()
        future.whenComplete { status, error ->
            when {
                error != null -> result.completeExceptionally(error)
                status == null -> result.completeExceptionally(CastProtocolException("MEDIA_STATUS without an active media session"))
                else -> result.complete(status)
            }
        }
        return result
    }

    private fun onMessage(message: CastMessage) {
        if (message.namespace != NS_MEDIA) return
        // CastSession only forwards payloads that already parsed as JSON.
        val obj = try {
            Json.parse(message.payloadUtf8 ?: return).objOrNull()
        } catch (_: IllegalArgumentException) {
            null
        } ?: return
        if (obj.field("type")?.asString() == "MEDIA_STATUS") applyMediaStatus(obj)
    }

    private fun handleMediaResponse(message: CastMessage): CastMediaStatus? {
        val payload = message.payloadUtf8 ?: throw CastProtocolException("Empty media response")
        val root = try {
            Json.parse(payload)
        } catch (e: IllegalArgumentException) {
            // CAST-INV-8: invalid JSON ends the session.
            session.abort()
            throw CastProtocolException("Invalid media JSON: ${e.message}")
        }
        val obj = root.objOrNull() ?: throw CastProtocolException("Media response is not a JSON object")
        return when (val type = obj.field("type")?.asString() ?: "") {
            "MEDIA_STATUS" -> applyMediaStatus(obj)
            "LOAD_FAILED", "INVALID_REQUEST", "LOAD_CANCELLED" -> throw CastProtocolException(type)
            else -> throw CastProtocolException("Expected MEDIA_STATUS, got $type")
        }
    }

    /**
     * Applies a MEDIA_STATUS (`status` as array or single object). An empty status
     * means the media session is gone: [currentStatus] is cleared and null returned.
     */
    private fun applyMediaStatus(obj: Json.Obj): CastMediaStatus? {
        val entry = when (val field = obj.field("status")) {
            is Json.Arr -> field.items.firstOrNull()?.objOrNull()
            is Json.Obj -> field
            else -> null
        }
        if (entry == null) {
            currentStatus = null
            return null
        }
        val mediaSessionId = entry.field("mediaSessionId")?.asDouble()?.toLong() ?: 0L
        // Receivers include `media` only when it changes; keep the known duration.
        val duration = (entry.field("media")?.field("duration") as? Json.Num)?.value
            ?: currentStatus?.takeIf { it.mediaSessionId == mediaSessionId }?.duration
        val status = CastMediaStatus(
            mediaSessionId = mediaSessionId,
            playerState = CastPlayerState.fromWire(entry.field("playerState")?.asString()),
            idleReason = CastIdleReason.fromWire(entry.field("idleReason")?.asString()),
            currentTime = entry.field("currentTime")?.asDouble() ?: 0.0,
            duration = duration,
            playbackRate = (entry.field("playbackRate") as? Json.Num)?.value ?: 1.0
        )
        currentStatus = status
        for (listener in listeners) {
            try {
                listener(status)
            } catch (_: Exception) {
            }
        }
        if (status.playerState == CastPlayerState.IDLE &&
            status.idleReason == CastIdleReason.FINISHED &&
            finishedMediaSessionId != mediaSessionId
        ) {
            finishedMediaSessionId = mediaSessionId
            try {
                onItemFinished?.invoke()
            } catch (_: Exception) {
            }
        }
        return status
    }

    private fun buildLoadPayload(
        item: CastMediaItem,
        autoplay: Boolean,
        currentTime: Double,
        sessionId: String
    ): String {
        val images = item.metadata.imageUrl?.let { url ->
            ""","images":[{"url":${jsonString(url)}}]"""
        } ?: ""
        val media = "{" +
            "\"contentId\":${jsonString(item.contentId)}," +
            "\"contentType\":${jsonString(item.contentType)}," +
            "\"streamType\":${jsonString(item.streamType)}," +
            "\"metadata\":{" +
            "\"metadataType\":3," +
            "\"title\":${jsonString(item.metadata.title)}," +
            "\"artist\":${jsonString(item.metadata.artist)}," +
            "\"albumName\":${jsonString(item.metadata.albumName)}" +
            images +
            "}}"
        // requestId 0 is a placeholder replaced by CastChannel.sendRequest.
        return "{" +
            "\"type\":\"LOAD\"," +
            "\"requestId\":0," +
            "\"sessionId\":${jsonString(sessionId)}," +
            "\"autoplay\":$autoplay," +
            "\"currentTime\":$currentTime," +
            "\"media\":$media}"
    }

    private fun jsonString(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun <T> failedFuture(cause: Throwable): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        future.completeExceptionally(cause)
        return future
    }
}
