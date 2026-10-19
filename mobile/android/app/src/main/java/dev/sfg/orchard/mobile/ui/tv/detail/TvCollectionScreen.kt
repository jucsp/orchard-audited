// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.BrowseDetail
import dev.sfg.orchard.mobile.model.CatalogKind
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.components.ExplicitBadge
import dev.sfg.orchard.mobile.ui.motion.PlayingBars
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val ButtonShape = RoundedCornerShape(50)
private val RowShape = RoundedCornerShape(TvTokens.ListRowRadius)

@Composable
fun TvCollectionScreen(
    state: LoadState<BrowseDetail>,
    isSaved: Boolean,
    currentTrackId: String?,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onPlayTrack: (List<Track>, Int, String) -> Unit,
    onSave: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = (state as? LoadState.Content)?.value
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(detail?.id) { if (detail != null) playFocus.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(CanopyColors.Chrome.copy(alpha = 0.7f)),
    ) {
        if (detail == null) {
            Text(
                "Colección no disponible",
                style = TvTokens.tvMeta,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Row(
            Modifier.fillMaxSize().padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
        ) {
            // Fixed left column.
            Column(Modifier.width(260.dp).fillMaxSize()) {
                ArtworkTile(detail.artworkUrl, detail.title, Modifier.size(230.dp), TvTokens.HeroArtworkRadius.value.toInt())
                Spacer(Modifier.height(16.dp))
                Text(
                    eyebrowFor(detail),
                    style = TvTokens.tvEyebrow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(detail.title, style = TvTokens.tvHeadline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.subtitle.isNotBlank()) {
                    Text(detail.subtitle, style = TvTokens.tvBody.copy(color = CanopyColors.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Reproducir", filled = true, onContentFocus, Modifier.focusRequester(playFocus)) {
                        onPlayAll(detail.tracks, detail.title)
                    }
                    PillButton("Aleatorio", filled = false, onContentFocus) { onShuffle(detail.tracks, detail.title) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        icon = if (isSaved) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                        description = if (isSaved) "Quitar de guardados" else "Guardar",
                        tint = if (isSaved) CanopyColors.Favorite else CanopyColors.Text,
                        onContentFocus,
                        onClick = onSave,
                    )
                    IconButton(Icons.Rounded.MoreHoriz, "Más opciones", CanopyColors.Muted, onContentFocus) { }
                }
            }

            Spacer(Modifier.width(28.dp))

            // Track list.
            Column(Modifier.weight(1f).fillMaxSize()) {
                Text("PISTAS", style = TvTokens.tvEyebrow)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxSize().focusRestorer()) {
                    itemsIndexed(detail.tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        val isCurrent = track.id == currentTrackId
                        TrackRow(
                            index = index,
                            track = track,
                            isCurrent = isCurrent,
                            onPlay = { onPlayTrack(detail.tracks, index, detail.title) },
                            onContentFocus = onContentFocus,
                        )
                    }
                }
            }
        }
    }
}

private fun eyebrowFor(detail: BrowseDetail): String = when (detail.kind) {
    CatalogKind.ALBUM -> "ÁLBUM · ${detail.year.ifBlank { detail.artist }}"
    CatalogKind.PLAYLIST -> "PLAYLIST"
    CatalogKind.ARTIST -> "ARTISTA"
    CatalogKind.TRACK -> "CANCIÓN"
}

@Composable
private fun PillButton(
    label: String,
    filled: Boolean,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .tvClickable(TvFocusStyle.Button, ButtonShape, onFocus = onContentFocus, onClick = onClick)
            .clip(ButtonShape)
            .background(if (filled) accent else CanopyColors.Glass.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TvTokens.tvBody.copy(color = if (filled) Color.Black else CanopyColors.Text, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .tvClickable(TvFocusStyle.Button, CircleShape, onFocus = onContentFocus, onClick = onClick)
            .clip(CircleShape)
            .background(CanopyColors.Glass.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .tvClickable(TvFocusStyle.Row, RowShape, onFocus = onContentFocus, onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            PlayingBars(accent, Modifier.size(16.dp))
        } else {
            Text("${index + 1}", style = TvTokens.tvMeta.copy(color = CanopyColors.Muted), modifier = Modifier.width(24.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    style = TvTokens.tvBody.copy(color = if (isCurrent) accent else CanopyColors.Text, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.explicit) {
                    Spacer(Modifier.width(6.dp))
                    ExplicitBadge()
                }
            }
            if (track.artist.isNotBlank()) {
                Text(track.artist, style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatDuration(track.durationMs), style = TvTokens.tvMeta, color = CanopyColors.Muted)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1_000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
