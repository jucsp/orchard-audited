// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.sfg.orchard.mobile.cast.CastDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Cast UI shows. [volume] is the receiver level, 0..1. */
data class CastStatus(
    val device: CastDevice? = null,
    val connecting: Boolean = false,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val error: String? = null,
    val pendingTrustPrompt: CastTrustPrompt? = null,
) {
    val connected: Boolean
        get() = device != null && !connecting
}

/** Trust conflict surfaced to the UI instead of a plain error string (CAST-ROT-INV-3). */
data class CastTrustPrompt(
    val device: CastDevice,
    val previousPinShort: String?,
    val newPinShort: String?,
    val isDeviceKey: Boolean,
    val reason: String? = null,
)

/**
 * The playback service side, called on the main thread. [chromecast] builds on first use, so
 * nothing cast-related exists until the user picks a device (CAST-INV-3).
 */
@OptIn(UnstableApi::class)
class CastHost(
    val chromecast: () -> ChromecastPlayback,
    val onCastStarted: (castPlayer: Player) -> Unit,
    val onCastEnded: (localPlayer: Player) -> Unit,
    val onCastError: (String) -> Unit,
)

/**
 * Bridge between the Cast UI and the playback service, held on the graph. Call from the main
 * thread. Commands are refused while no service is attached.
 */
@OptIn(UnstableApi::class)
class CastControls {
    private val mutableStatus = MutableStateFlow(CastStatus())
    val status: StateFlow<CastStatus> = mutableStatus.asStateFlow()

    private var host: CastHost? = null
    private var active: ChromecastPlayback? = null

    fun attach(host: CastHost) {
        this.host = host
    }

    fun detach() {
        host = null
        active = null
        mutableStatus.value = CastStatus()
    }

    fun connect(device: CastDevice) {
        val host = host ?: run {
            mutableStatus.value = CastStatus(error = "Playback is still starting. Try again in a moment")
            return
        }
        val playback = host.chromecast()
        if (playback.isConnecting || playback.isActive) return
        active = playback
        mutableStatus.value = CastStatus(device = device, connecting = true)
        playback.connectToDevice(
            device,
            onStarted = { castPlayer ->
                val volume = playback.getDeviceVolume()
                mutableStatus.value = CastStatus(
                    device = device,
                    volume = volume?.level ?: 1f,
                    muted = volume?.muted ?: false,
                )
                castPlayer.addListener(volumeListener(device, castPlayer))
                host.onCastStarted(castPlayer)
            },
            onEnded = { localPlayer ->
                mutableStatus.value = CastStatus()
                host.onCastEnded(localPlayer)
            },
            onError = { message ->
                mutableStatus.value = CastStatus(error = message)
                host.onCastError(message)
            },
            onTrustPrompt = { prompt ->
                mutableStatus.value = CastStatus(pendingTrustPrompt = prompt)
            },
        )
    }

    fun disconnect() {
        active?.disconnect(stopReceiver = true)
    }

    fun setDeviceVolume(level: Float) {
        val playback = active?.takeIf { it.isActive } ?: return
        val clamped = level.coerceIn(0f, 1f)
        playback.setDeviceVolume(clamped)
        mutableStatus.value = mutableStatus.value.copy(volume = clamped)
    }

    fun setDeviceMuted(muted: Boolean) {
        val playback = active?.takeIf { it.isActive } ?: return
        playback.setDeviceMuted(muted)
        mutableStatus.value = mutableStatus.value.copy(muted = muted)
    }

    fun clearError() {
        if (mutableStatus.value.error != null) mutableStatus.value = mutableStatus.value.copy(error = null)
    }

    /** User confirmed the pending trust prompt; re-connects to the same device. */
    fun confirmDeviceTrust() {
        val prompt = mutableStatus.value.pendingTrustPrompt ?: return
        active?.confirmDeviceTrust()
        mutableStatus.value = CastStatus()
        connect(prompt.device)
    }

    /** User cancelled the pending trust prompt; nothing is written to the store. */
    fun cancelDeviceTrust() {
        active?.cancelDeviceTrust()
        if (mutableStatus.value.pendingTrustPrompt != null) {
            mutableStatus.value = mutableStatus.value.copy(pendingTrustPrompt = null)
        }
    }

    /**
     * Drops any pending prompt for the device and the pins held by the active playback. The UI
     * also clears the persistent store, so this works before any connection in this process.
     */
    fun forgetDevice(deviceId: String) {
        active?.forgetDeviceTrust(deviceId)
        if (mutableStatus.value.pendingTrustPrompt?.device?.id == deviceId) {
            mutableStatus.value = mutableStatus.value.copy(pendingTrustPrompt = null)
        }
    }

    /** Receiver volume changes from the device buttons, other senders or the notification. */
    private fun volumeListener(device: CastDevice, castPlayer: Player) = object : Player.Listener {
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            val current = mutableStatus.value
            if (current.device?.id != device.id || current.connecting) return
            val max = castPlayer.deviceInfo.maxVolume.coerceAtLeast(1)
            mutableStatus.value = current.copy(volume = volume.coerceIn(0, max) / max.toFloat(), muted = muted)
        }
    }
}
