// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.LyricLine
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.components.LosslessBadge
import dev.sfg.orchard.mobile.ui.components.rememberArtworkPalette
import dev.sfg.orchard.mobile.ui.screens.LyricPeek
import dev.sfg.orchard.mobile.ui.tv.prefs.TvPlayerPanel
import dev.sfg.orchard.mobile.ui.tv.prefs.TvUiState
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens
import kotlinx.coroutines.delay

private val PanelShape = RoundedCornerShape(TvTokens.PanelRadius)

/** How long the normal player stays visible after a key press in night mode. */
private const val NightPeekMs = 8_000L

/**
 * Now playing (mockups 1A–1D). The side panel state is persisted in [TvUiState.playerPanel]:
 * Lyrics/Queue show the two-column layout (1A/1B); Hidden shows the side-by-side layout (1C).
 * In night mode (1D) the screen goes black with a tiny corner player; any D-pad key shows the
 * normal player for [NightPeekMs], and Back leaves night mode.
 */
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
    hasVideo: Boolean,
    onOpenMusicVideo: () -> Unit,
    onOpenListeningParty: () -> Unit,
    onTrackActions: (Track) -> Unit,
    ui: TvUiState,
    onPanelChange: (TvPlayerPanel) -> Unit,
    nightMode: Boolean,
    onNightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.currentTrack
    val synced = (lyrics as? LoadState.Content)?.value?.any { it.startMs != null } == true
    // Prefer lyrics, but fall back to the queue when the song has no synced lyrics (Spec §7.2).
    val panel = if (ui.playerPanel == TvPlayerPanel.Lyrics && !synced && lyrics !is LoadState.Loading) {
        TvPlayerPanel.Queue
    } else {
        ui.playerPanel
    }

    var menuOpen by remember { mutableStateOf(false) }
    var peek by remember { mutableStateOf(false) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(nightMode) { peek = false }
    LaunchedEffect(peek, lastKeyAt) {
        if (nightMode && peek) {
            delay(NightPeekMs)
            peek = false
        }
    }

    if (nightMode && !peek && track != null) {
        TvNightPlayer(
            playback = playback,
            corner = ui.nightCorner,
            onWake = {
                lastKeyAt = SystemClock.uptimeMillis()
                peek = true
            },
            onExit = { onNightModeChange(false) },
            modifier = modifier,
        )
        return
    }

    // While peeking, Back leaves night mode instead of the screen.
    BackHandler(enabled = nightMode) { onNightModeChange(false) }
    LaunchedEffect(track != null, peek) { if (track != null) runCatching { playFocus.requestFocus() } }

    val actions = TvTransportActions(
        onToggle = onToggle,
        onNext = onNext,
        onPrevious = onPrevious,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onToggleLike = onToggleLike,
        onNightMode = { onNightModeChange(true) },
        onMore = { menuOpen = true },
        onContentFocus = onContentFocus,
    )

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (nightMode && it.type == KeyEventType.KeyDown) lastKeyAt = SystemClock.uptimeMillis()
                false
            },
    ) {
        if (track == null) {
            Text("Nada en reproducción", style = TvTokens.tvBody, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (panel == TvPlayerPanel.Hidden) {
            HiddenLayout(playback, track, lyrics, liked, synced, actions, playFocus, onSeek, onPanelChange)
        } else {
            PanelLayout(playback, track, lyrics, liked, panel, actions, playFocus, onSeek, onPlayQueueIndex, onPanelChange)
        }
        if (nightMode) {
            Text(
                "Modo nocturno · Atrás para salir",
                style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    if (menuOpen) {
        TvPlayerMenu(
            hasVideo = hasVideo,
            onDismiss = { menuOpen = false },
            onNightMode = { menuOpen = false; onNightModeChange(true) },
            onOpenMusicVideo = { menuOpen = false; onOpenMusicVideo() },
            onOpenListeningParty = { menuOpen = false; onOpenListeningParty() },
            onTrackActions = { menuOpen = false; track?.let(onTrackActions) },
        )
    }
}

/** 1A/1B: player column on the left, glass panel with lyrics or queue on the right. */
@Composable
private fun PanelLayout(
    playback: PlaybackSnapshot,
    track: Track,
    lyrics: LoadState<List<LyricLine>>,
    liked: Boolean,
    panel: TvPlayerPanel,
    actions: TvTransportActions,
    playFocus: FocusRequester,
    onSeek: (Long) -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onPanelChange: (TvPlayerPanel) -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvTokens.OverscanHorizontal, vertical = TvTokens.OverscanVertical),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Eyebrow(playback)
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                FloatingCover(track, 220)
                Spacer(Modifier.width(20.dp))
                LosslessBadge(showBitrate = true, bitrateKbps = track.localBitrateKbps, isQobuz = track.isQobuz)
            }
            Spacer(Modifier.height(20.dp))
            Text(track.title, style = TvTokens.tvTitleNowPlaying, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                track.artist.ifBlank { "Artista desconocido" },
                style = TvTokens.tvBody.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.86f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            TvProgressBar(playback, onSeek, actions.onContentFocus)
            Spacer(Modifier.height(11.dp))
            TvTransportRow(playback, liked, actions, TvTransportLayout.Wide, playFocus)
        }
        Column(
            Modifier
                .width(404.dp)
                .fillMaxHeight()
                .clip(PanelShape)
                .background(TvTokens.GlassPanel)
                .border(1.dp, TvTokens.GlassBorder, PanelShape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            TvPanelTabs(
                active = panel,
                queueCount = playback.upcoming.size,
                onSelect = onPanelChange,
                onContentFocus = actions.onContentFocus,
            )
            when (panel) {
                TvPlayerPanel.Lyrics -> TvLyricsPanel(lyrics, onSeek, actions.onContentFocus, Modifier.weight(1f))
                TvPlayerPanel.Queue -> TvQueuePanel(playback, onPlayQueueIndex, actions.onContentFocus, Modifier.weight(1f))
                TvPlayerPanel.Hidden -> Unit
            }
            if (panel == TvPlayerPanel.Lyrics) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TvKeyHint("◀", "Controles")
                    TvKeyHint("OK", "Ir a esta línea")
                }
            }
        }
    }
}

