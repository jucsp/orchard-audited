// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sfg.orchard.mobile.model.Playlist
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val RowShape = RoundedCornerShape(10.dp)
private val SheetShape = RoundedCornerShape(20.dp)

/**
 * Host that owns the nullable-track lifecycle for [TvTrackActionsPopup], so the root screen only
 * keeps a `Track?` state and forwards the callbacks it already has.
 */
@Composable
fun TvTrackActionsHost(
    track: Track?,
    liked: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (String, Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    if (track == null) return
    TvTrackActionsPopup(
        track = track,
        liked = liked,
        playlists = playlists,
        onDismiss = onDismiss,
        onPlay = { onPlay(track) },
        onPlayNext = { onPlayNext(track) },
        onAddToQueue = { onAddToQueue(track) },
        onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) },
        onToggleLike = { onToggleLike(track) },
        onViewAlbum = track.albumId.takeIf(String::isNotBlank)?.let { id -> { onOpenDetail(id) } },
        onViewArtist = track.artistId.takeIf(String::isNotBlank)?.let { id -> { onOpenDetail(id) } },
    )
}

@Composable
private fun TvTrackActionsPopup(
    track: Track,
    liked: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onToggleLike: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        // Inside the dialog window: the requester is only attached once this content composes.
        LaunchedEffect(showPlaylists) { focus.requestFocus() }
        Surface(shape = SheetShape, color = CanopyColors.Surface, modifier = Modifier.width(480.dp).wrapContentHeight()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtworkTile(track.artworkUrl, track.title, Modifier.size(52.dp), 10)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = TvTokens.tvBody.copy(fontWeight = FontWeight.SemiBold),
                            color = CanopyColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                track.artist,
                                style = TvTokens.tvMeta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(CanopyColors.Rule))
                Spacer(Modifier.height(8.dp))

                if (showPlaylists) {
                    PlaylistPicker(
                        playlists = playlists,
                        focus = focus,
                        onAddToPlaylist = { playlistId -> onDismiss(); onAddToPlaylist(playlistId) },
                        onBack = { showPlaylists = false },
                    )
                } else {
                    ActionList(
                        track = track,
                        liked = liked,
                        focus = focus,
                        onDismiss = onDismiss,
                        onPlay = onPlay,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onOpenPlaylists = { showPlaylists = true },
                        onToggleLike = onToggleLike,
                        onViewAlbum = onViewAlbum,
                        onViewArtist = onViewArtist,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionList(
    track: Track,
    liked: Boolean,
    focus: FocusRequester,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onToggleLike: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
) {
    var slot = 0
    Column {
        ActionRow(Icons.Rounded.PlayArrow, "Reproducir ahora", focus, first = slot++ == 0) { onDismiss(); onPlay() }
        ActionRow(Icons.Rounded.SkipNext, "Reproducir a continuación", focus, first = slot++ == 0) { onDismiss(); onPlayNext() }
        ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Agregar a la cola", focus, first = slot++ == 0) { onDismiss(); onAddToQueue() }
        ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Agregar a playlist", focus, first = slot++ == 0, onClick = onOpenPlaylists)
        ActionRow(
            if (liked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Quitar de me gusta" else "Me gusta",
            focus,
            first = slot++ == 0,
            tint = if (liked) CanopyColors.Favorite else CanopyColors.Text,
        ) { onToggleLike() }
        onViewAlbum?.let { action ->
            if (track.albumId.isNotBlank()) {
                ActionRow(Icons.Rounded.Album, "Ir al álbum", focus, first = slot++ == 0) { onDismiss(); action() }
            }
        }
        onViewArtist?.let { action ->
            ActionRow(Icons.Rounded.Person, "Ir al artista", focus, first = slot++ == 0) { onDismiss(); action() }
        }
    }
}

@Composable
private fun PlaylistPicker(
    playlists: List<Playlist>,
    focus: FocusRequester,
    onAddToPlaylist: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column {
        if (playlists.isEmpty()) {
            Text("No tienes playlists donde agregar esta canción.", style = TvTokens.tvMeta)
            Spacer(Modifier.height(8.dp))
        }
        // Always a focus target, so the requester is attached even with no playlists.
        ActionRow(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", focus, first = true, onClick = onBack)
        playlists.forEach { playlist ->
            ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, playlist.title, focus, first = false, tint = CanopyColors.Text) {
                onAddToPlaylist(playlist.id)
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    focus: FocusRequester,
    first: Boolean,
    tint: Color = CanopyColors.Text,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier.focusRequester(focus) else Modifier)
            .tvClickable(TvFocusStyle.Row, RowShape, onClick = onClick)
            .clip(RowShape)
            .padding(vertical = 13.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = TvTokens.tvBody.copy(color = tint), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
