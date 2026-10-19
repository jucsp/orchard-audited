// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.sfg.orchard.mobile.app.MusicVideoState
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.screens.MusicVideoSurface
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val ControlShape = CircleShape
private val ProgressShape = RoundedCornerShape(4.dp)

/**
 * Full-screen music video for the TV tree (TvRoutes.MUSIC_VIDEO). It plays the video source the
 * phone player already owns and switches back to album audio through [onToggleMusicVideo].
 */
@Composable
fun TvMusicVideoScreen(
    musicVideo: MusicVideoState,
    player: Player?,
    track: Track?,
    playback: PlaybackSnapshot,
    onToggle: () -> Unit,
    onToggleMusicVideo: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onContentFocus: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onExit)

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        MusicVideoSurface(player, Modifier.fillMaxSize())

        // Top and bottom scrims keep the chrome legible over bright video.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = TvTokens.ContentStartX,
                    end = TvTokens.OverscanHorizontal,
                    top = TvTokens.OverscanVertical,
                    bottom = TvTokens.OverscanVertical,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Video musical",
                        style = TvTokens.tvTitleNowPlaying.copy(color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!track?.artist.isNullOrBlank()) {
                        Text(
                            track?.artist.orEmpty(),
                            style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                VideoControl(
                    Icons.Rounded.MusicNote,
                    "Volver a la canción",
                    onContentFocus,
                    onClick = { onToggleMusicVideo(); onExit() },
                )
                Spacer(Modifier.width(10.dp))
                VideoControl(Icons.Rounded.Close, "Cerrar video", onContentFocus, onClick = onExit)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoTransportIcon(Icons.Rounded.SkipPrevious, "Anterior", onContentFocus, onClick = onPrevious)
                Spacer(Modifier.width(40.dp))
                VideoTransportIcon(
                    if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playback.isPlaying) "Pausa" else "Reproducir",
                    onContentFocus,
                    playFocus = playFocus,
                    onClick = onToggle,
                )
                Spacer(Modifier.width(40.dp))
                VideoTransportIcon(Icons.Rounded.SkipNext, "Siguiente", onContentFocus, onClick = onNext)
            }

            Spacer(Modifier.weight(1f))

            VideoProgress(playback, onSeek, onContentFocus, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun VideoControl(
    icon: ImageVector,
    description: String,
    onContentFocus: () -> Unit,
    playFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
            .tvClickable(TvFocusStyle.Button, ControlShape, onFocus = onContentFocus, onClick = onClick)
            .clip(ControlShape)
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun VideoTransportIcon(
    icon: ImageVector,
    description: String,
    onContentFocus: () -> Unit,
    playFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val hero = playFocus != null
    Box(
        Modifier
            .size(if (hero) 72.dp else 56.dp)
            .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
            .tvClickable(TvFocusStyle.Transport, ControlShape, onFocus = onContentFocus, onClick = onClick)
            .clip(ControlShape)
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(if (hero) 36.dp else 28.dp))
    }
}

@Composable
private fun VideoProgress(
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = LocalPlayerClock.current
    val head by remember(clock) { derivedStateOf { clock.reported() } }
    val durationMs = playback.durationMs
    val fraction = (head.positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek((head.positionMs - 10_000).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { onSeek((head.positionMs + 10_000).coerceAtMost(durationMs)); true }
                    else -> false
                }
            }
            .tvFocusable(TvFocusStyle.Row, ProgressShape, onFocus = onContentFocus),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(ProgressShape).background(Color.White.copy(alpha = 0.25f)))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(ProgressShape)
                .background(accent),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(head.positionMs), style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.8f)))
            Text(formatTime(durationMs), style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.8f)))
        }
    }
}
