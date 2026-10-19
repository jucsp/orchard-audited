// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.motion.PlayingBars
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val QueueRowShape = RoundedCornerShape(11.dp)

/** Queue (mockup 1B): the playing row, then "A CONTINUACIÓN". OK plays the row; the focused row shows the hint. */
@OptIn(ExperimentalComposeUiApi::class)
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
            item(key = "playing") { NowPlayingRow(current) }
        }
        item(key = "next_caption") {
            Text(
                "A CONTINUACIÓN",
                style = TvTokens.tvEyebrow.copy(color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp),
            )
        }
        itemsIndexed(upcoming, key = { index, track -> "upcoming_${track.id}_$index" }) { index, track ->
            QueueRow(track, onPlay = { onPlayIndex(baseIndex + index) }, onContentFocus = onContentFocus)
        }
        if (upcoming.isEmpty()) {
            item(key = "empty") {
                Text(
                    "La cola termina aquí",
                    style = TvTokens.tvMeta,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingRow(track: Track) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(QueueRowShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(34.dp), 6)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("SONANDO", style = TvTokens.tvEyebrow.copy(color = Color.White.copy(alpha = 0.8f)))
            Text(track.title, style = TvTokens.tvBody.copy(color = accent, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayingBars(accent, size = 14.dp)
    }
}

@Composable
private fun QueueRow(track: Track, onPlay: () -> Unit, onContentFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .tvClickable(TvFocusStyle.Row, QueueRowShape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onPlay)
            .clip(QueueRowShape)
            .background(if (focused) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .height(48.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(34.dp), 6)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = TvTokens.tvBody.copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (focused) {
            TvKeyHint("OK", "Reproducir", Modifier.padding(end = 8.dp))
        }
        Text(formatDuration(track.durationMs), style = TvTokens.tvMeta)
    }
}

private fun formatDuration(ms: Long): String = if (ms <= 0) "" else formatTime(ms)
