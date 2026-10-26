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

package dev.sfg.orchard.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.OrchardGraph
import dev.sfg.orchard.mobile.cast.CastDevice
import dev.sfg.orchard.mobile.cast.CastDiscovery
import dev.sfg.orchard.mobile.cast.CastTrustStore
import dev.sfg.orchard.mobile.cast.SharedPreferencesCastTrustStore
import dev.sfg.orchard.mobile.playback.CastControls
import dev.sfg.orchard.mobile.playback.CastStatus
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Local-network discovery owned by one picker. Nothing touches the network until [search], and
 * leaving the screen stops it (CAST-INV-3).
 */
@Stable
private class CastPicker(private val discovery: CastDiscovery, val trustStore: CastTrustStore) {
    var devices by mutableStateOf<List<CastDevice>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Ids trusted only through a TLS pin after a failed `deviceauth` (SDD §6.2 step 5). */
    var unauthenticated by mutableStateOf<Set<String>>(emptySet())
        private set
    var trustRevision by mutableStateOf(0)
        private set

    suspend fun refreshTrust(ids: List<String>) {
        unauthenticated = withContext(Dispatchers.IO) {
            ids.filterTo(HashSet()) { trustStore.getDeviceKeyPin(it) == null && trustStore.getLegacyPin(it) != null }
        }
    }

    fun forget(deviceId: String) {
        trustStore.forget(deviceId)
        trustRevision++
    }

    fun search() {
        error = null
        try {
            discovery.startDiscovery { found -> devices = found }
            searching = true
            searched = true
        } catch (e: RuntimeException) {
            searching = false
            error = "Could not search this network for Cast devices"
        }
    }

    fun stop() {
        discovery.stopDiscovery()
        searching = false
    }

    /** Discovery stops by itself after 30 s or when NsdManager refuses to start. */
    fun syncSearching() {
        if (searching && !discovery.isDiscovering) searching = false
    }
}

@Composable
private fun rememberCastPicker(): CastPicker {
    val context = LocalContext.current
    val picker = remember { CastPicker(CastDiscovery(context), SharedPreferencesCastTrustStore(context)) }
    DisposableEffect(picker) { onDispose { picker.stop() } }
    LaunchedEffect(picker.searching) {
        while (picker.searching) {
            delay(SEARCH_POLL_MS)
            picker.syncSearching()
        }
    }
    return picker
}

/** Cast picker inside Settings -> Audio -> Devices. */
@Composable
internal fun ChromecastSettingsRow(modifier: Modifier = Modifier) {
    CastPanel(modifier)
}

/** Cast picker on the Connect sheet, next to Orchard Connect devices. */
@Composable
internal fun ChromecastConnectRow(modifier: Modifier = Modifier) {
    CastPanel(modifier)
}

@Composable
private fun CastPanel(modifier: Modifier = Modifier) {
    val controls = OrchardGraph.from(LocalContext.current).cast
    val status by controls.status.collectAsState()
    val picker = rememberCastPicker()
    LaunchedEffect(picker.devices, picker.trustRevision, status.connected) {
        picker.refreshTrust(picker.devices.map { it.id })
    }
    status.pendingTrustPrompt?.let { prompt ->
        CastTrustChangedDialog(prompt, onConfirm = controls::confirmDeviceTrust, onCancel = controls::cancelDeviceTrust)
    }

    Column(modifier.fillMaxWidth()) {
        CastHeader(status, picker, controls)
        when {
            status.connected -> CastVolumeControls(status, controls)
            status.connecting -> Unit
            else -> CastDeviceList(picker, controls)
        }
        val message = status.error ?: picker.error
        if (message != null && !status.connected) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FrostedMessageBanner(message, isError = true)
            }
        }
    }
}

