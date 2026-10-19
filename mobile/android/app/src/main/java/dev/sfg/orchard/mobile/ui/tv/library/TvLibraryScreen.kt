// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.Album
import dev.sfg.orchard.mobile.model.Artist
import dev.sfg.orchard.mobile.model.LibraryFilter
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.Playlist
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val FilterShape = RoundedCornerShape(50)
private val CardShape = RoundedCornerShape(TvTokens.CardRadius)
private val RowShape = RoundedCornerShape(TvTokens.ListRowRadius)

private val filters = listOf(
    LibraryFilter.PLAYLISTS,
    LibraryFilter.ARTISTS,
    LibraryFilter.ALBUMS,
    LibraryFilter.SONGS,
    LibraryFilter.RECENT,
    LibraryFilter.DOWNLOADS,
)

@Composable
fun TvLibraryScreen(
    library: LibrarySnapshot,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { filterFocus.requestFocus() }
    Column(
        modifier
            .fillMaxSize()
            .padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
    ) {
        Text("Biblioteca", style = TvTokens.tvHeadline)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            filters.forEach { candidate ->
                val selected = candidate == filter
                FilterPill(
                    candidate.label(),
                    selected,
                    onContentFocus,
                    if (selected) Modifier.focusRequester(filterFocus) else Modifier,
                ) { onFilterChange(candidate) }
            }
        }
        Spacer(Modifier.height(18.dp))

        when (filter) {
            LibraryFilter.SONGS -> TvSongList(library.likedTracks, onPlay, onContentFocus)
            else -> TvLibraryGrid(library, filter, onOpenDetail, onPlay, onContentFocus)
        }
    }
}

private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.PLAYLISTS -> "Playlists"
    LibraryFilter.ARTISTS -> "Artistas"
    LibraryFilter.ALBUMS -> "Álbumes"
    LibraryFilter.SONGS -> "Canciones"
    LibraryFilter.RECENT -> "Recientes"
    LibraryFilter.DOWNLOADS -> "Descargas"
    LibraryFilter.LOCAL -> "Local"
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onContentFocus: () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .tvClickable(TvFocusStyle.Button, FilterShape, onFocus = onContentFocus, onClick = onClick)
            .clip(FilterShape)
            .background(if (selected) accent.copy(alpha = 0.28f) else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvBody.copy(color = if (selected) CanopyColors.Text else CanopyColors.MutedStrong))
    }
}

@Composable
private fun TvLibraryGrid(
    library: LibrarySnapshot,
    filter: LibraryFilter,
    onOpenDetail: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onContentFocus: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(TvTokens.ShelfColumns),
        modifier = Modifier.fillMaxSize().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(TvTokens.ShelfGap),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (filter == LibraryFilter.PLAYLISTS) {
            item(key = "liked") {
                Column(
                    Modifier
                        .width(123.dp)
                        .tvClickable(TvFocusStyle.Card, CardShape, onFocus = onContentFocus) {
                            onOpenDetail(dev.sfg.orchard.mobile.catalog.CatalogRepository.LIKED_MUSIC)
                        },
                ) {
                    Box(
                        Modifier.size(123.dp).clip(CardShape).background(Brush.linearGradient(listOf(CanopyColors.Favorite, Color(0xFF7B2FF7)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Favorite, "Me gusta", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Me gusta", style = TvTokens.tvBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when (filter) {
            LibraryFilter.PLAYLISTS -> items(library.savedPlaylists, key = { "pl_${it.id}" }) { playlist ->
                CollectionCard(playlist.title, playlist.artworkUrl, onContentFocus) { onOpenDetail(playlist.id) }
            }
            LibraryFilter.ARTISTS -> items(library.savedArtists, key = { "ar_${it.id}" }) { artist ->
                CollectionCard(artist.name, artist.artworkUrl, onContentFocus, round = true) { onOpenDetail(artist.id) }
            }
            LibraryFilter.ALBUMS -> items(library.savedAlbums, key = { "al_${it.id}" }) { album ->
                CollectionCard(album.title, album.artworkUrl, onContentFocus) { onOpenDetail(album.id) }
            }
            // Recent entries are tracks, not collections: OK plays them.
            LibraryFilter.RECENT -> items(library.recentlyPlayed, key = { "re_${it.id}" }) { track ->
                CollectionCard(track.title, track.artworkUrl, onContentFocus) { onPlay(track) }
            }
            LibraryFilter.DOWNLOADS -> item(key = "downloads_placeholder") {
                Text("Tus descargas aparecen aquí", style = TvTokens.tvMeta, color = CanopyColors.Muted)
            }
            else -> Unit
        }
    }
}

@Composable
private fun CollectionCard(
    title: String,
    artworkUrl: String,
    onContentFocus: () -> Unit,
    round: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = if (round) CircleShape else CardShape
    Column(
        Modifier
            .width(123.dp)
            .tvClickable(TvFocusStyle.Card, shape, onFocus = onContentFocus, onClick = onClick),
    ) {
        if (round) {
            Box(Modifier.size(123.dp).clip(CircleShape).background(CanopyColors.Surface), contentAlignment = Alignment.Center) {
                ArtworkTile(artworkUrl, title, Modifier.fillMaxSize(), radius = 62)
            }
        } else {
            ArtworkTile(artworkUrl, title, Modifier.size(123.dp).clip(CardShape), TvTokens.CardRadius.value.toInt())
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = TvTokens.tvBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvSongList(tracks: List<Track>, onPlay: (Track) -> Unit, onContentFocus: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().focusRestorer()) {
        items(tracks, key = { "song_${it.id}" }) { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .tvClickable(TvFocusStyle.Row, RowShape, onFocus = onContentFocus) { onPlay(track) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(40.dp), 6)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = TvTokens.tvBody.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatDuration(track.durationMs), style = TvTokens.tvMeta, color = CanopyColors.Muted)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1_000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
