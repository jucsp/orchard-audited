// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.LyricLine
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.RepeatMode
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.components.LosslessBadge
import dev.sfg.orchard.mobile.ui.screens.LyricPeek
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/** The now-playing side panel machine (Spec §7.2). */
enum class PanelState { Hidden, Lyrics, Queue }

private val TransportShape = CircleShape
private val ProgressShape = RoundedCornerShape(4.dp)
private val LikeShape = CircleShape

@Composable
fun TvNowPlaying(
    playback: PlaybackSnapshot,
    lyrics: LoadState<List<LyricLine>>,
    liked: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var panel by rememberSaveable { mutableStateOf(PanelState.Hidden) }
    val playFocus = remember { FocusRequester() }
    val hasTrack = playback.currentTrack != null
    LaunchedEffect(hasTrack) { if (hasTrack) playFocus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(CanopyColors.PlayerBackdrop.copy(alpha = 0.55f)),
    ) {
        if (panel == PanelState.Hidden) {
            Column(
                Modifier.fillMaxSize().padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PlayerColumn(
                    playback = playback,
                    lyrics = lyrics,
                    liked = liked,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onToggleLike = onToggleLike,
                    onContentFocus = onContentFocus,
                    onOpenLyrics = { panel = PanelState.Lyrics },
                    playFocus = playFocus,
                )
            }
            EdgeTabs(
                queueCount = playback.queue.size,
                onSelect = { panel = it },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = TvTokens.OverscanHorizontal),
            )
        } else {
            Row(Modifier.fillMaxSize().padding(start = TvTokens.ContentStartX)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(end = TvTokens.OverscanHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    PlayerColumn(
                        playback = playback,
                        lyrics = lyrics,
                        liked = liked,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        onToggleLike = onToggleLike,
                        onContentFocus = onContentFocus,
                        onOpenLyrics = { panel = PanelState.Lyrics },
                        playFocus = playFocus,
                    )
                }
                // The panel's glass reaches the edge; its content stays inside the safe area.
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0F1011).copy(alpha = 0.55f))
                        .padding(
                            end = TvTokens.OverscanHorizontal,
                            top = TvTokens.OverscanVertical,
                            bottom = TvTokens.OverscanVertical,
                        ),
                ) {
                    TvPanelTabs(
                        active = panel,
                        queueCount = playback.queue.size,
                        onSelect = { panel = it },
                        onContentFocus = onContentFocus,
                    )
                    when (panel) {
                        PanelState.Lyrics -> TvLyricsPanel(lyrics, onSeek, onContentFocus, Modifier.weight(1f))
                        PanelState.Queue -> TvQueuePanel(playback, onPlayQueueIndex, onContentFocus, Modifier.weight(1f))
                        PanelState.Hidden -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerColumn(
    playback: PlaybackSnapshot,
    lyrics: LoadState<List<LyricLine>>,
    liked: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onContentFocus: () -> Unit,
    onOpenLyrics: () -> Unit,
    playFocus: FocusRequester,
) {
    val track = playback.currentTrack
    if (track != null) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(230.dp), TvTokens.HeroArtworkRadius.value.toInt())
        Spacer(Modifier.height(18.dp))
        Text(
            "REPRODUCIENDO DESDE · ${playback.contextTitle.ifBlank { "Cola" }}",
            style = TvTokens.tvEyebrow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = TvTokens.tvTitleNowPlaying, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist.ifBlank { "Artista desconocido" }, style = TvTokens.tvBody.copy(color = CanopyColors.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LikeButton(liked, onToggleLike, onContentFocus)
            LosslessBadge(showBitrate = true, bitrateKbps = track.localBitrateKbps, isQobuz = track.isQobuz)
        }
        Spacer(Modifier.height(14.dp))
        TvProgressBar(playback, onSeek, onContentFocus, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        TvTransport(
            isPlaying = playback.isPlaying,
            shuffle = playback.shuffle,
            repeatMode = playback.repeatMode,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            onContentFocus = onContentFocus,
            playFocus = playFocus,
        )
        LyricPeek(lyrics, onOpenLyrics, Modifier.padding(top = 12.dp))
    } else {
        Text("Nada en reproducción", style = TvTokens.tvBody.copy(color = CanopyColors.Muted))
    }
}

@Composable
private fun LikeButton(liked: Boolean, onToggleLike: () -> Unit, onContentFocus: () -> Unit) {
    Box(
        Modifier
            .tvClickable(TvFocusStyle.Button, LikeShape, onFocus = onContentFocus, onClick = onToggleLike)
            .clip(LikeShape)
            .background(CanopyColors.Glass.copy(alpha = 0.12f))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Quitar de me gusta" else "Me gusta",
            tint = if (liked) CanopyColors.Favorite else CanopyColors.Text,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TvProgressBar(
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The snapshot carries no position; the clock is the only source of playback time.
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
        Box(Modifier.fillMaxWidth().height(4.dp).clip(ProgressShape).background(Color.White.copy(alpha = 0.2f)))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(ProgressShape)
                .background(accent),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(head.positionMs), style = TvTokens.tvMeta)
            Text(formatTime(durationMs), style = TvTokens.tvMeta)
        }
    }
}

@Composable
private fun TvTransport(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onContentFocus: () -> Unit,
    playFocus: FocusRequester,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TransportIcon(Icons.Rounded.Shuffle, if (shuffle) "Desactivar aleatorio" else "Aleatorio", active = shuffle, onClick = onShuffle, onContentFocus = onContentFocus)
        TransportIcon(Icons.Rounded.SkipPrevious, "Anterior", active = false, onClick = onPrevious, onContentFocus = onContentFocus)
        // Play/Pause is the transport hero: white circle with an accent ring when focused.
        Box(
            Modifier
                .size(60.dp)
                .focusRequester(playFocus)
                .tvClickable(TvFocusStyle.Transport, CircleShape, onFocus = onContentFocus, onClick = onToggle)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                tint = Color(0xFF111214),
                modifier = Modifier.size(32.dp),
            )
        }
        TransportIcon(Icons.Rounded.SkipNext, "Siguiente", active = false, onClick = onNext, onContentFocus = onContentFocus)
        TransportIcon(
            if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            if (repeatMode == RepeatMode.OFF) "Repetir todo" else "Repetir",
            active = repeatMode != RepeatMode.OFF,
            onClick = onRepeat,
            onContentFocus = onContentFocus,
        )
    }
}

@Composable
private fun TransportIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .size(44.dp)
            .tvClickable(TvFocusStyle.Button, CircleShape, onFocus = onContentFocus, onClick = onClick)
            .clip(CircleShape)
            .background(CanopyColors.Glass.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (active) accent else CanopyColors.Text.copy(alpha = 0.75f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun EdgeTabs(queueCount: Int, onSelect: (PanelState) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EdgeTab("‹ Letras") { onSelect(PanelState.Lyrics) }
        EdgeTab("Cola · $queueCount") { onSelect(PanelState.Queue) }
    }
}

@Composable
private fun EdgeTab(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .tvClickable(TvFocusStyle.Button, RoundedCornerShape(10.dp), onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(CanopyColors.GlassChrome.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvMeta.copy(color = CanopyColors.Text))
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1_000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
