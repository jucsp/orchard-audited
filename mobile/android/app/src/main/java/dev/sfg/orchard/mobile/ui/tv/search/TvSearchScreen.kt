// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.SearchResults
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.screens.catalogSubtitle
import dev.sfg.orchard.mobile.ui.screens.topResult
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val KeyShape = RoundedCornerShape(TvTokens.KeyboardKeyRadius)
private val ResultShape = RoundedCornerShape(10.dp)

private val alphabet = ('a'..'z').toList() + listOf('ñ', '-')

@Composable
fun TvSearchScreen(
    query: String,
    results: LoadState<SearchResults>,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) {
            onQueryChange(spoken)
        }
    }

    Row(
        modifier
            .fillMaxSize()
            .padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
    ) {
        // Left: input, voice and on-screen keyboard.
        Column(Modifier.width(290.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TvTokens.tvBody.copy(color = CanopyColors.Text),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CanopyColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(10.dp))
                if (context.packageManager.resolveActivity(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        },
                        0,
                    ) != null
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .tvClickable(TvFocusStyle.Button, CircleShape, onFocus = onContentFocus) {
                                voiceLauncher.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    },
                                )
                            }
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Mic, "Buscar por voz", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            TvKeyboard(query, onQueryChange, onContentFocus)
        }

        Spacer(Modifier.width(28.dp))

        // Right: results.
        Column(Modifier.weight(1f).fillMaxSize()) {
            val value = results as? LoadState.Content
            if (value == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (results is LoadState.Loading) "Buscando…" else "Escribe para buscar", style = TvTokens.tvMeta)
                }
            } else {
                SearchResultsList(value.value, query, onPlay, onOpenDetail, onContentFocus)
            }
        }
    }
}

@Composable
private fun TvKeyboard(query: String, onQueryChange: (String) -> Unit, onContentFocus: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        alphabet.chunked(7).forEach { rowChars ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowChars.forEach { ch ->
                    KeyButton(ch.toString(), first = ch == 'a', focusRequester, onContentFocus) {
                        onQueryChange(query + ch)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton("Espacio", first = false, null, onContentFocus) { onQueryChange("$query ") }
            KeyButton("Borrar", first = false, null, onContentFocus) { onQueryChange(query.dropLast(1)) }
            KeyButton("123·#", first = false, null, onContentFocus) { onQueryChange("$query#") }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    first: Boolean,
    focusRequester: FocusRequester?,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val modifier = if (first && focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    Box(
        modifier
            .height(32.dp)
            .tvClickable(TvFocusStyle.Key, KeyShape, onFocus = onContentFocus, onClick = onClick)
            .clip(KeyShape)
            .background(CanopyColors.Glass.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvBody.copy(color = CanopyColors.Text), maxLines = 1)
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    query: String,
    onPlay: (Track) -> Unit,
    onOpenDetail: (String) -> Unit,
    onContentFocus: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        val top = topResult(results, query)
        if (top != null) {
            item(key = "top") {
                Text("MEJOR RESULTADO", style = TvTokens.tvEyebrow)
                Spacer(Modifier.height(8.dp))
                ResultRow(top.title, catalogSubtitle(top), top is CatalogItem.Song, onContentFocus) {
                    when (top) {
                        is CatalogItem.Song -> onPlay(top.track)
                        else -> onOpenDetail(top.stableId)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (results.tracks.isNotEmpty()) {
            item(key = "songs_caption") { Text("CANCIONES", style = TvTokens.tvEyebrow, modifier = Modifier.padding(bottom = 6.dp)) }
            items(results.tracks.take(4), key = { "song_${it.id}" }) { track ->
                ResultRow(track.title, track.artist, true, onContentFocus) { onPlay(track) }
            }
        }
        val collections = results.albums.take(5).map { album ->
            Triple(album.title, album.artworkUrl, album.id)
        } + results.artists.take(5).map { artist ->
            Triple(artist.name, artist.artworkUrl, artist.id)
        } + results.podcasts.take(5).map { Triple(it.title, it.artworkUrl, it.id) }
        if (collections.isNotEmpty()) {
            item(key = "collections_caption") {
                Text("ÁLBUMES, ARTISTAS Y PÓDCASTS", style = TvTokens.tvEyebrow, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
            item(key = "collections") {
                CollectionGrid(collections, onContentFocus, onOpenDetail)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionGrid(
    collections: List<Triple<String, String, String>>,
    onContentFocus: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        collections.forEach { (title, artwork, id) ->
            Column(
                Modifier
                    .width(123.dp)
                    .tvClickable(TvFocusStyle.Card, RoundedCornerShape(TvTokens.CardRadius), onFocus = onContentFocus) { onOpenDetail(id) },
            ) {
                ArtworkTile(artwork, title, Modifier.size(123.dp), TvTokens.CardRadius.value.toInt())
                Spacer(Modifier.height(6.dp))
                Text(title, style = TvTokens.tvBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    showArtwork: Boolean,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .tvClickable(TvFocusStyle.Row, ResultShape, onFocus = onContentFocus, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = TvTokens.tvBody.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