@Composable
private fun CastHeader(status: CastStatus, picker: CastPicker, controls: CastControls) {
    val accent = LocalAccent.current
    val name = status.device?.name
    val subtitle = when {
        status.connecting -> "Connecting to $name…"
        status.connected -> "Connected to $name"
        picker.searching -> "Searching this Wi-Fi network…"
        else -> "Play on speakers and displays on this Wi-Fi"
    }
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CastBadge(if (status.connected) Icons.Rounded.CastConnected else Icons.Rounded.Cast, status.connected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Chromecast",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = CanopyColors.Text,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.connected || status.connecting) accent else CanopyColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            status.connecting -> Unit
            status.connected -> TextButton(onClick = controls::disconnect) { Text("Disconnect", color = accent) }
            picker.searching -> TextButton(onClick = picker::stop) { Text("Stop", color = accent) }
            else -> TextButton(onClick = { controls.clearError(); picker.search() }) {
                Text(if (picker.searched) "Search again" else "Search", color = accent)
            }
        }
    }
}

@Composable
private fun CastBadge(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .background(CanopyColors.Canvas)
            .border(1.dp, CanopyColors.Rule, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) LocalAccent.current else CanopyColors.MutedStrong,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CastDeviceList(picker: CastPicker, controls: CastControls) {
    val devices = picker.devices
    if (devices.isEmpty()) {
        if (picker.searched && !picker.searching && picker.error == null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FrostedMessageBanner(
                    "No Cast devices found. The phone and the device must share a Wi-Fi network " +
                        "that allows device discovery, without a VPN in between.",
                )
            }
        }
        return
    }
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        devices.forEach { device ->
            CastDeviceRow(
                device,
                unauthenticated = device.id in picker.unauthenticated,
                onForget = {
                    picker.forget(device.id)
                    controls.forgetDevice(device.id)
                },
            ) {
                picker.stop()
                controls.connect(device)
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    device: CastDevice,
    unauthenticated: Boolean,
    onForget: () -> Unit,
    onClick: () -> Unit,
) {
    // clickable keeps the row in the D-Pad focus order and draws the focus highlight.
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Cast to ${device.name}", onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            deviceIcon(device),
            contentDescription = null,
            tint = CanopyColors.MutedStrong,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = CanopyColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                device.model?.takeIf { it.isNotBlank() },
                "Sin autenticación de dispositivo".takeIf { unauthenticated },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = CanopyColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Always offered so a device whose pin no longer matches is never stuck (CAST-ROT-INV-3).
        IconButton(onClick = onForget) {
            Icon(Icons.Rounded.LinkOff, contentDescription = "Olvidar ${device.name}", tint = CanopyColors.Muted)
        }
    }
}

/** Groups announce a fixed model name; `ca` bit 0 is video output. Speakers have neither. */
private fun deviceIcon(device: CastDevice): ImageVector = when {
    device.model?.contains("group", ignoreCase = true) == true -> Icons.Rounded.SpeakerGroup
    device.capabilities and CAPABILITY_VIDEO_OUT != 0 -> Icons.Rounded.Tv
    else -> Icons.Rounded.Speaker
}

@Composable
private fun CastVolumeControls(status: CastStatus, controls: CastControls) {
    var draft by remember { mutableFloatStateOf(status.volume) }
    // Follows changes made on the device itself or from the notification.
    LaunchedEffect(status.volume) { draft = status.volume }
    // One SET_VOLUME per pause in the gesture, not one per pixel or key repeat.
    LaunchedEffect(draft) {
        if (draft == status.volume) return@LaunchedEffect
        delay(VOLUME_DEBOUNCE_MS)
        controls.setDeviceVolume(draft)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controls.setDeviceMuted(!status.muted) }) {
            Icon(
                if (status.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (status.muted) "Unmute device" else "Mute device",
                tint = if (status.muted) CanopyColors.Muted else LocalAccent.current,
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            valueRange = 0f..1f,
            // 5 % per D-Pad press, matching the 0..20 scale of the Media3 notification.
            steps = VOLUME_STEPS - 1,
            colors = settingsSliderColors(),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
}

private const val SEARCH_POLL_MS = 500L
private const val VOLUME_DEBOUNCE_MS = 150L
private const val VOLUME_STEPS = 20
private const val CAPABILITY_VIDEO_OUT = 1
