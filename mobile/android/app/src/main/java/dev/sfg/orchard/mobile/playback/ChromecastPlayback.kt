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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.sfg.orchard.mobile.cast.CastChannel
import dev.sfg.orchard.mobile.cast.CastDevice
import dev.sfg.orchard.mobile.cast.CastDeviceVolume
import dev.sfg.orchard.mobile.cast.CastMediaChannel
import dev.sfg.orchard.mobile.cast.CastSecurityException
import dev.sfg.orchard.mobile.cast.CastSession
import dev.sfg.orchard.mobile.cast.CastSessionState
import dev.sfg.orchard.mobile.cast.CastTrustStore
import dev.sfg.orchard.mobile.cast.CastV2Player
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import okhttp3.OkHttpClient

/**
 * Owns the local/receiver handoff while keeping one authoritative Media3 queue on the phone.
 *
 * Call from the main thread. TLS, LAUNCH and teardown run on a private thread because they block
 * on the socket; the connect callbacks are delivered on the main thread. Nothing touches the
 * network until [connectToDevice] (CAST-INV-3).
 */
@UnstableApi
class ChromecastPlayback(
    context: Context,
    private val client: OkHttpClient,
    private val resolver: YouTubeStreamResolver,
    private val trustStore: CastTrustStore,
    private val localPlayer: () -> Player,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "orchard-cast-connect").apply { isDaemon = true }
    }

    private class Connection(
        val streamServer: ChromecastStreamServer,
        val channel: CastChannel,
        val session: CastSession,
        val mediaChannel: CastMediaChannel,
        val player: CastV2Player,
        val phoneState: TransferState,
        val onEnded: (Player) -> Unit,
        val sessionListener: (CastSessionState) -> Unit,
    )

    private var connection: Connection? = null
    private var closed = false

    var isConnecting: Boolean = false
        private set

    val isActive: Boolean
        get() = connection != null

    fun authoritativePlayer(): Player = connection?.player ?: localPlayer()

    fun connectToDevice(
        device: CastDevice,
        onStarted: (Player) -> Unit,
        onEnded: (Player) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (closed || isConnecting || connection != null) {
            onError("Disconnect the current Cast device first")
            return
        }
        isConnecting = true
        io.execute {
            val streamServer =
                try {
                    ChromecastStreamServer(appContext, client, resolver, receiverIp = device.host)
                } catch (error: Exception) {
                    failConnect(error, onError)
                    return@execute
                }
            val channel = CastChannel(device.host, device.port, device.id, trustStore)
            val session = CastSession(channel)
            try {
                session.connectAndLaunch()
            } catch (error: Exception) {
                teardown(null, session, channel, streamServer, stopReceiver = false)
                failConnect(error, onError)
                return@execute
            }
            val mediaChannel = CastMediaChannel(session)
            main.post {
                start(streamServer, channel, session, mediaChannel, onStarted, onEnded, onError)
            }
        }
    }

    private fun start(
        streamServer: ChromecastStreamServer,
        channel: CastChannel,
        session: CastSession,
        mediaChannel: CastMediaChannel,
        onStarted: (Player) -> Unit,
        onEnded: (Player) -> Unit,
        onError: (String) -> Unit,
    ) {
        isConnecting = false
        if (closed) {
            io.execute { teardown(mediaChannel, session, channel, streamServer, stopReceiver = false) }
            return
        }
        val castPlayer = CastV2Player(Looper.getMainLooper(), session, mediaChannel, streamServer)
        val local = localPlayer()
        val state = TransferState.capture(local)
        val sessionListener: (CastSessionState) -> Unit = { next ->
            if (next == CastSessionState.DISCONNECTED) {
                main.post { if (connection?.session === session) disconnect(stopReceiver = false) }
            }
        }
        session.addStateListener(sessionListener)
        connection =
            Connection(streamServer, channel, session, mediaChannel, castPlayer, state, onEnded, sessionListener)
        runCatching {
                local.pause()
                castPlayer.repeatMode = state.repeatMode
                if (state.items.isNotEmpty()) {
                    castPlayer.setMediaItems(state.items, state.index, state.positionMs)
                }
                castPlayer.playWhenReady = state.playWhenReady
                onStarted(castPlayer)
                if (state.items.isNotEmpty()) castPlayer.prepare()
            }
            .onFailure { error ->
                Log.w(TAG, "Could not transfer playback to Cast: ${error.javaClass.simpleName}")
                connection = null
                session.removeStateListener(sessionListener)
                castPlayer.release()
                io.execute { teardown(mediaChannel, session, channel, streamServer, stopReceiver = false) }
                local.playWhenReady = state.playWhenReady
                onError("Could not start Chromecast playback")
                return
            }
        // The receiver may have dropped the session before the listener was attached.
        if (session.state == CastSessionState.DISCONNECTED) disconnect(stopReceiver = false)
    }

    /**
     * Returns playback to the phone at the receiver's position. [stopReceiver] sends media STOP
     * first; without it the session only gets CLOSE, as when the device itself went away.
     */
    fun disconnect(stopReceiver: Boolean = true) {
        val conn = connection ?: return
        connection = null
        conn.session.removeStateListener(conn.sessionListener)
        val remote = TransferState.capture(conn.player)
        val state =
            if (remote.items.isEmpty()) conn.phoneState
            else remote.copy(shuffle = conn.phoneState.shuffle, playlistMetadata = conn.phoneState.playlistMetadata)
        conn.player.release()
        io.execute {
            teardown(conn.mediaChannel, conn.session, conn.channel, conn.streamServer, stopReceiver)
        }
        val local = localPlayer()
        runCatching {
                local.stop()
                if (state.items.isEmpty()) {
                    local.clearMediaItems()
                } else {
                    local.setMediaItems(state.items, state.index, state.positionMs)
                }
                local.setPlaylistMetadata(state.playlistMetadata)
                local.repeatMode = state.repeatMode
                local.shuffleModeEnabled = state.shuffle
                if (state.items.isNotEmpty()) local.prepare()
                conn.onEnded(local)
                local.playWhenReady = state.playWhenReady
            }
            .onFailure { error ->
                Log.w(TAG, "Could not return Cast playback to phone: ${error.javaClass.simpleName}")
                conn.onEnded(local)
            }
    }

    /** Receiver volume, 0..1. This is the level that matters on a Nest or Home speaker. */
    fun setDeviceVolume(level: Float) {
        val session = connection?.session ?: return
        io.execute { session.setDeviceVolume(level) }
    }

    fun setDeviceMuted(muted: Boolean) {
        val session = connection?.session ?: return
        io.execute { session.setDeviceMuted(muted) }
    }

    fun getDeviceVolume(): CastDeviceVolume? = connection?.session?.receiverVolume

    override fun close() {
        if (closed) return
        closed = true
        connection?.let { conn ->
            connection = null
            conn.session.removeStateListener(conn.sessionListener)
            conn.player.release()
            io.execute {
                teardown(conn.mediaChannel, conn.session, conn.channel, conn.streamServer, stopReceiver = true)
            }
        }
        // Queued teardowns still run after shutdown().
        io.shutdown()
    }

    private fun failConnect(error: Exception, onError: (String) -> Unit) {
        // CAST-INV-6: exception messages can carry the device id, so only the type is logged.
        Log.w(TAG, "Could not connect to Cast device: ${error.javaClass.simpleName}")
        main.post {
            isConnecting = false
            onError(messageFor(error))
        }
    }

    private fun teardown(
        mediaChannel: CastMediaChannel?,
        session: CastSession,
        channel: CastChannel,
        streamServer: ChromecastStreamServer,
        stopReceiver: Boolean,
    ) {
        if (stopReceiver && mediaChannel != null && mediaChannel.currentStatus != null) {
            runCatching { mediaChannel.stop().get(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        }
        runCatching { mediaChannel?.close() }
        runCatching { session.close() }
        runCatching { channel.close() }
        runCatching { streamServer.close() }
    }

    private fun messageFor(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val security = causes.firstOrNull { it is CastSecurityException }
        return when {
            security?.message?.contains("mismatch") == true ->
                "This Cast device presented a different certificate. Connection aborted"
            security != null -> "This Cast device is not on a private local network"
            causes.any { it is TimeoutException } -> "The Cast device did not respond"
            else -> "Could not connect to the Cast device"
        }
    }

    private data class TransferState(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int,
        val shuffle: Boolean,
        val playlistMetadata: MediaMetadata,
    ) {
        companion object {
            fun capture(player: Player): TransferState {
                val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                return TransferState(
                    items = items,
                    index =
                        if (items.isEmpty()) 0
                        else {
                            player.currentMediaItemIndex
                                .takeUnless { it == C.INDEX_UNSET }
                                ?.coerceIn(items.indices) ?: 0
                        },
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    playWhenReady = player.playWhenReady,
                    repeatMode = player.repeatMode,
                    shuffle = player.shuffleModeEnabled,
                    playlistMetadata = player.playlistMetadata,
                )
            }
        }
    }

    private companion object {
        const val TAG = "OrchardCast"
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
