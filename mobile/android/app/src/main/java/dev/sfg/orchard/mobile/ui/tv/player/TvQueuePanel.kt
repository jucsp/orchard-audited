// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.motion.PlayingBars
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val QueueRowShape = RoundedCornerShape(10.dp)

/** Up-next queue: the playing row plus "A CONTINUACIÓN" rows; OK plays the row's index. */
@Composable
fun TvQueuePanel(
    playback: PlaybackSnapshot,
    onPlayIndex: (Int) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = playback.currentTrack
    val upcoming = playback.upcoming
    val baseIndex = (playback.currentIndex + 1).coerceAtLeast(0)

    LazyColumn(modifier = modifier.fillMaxSize().focusRestorer()) {
        if (current != null) {
            item(key = "playing") {
                NowPlayingHeader(current)
            }
        }
        item(key = "next_caption") {
            Text(
                "A CONTINUACIÓN",
                style = TvTokens.tvEyebrow,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        itemsIndexed(upcoming, key = { index, track -> "upcoming_${track.id}_$index" }) { index, track ->
            QueueRow(
                track = track,
                onPlay = { onPlayIndex(baseIndex + index) },
                onContentFocus = onContentFocus,
            )
        }
        if (upcoming.isEmpty()) {
            item(key = "empty") {
                Text(
                    "La cola termina aquí",
                    style = TvTokens.tvMeta,
                    color = CanopyColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(track: Track) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(44.dp), 8)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("SONANDO", style = TvTokens.tvEyebrow)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayingBars(accent, Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(track.title, style = TvTokens.tvBody.copy(color = accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, onPlay: () -> Unit, onContentFocus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .tvClickable(TvFocusStyle.Row, QueueRowShape, onFocus = onContentFocus, onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(36.dp), 6)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = TvTokens.tvBody.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = TvTokens.tvMeta, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(track.durationMs), style = TvTokens.tvMeta, color = CanopyColors.Muted)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1_000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