/** 1C: big cover and info side by side; Lyrics and Queue as icon buttons on the right edge. */
@Composable
private fun HiddenLayout(
    playback: PlaybackSnapshot,
    track: Track,
    lyrics: LoadState<List<LyricLine>>,
    liked: Boolean,
    synced: Boolean,
    actions: TvTransportActions,
    playFocus: FocusRequester,
    onSeek: (Long) -> Unit,
    onPanelChange: (TvPlayerPanel) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical)) { Eyebrow(playback) }
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingCover(track, 320)
            Spacer(Modifier.width(48.dp))
            Column(Modifier.width(350.dp).height(320.dp)) {
                LosslessBadge(showBitrate = true, bitrateKbps = track.localBitrateKbps, isQobuz = track.isQobuz)
                Spacer(Modifier.height(11.dp))
                Text(track.title, style = TvTokens.tvTitleNowPlaying.copy(fontSize = 46.sp, lineHeight = 50.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = TvTokens.tvBody.copy(fontSize = 19.sp, color = Color.White.copy(alpha = 0.88f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                LyricPeek(lyrics, onOpen = { onPanelChange(TvPlayerPanel.Lyrics) }, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.weight(1f))
                TvProgressBar(playback, onSeek, actions.onContentFocus)
                Spacer(Modifier.height(10.dp))
                TvTransportRow(playback, liked, actions, TvTransportLayout.Compact, playFocus)
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = TvTokens.OverscanHorizontal),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (synced) {
                TvEdgeIconButton(Icons.Rounded.Mic, "Letras", null, actions.onContentFocus) { onPanelChange(TvPlayerPanel.Lyrics) }
            }
            TvEdgeIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "Cola", playback.upcoming.size, actions.onContentFocus) {
                onPanelChange(TvPlayerPanel.Queue)
            }
        }
    }
}

@Composable
private fun Eyebrow(playback: PlaybackSnapshot) {
    Text(
        "REPRODUCIENDO DESDE · ${playback.contextTitle.ifBlank { "Cola" }}",
        style = TvTokens.tvEyebrow.copy(color = Color.White.copy(alpha = 0.86f)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Cover with a soft shadow tinted by its own colours, so it "floats" over the backdrop. */
@Composable
private fun FloatingCover(track: Track, sizeDp: Int) {
    val palette = rememberArtworkPalette(track.artworkUrl)
    val shape = RoundedCornerShape(TvTokens.HeroArtworkRadius)
    ArtworkTile(
        track.artworkUrl,
        "Portada de ${track.title}",
        Modifier
            .size(sizeDp.dp)
            .shadow(36.dp, shape, ambientColor = palette.accent, spotColor = palette.accent),
        TvTokens.HeroArtworkRadius.value.toInt(),
    )
}
