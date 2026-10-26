/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SFG545
 *
 * Media3 Player backed by the sovereign CastV2 stack. The phone keeps the
 * authoritative queue; the receiver only ever holds the current item.
 */
package dev.sfg.orchard.mobile.cast

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.sfg.orchard.mobile.playback.ChromecastStreamServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.roundToInt

/**
 * State lives on [looper]. Every network command runs on a private single thread
 * because [CastChannel] writes to the socket on the calling thread. Results and
 * receiver events are posted back to [looper] before [invalidateState].
 */
@UnstableApi
class CastV2Player(
    looper: Looper,
    private val session: CastSession,
    private val mediaChannel: CastMediaChannel,
    private val streamServer: ChromecastStreamServer,
) : SimpleBasePlayer(looper) {

    private class Entry(val uid: Any, val item: MediaItem)

    private val handler = Handler(looper)
    private val commands: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cast-v2-player").apply { isDaemon = true }
    }

    private var entries: List<Entry> = emptyList()
    private var currentIndex = 0
    private var playWhenReady = false
    private var prepared = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var error: PlaybackException? = null
    private var released = false

    // Each LOAD bumps the generation so late results for a replaced item are ignored.
    private var loadGeneration = 0
    private var loadInFlight = false
    private var activeMediaSessionId = 0L
    private var loadedAppSessionId: String? = null
    private var lastStatus: CastMediaStatus? = null
    private var lastStatusAtMs = 0L
    private var positionMs = 0L

    private val statusListener: (CastMediaStatus) -> Unit = { status ->
        val receivedAt = SystemClock.elapsedRealtime()
        post { onStatus(status, receivedAt) }
    }
    private val stateListener: (CastSessionState) -> Unit = { state -> post { onSessionState(state) } }
    private val receiverListener: (CastMessage) -> Unit = { message ->
        if (message.namespace == CastSession.NS_RECEIVER) post(::invalidateState)
    }

    init {
        mediaChannel.listeners.add(statusListener)
        mediaChannel.onItemFinished = { post(::advanceAfterFinish) }
        session.addStateListener(stateListener)
        session.addMessageListener(receiverListener)
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(repeatMode)
            .setDeviceInfo(DEVICE_INFO)
        session.receiverVolume?.let { volume ->
            builder.setDeviceVolume((volume.level * MAX_DEVICE_VOLUME).roundToInt())
                .setIsDeviceMuted(volume.muted)
        }
        if (entries.isEmpty()) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }
        val status = lastStatus?.takeIf {
            activeMediaSessionId > 0L && it.mediaSessionId == activeMediaSessionId && !loadInFlight
        }
        val durationUs = status?.duration?.takeIf { it > 0 }?.let { (it * 1_000_000).toLong() } ?: C.TIME_UNSET
        val playlist = entries.mapIndexed { index, entry ->
            MediaItemData.Builder(entry.uid)
                .setMediaItem(entry.item)
                .setIsSeekable(true)
                .setDurationUs(if (index == currentIndex) durationUs else C.TIME_UNSET)
                .build()
        }
        val sessionGone = session.state == CastSessionState.DISCONNECTED ||
            session.state == CastSessionState.CLOSING
        val playerError = error ?: if (prepared && sessionGone) SESSION_ENDED else null
        val playbackState = when {
            playerError != null || !prepared -> Player.STATE_IDLE
            loadInFlight || status == null -> Player.STATE_BUFFERING
            else -> stateFor(status)
        }
        builder.setPlaylist(playlist)
            .setCurrentMediaItemIndex(currentIndex)
            .setPlayerError(playerError)
            .setPlaybackState(playbackState)
            .setIsLoading(playbackState == Player.STATE_BUFFERING)
        when {
            status == null || playbackState == Player.STATE_IDLE -> builder.setContentPositionMs(positionMs)
            status.playerState == CastPlayerState.PLAYING && playbackState == Player.STATE_READY ->
                builder.setContentPositionMs(
                    PositionSupplier.getExtrapolating(currentPositionMs(status), status.playbackRate.toFloat())
                )
            else -> builder.setContentPositionMs(currentPositionMs(status))
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (prepared && entries.isNotEmpty()) {
            when {
                activeMediaSessionId > 0L -> send { if (playWhenReady) mediaChannel.play() else mediaChannel.pause() }
                !loadInFlight -> loadCurrent(positionMs)
            }
        }
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        error = null
        if (entries.isNotEmpty()) loadCurrent(positionMs)
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        positionMs = currentPosition.coerceAtLeast(0)
        prepared = false
        loadGeneration++
        loadInFlight = false
        if (activeMediaSessionId > 0L) send { mediaChannel.stop() }
        activeMediaSessionId = 0L
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        handler.removeCallbacksAndMessages(null)
        mediaChannel.listeners.remove(statusListener)
        mediaChannel.onItemFinished = null
        session.removeStateListener(stateListener)
        session.removeMessageListener(receiverListener)
        // Commands already queued still run; the owner closes the session afterwards.
        commands.shutdown()
        return done()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val keepPosition = startIndex == C.INDEX_UNSET
        entries = mediaItems.map { Entry(Any(), it) }
        if (entries.isEmpty()) {
            currentIndex = 0
            positionMs = 0
            loadGeneration++
            loadInFlight = false
            if (activeMediaSessionId > 0L) send { mediaChannel.stop() }
            activeMediaSessionId = 0L
            return done()
        }
        if (keepPosition) {
            currentIndex = currentIndex.coerceIn(entries.indices)
        } else {
            currentIndex = startIndex.coerceIn(entries.indices)
            positionMs = startPositionMs.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
        }
        if (prepared) loadCurrent(positionMs)
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val wasEmpty = entries.isEmpty()
        val at = index.coerceIn(0, entries.size)
        entries = entries.toMutableList().apply { addAll(at, mediaItems.map { Entry(Any(), it) }) }
        if (wasEmpty) {
            currentIndex = 0
            positionMs = 0
            if (prepared) loadCurrent(0)
        } else if (at <= currentIndex) {
            currentIndex += mediaItems.size
        }
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removesCurrent = currentIndex in fromIndex until toIndex
        entries = entries.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        when {
            entries.isEmpty() -> return handleSetMediaItems(mutableListOf(), 0, 0)
            removesCurrent -> {
                currentIndex = fromIndex.coerceAtMost(entries.lastIndex)
                positionMs = 0
                if (prepared) loadCurrent(0)
            }
            currentIndex >= toIndex -> currentIndex -= toIndex - fromIndex
        }
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val current = entries[currentIndex]
        val list = entries.toMutableList()
        val moved = list.subList(fromIndex, toIndex).toList()
        list.subList(fromIndex, toIndex).clear()
        list.addAll(newIndex.coerceIn(0, list.size), moved)
        entries = list
        currentIndex = list.indexOf(current)
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (entries.isEmpty() || mediaItemIndex == C.INDEX_UNSET) return done()
        val target = mediaItemIndex.coerceIn(entries.indices)
        val targetMs = positionMs.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
        this.positionMs = targetMs
        if (target != currentIndex) {
            currentIndex = target
            if (prepared) loadCurrent(targetMs)
        } else if (prepared && activeMediaSessionId > 0L && !loadInFlight) {
            send { mediaChannel.seek(targetMs / 1000.0) }
        } else if (prepared) {
            loadCurrent(targetMs)
        }
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return done()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        val level = deviceVolume.coerceIn(0, MAX_DEVICE_VOLUME) / MAX_DEVICE_VOLUME.toFloat()
        return onCommandThread { session.setDeviceVolume(level) }
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(deviceVolume + 1, flags)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(deviceVolume - 1, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        return onCommandThread { session.setDeviceMuted(muted) }
    }

    private fun onStatus(status: CastMediaStatus, receivedAtMs: Long) {
        lastStatus = status
        lastStatusAtMs = receivedAtMs
        if (status.mediaSessionId == activeMediaSessionId && !loadInFlight) {
            positionMs = currentPositionMs(status)
            // Another sender or the device's own controls can toggle playback.
            when (status.playerState) {
                CastPlayerState.PLAYING -> playWhenReady = true
                CastPlayerState.PAUSED -> playWhenReady = false
                else -> Unit
            }
            if (status.playerState == CastPlayerState.IDLE && status.idleReason == CastIdleReason.ERROR) {
                error = PlaybackException("Cast receiver could not play this track", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
            }
        }
        invalidateState()
    }

    private fun onSessionState(state: CastSessionState) {
        if (state == CastSessionState.DISCONNECTED || state == CastSessionState.CLOSING) {
            // Freeze the extrapolated position so the handoff resumes where the receiver stopped.
            lastStatus?.takeIf { activeMediaSessionId > 0L && it.mediaSessionId == activeMediaSessionId && !loadInFlight }
                ?.let { positionMs = currentPositionMs(it) }
        } else if (state == CastSessionState.READY && prepared && !loadInFlight &&
            loadedAppSessionId != null && session.sessionId != loadedAppSessionId
        ) {
            // A reconnection relaunched the receiver app, so the old media session is gone.
            positionMs = currentPosition.coerceAtLeast(0)
            loadCurrent(positionMs)
        }
        invalidateState()
    }

    private fun stateFor(status: CastMediaStatus): Int = when (status.playerState) {
        CastPlayerState.PLAYING, CastPlayerState.PAUSED -> Player.STATE_READY
        CastPlayerState.BUFFERING, CastPlayerState.UNKNOWN -> Player.STATE_BUFFERING
        CastPlayerState.IDLE -> when {
            status.idleReason != CastIdleReason.FINISHED -> Player.STATE_READY
            // The next LOAD is already posted; avoid a transient ENDED.
            nextIndexAfterFinish() != null -> Player.STATE_BUFFERING
            else -> Player.STATE_ENDED
        }
    }

    private fun nextIndexAfterFinish(): Int? = when {
        repeatMode == Player.REPEAT_MODE_ONE -> currentIndex
        currentIndex < entries.lastIndex -> currentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL && entries.isNotEmpty() -> 0
        else -> null
    }

    private fun advanceAfterFinish() {
        val finished = mediaChannel.currentStatus ?: lastStatus
        if (!prepared || loadInFlight || activeMediaSessionId <= 0L) return
        if (finished?.mediaSessionId != activeMediaSessionId) return
        val next = nextIndexAfterFinish()
        if (next == null) {
            invalidateState()
            return
        }
        currentIndex = next
        positionMs = 0
        loadCurrent(0)
        invalidateState()
    }

    private fun loadCurrent(startMs: Long) {
        val entry = entries.getOrNull(currentIndex) ?: return
        val generation = ++loadGeneration
        loadInFlight = true
        activeMediaSessionId = 0L
        error = null
        val castItem = try {
            toCastItem(entry.item)
        } catch (e: IllegalArgumentException) {
            failLoad(generation, e)
            return
        } catch (e: IllegalStateException) {
            failLoad(generation, e)
            return
        }
        val autoplay = playWhenReady
        loadedAppSessionId = session.sessionId
        send(
            command = { mediaChannel.load(castItem, autoplay, startMs / 1000.0) },
            onResult = { status, failure ->
                if (generation != loadGeneration) return@send
                loadInFlight = false
                if (failure != null || status == null) {
                    error = PlaybackException(
                        "Cast receiver rejected the track",
                        failure,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                } else {
                    activeMediaSessionId = status.mediaSessionId
                    lastStatus = status
                    lastStatusAtMs = SystemClock.elapsedRealtime()
                    // Play/pause pressed while the LOAD was in flight.
                    if (playWhenReady != autoplay) {
                        send { if (playWhenReady) mediaChannel.play() else mediaChannel.pause() }
                    }
                }
            },
        )
    }

    private fun failLoad(generation: Int, cause: Exception) {
        if (generation != loadGeneration) return
        loadInFlight = false
        error = PlaybackException(cause.message, cause, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
    }

    private fun toCastItem(item: MediaItem): CastMediaItem {
        val metadata = item.mediaMetadata
        val artwork = metadata.artworkUri?.toString()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        return CastMediaItem(
            contentId = streamServer.urlFor(item.mediaId),
            // The stream response carries the exact type; this is the receiver's up-front hint.
            contentType = item.localConfiguration?.mimeType ?: DEFAULT_AUDIO_MIME_TYPE,
            metadata = CastMediaMetadata(
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                albumName = metadata.albumTitle?.toString().orEmpty(),
                imageUrl = artwork,
            ),
        )
    }

    private fun currentPositionMs(status: CastMediaStatus): Long {
        val base = (status.currentTime * 1000).toLong()
        if (status.playerState != CastPlayerState.PLAYING) return base.coerceAtLeast(0)
        val elapsed = SystemClock.elapsedRealtime() - lastStatusAtMs
        return (base + elapsed * status.playbackRate).toLong().coerceAtLeast(0)
    }

    private fun send(command: () -> CompletableFuture<CastMediaStatus>) = send(command, null)
    private fun send(
        command: () -> CompletableFuture<CastMediaStatus>,
        onResult: ((CastMediaStatus?, Throwable?) -> Unit)? = null,
    ) {
        commands.executeSafely {
            val future = try {
                command()
            } catch (e: Exception) {
                CompletableFuture<CastMediaStatus>().apply { completeExceptionally(e) }
            }
            future.whenComplete { status, failure ->
                post {
                    onResult?.invoke(status, failure)
                    invalidateState()
                }
            }
        }
    }

    /** Completes once [block] ran, so Media3 reads the updated receiver volume. */
    private fun onCommandThread(block: () -> Unit): ListenableFuture<*> {
        val future = SettableFuture.create<Unit>()
        try {
            commands.execute {
                try {
                    block()
                } finally {
                    future.set(Unit)
                }
            }
        } catch (_: RejectedExecutionException) {
            future.set(Unit)
        }
        return future
    }

    private fun ExecutorService.executeSafely(block: () -> Unit) {
        try {
            execute(block)
        } catch (_: RejectedExecutionException) {
            // Released: the session is being torn down.
        }
    }

    private fun post(block: () -> Unit) {
        handler.post { if (!released) block() }
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val MAX_DEVICE_VOLUME = 20
        const val DEFAULT_AUDIO_MIME_TYPE = "audio/webm"
        val DEVICE_INFO: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(MAX_DEVICE_VOLUME)
            .build()

        val SESSION_ENDED = PlaybackException(
            "Cast session ended",
            null,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
        )

        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
