// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.tv.prefs.TvNightCorner
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens
import kotlinx.coroutines.delay

/** Grey levels chosen to stay readable from the couch while emitting almost no light. */
private val NightTitle = Color(0xFF727272)
private val NightSubtitle = Color(0xFF686868)
private val NightTrack = Color(0xFF1E1E1E)
private val NightFill = Color(0xFF4A4A4A)

/** Small offsets the mini player cycles through to avoid burn-in on OLED panels. */
private val BurnInDrift = listOf(
    DpOffset(0.dp, 0.dp),
    DpOffset((-5).dp, (-3).dp),
    DpOffset((-8).dp, 2.dp),
    DpOffset((-3).dp, 5.dp),
)
private const val DriftEveryMs = 60_000L

/**
 * Night mode (mockup 1D): absolute black and a 170 dp mini player in a corner. Nothing animates.
 * D-pad or OK calls [onWake] (the caller shows the normal player for a few seconds). Back calls
 * [onExit]. Media keys are not consumed, so play/pause and skip keep working through the
 * existing MediaSession without lighting up the screen.
 */
@Composable
fun TvNightPlayer(
    playback: PlaybackSnapshot,
    corner: TvNightCorner,
    onWake: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DriftEveryMs)
            step = (step + 1) % BurnInDrift.size
        }
    }
    BackHandler(onBack = onExit)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onWake(); true }
                    else -> false
                }
            }
            .semantics { contentDescription = "Modo nocturno. Pulsa cualquier flecha para ver el reproductor." }
            .focusable(),
    ) {
        val alignment = when (corner) {
            TvNightCorner.BottomEnd -> Alignment.BottomEnd
            TvNightCorner.BottomStart -> Alignment.BottomStart
            TvNightCorner.TopEnd -> Alignment.TopEnd
        }
        val drift = BurnInDrift[step]
        NightMiniPlayer(
            playback,
            Modifier
                .align(alignment)
                .padding(horizontal = TvTokens.OverscanHorizontal, vertical = TvTokens.OverscanVertical)
                .offset(drift.x, drift.y),
        )
    }
}

@Composable
private fun NightMiniPlayer(playback: PlaybackSnapshot, modifier: Modifier = Modifier) {
    val track = playback.currentTrack ?: return
    val clock = LocalPlayerClock.current
    val fraction by remember(clock, playback.durationMs) {
        derivedStateOf { (clock.reported().positionMs.toFloat() / playback.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }
    }
    Column(modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Alpha over black dims the cover to ~38 % brightness.
            ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(32.dp).alpha(0.38f), 5)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = NightTitle, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = NightSubtitle, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (playback.isPlaying) "▮▮" else "▶", color = Color(0xFF5E5E5E), fontSize = 9.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NightTrack)) {
            Box(Modifier.fillMaxWidth(fraction).height(1.dp).background(NightFill))
        }
    }
}
