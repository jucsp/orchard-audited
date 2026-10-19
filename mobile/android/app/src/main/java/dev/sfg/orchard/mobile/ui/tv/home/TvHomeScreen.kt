// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.sfg.orchard.mobile.model.CatalogItem
import dev.sfg.orchard.mobile.model.CatalogSection
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.screens.catalogSubtitle
import dev.sfg.orchard.mobile.ui.screens.discoverEntries
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.TvEmptyState
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.focus.tvLongClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val CardWidth = 123.dp
private val CardShape = RoundedCornerShape(TvTokens.CardRadius)
private val PillShape = RoundedCornerShape(50)

@Composable
fun TvHomeScreen(
    state: LoadState<List<CatalogSection>>,
    library: LibrarySnapshot,
    onPlay: (Track) -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlayCollection: (String, String) -> Unit,
    onPlayItem: (CatalogItem, shuffle: Boolean) -> Unit,
    onContentFocus: () -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onGoToSettings: () -> Unit,
    onTrackActions: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val entries = discoverEntries(state, library)
    val hero = entries.firstOrNull()
    val sections = (state as? LoadState.Content)?.value.orEmpty()
    val emptyLibrary = hero == null && sections.isEmpty() && state is LoadState.Content

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to CanopyColors.Chrome.copy(alpha = 0.35f),
                    0.55f to CanopyColors.Chrome.copy(alpha = 0.65f),
                    1f to Color.Black.copy(alpha = 0.75f),
                )
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal),
            contentPadding = PaddingValues(top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                !signedIn -> item(key = "signed_out") {
                    TvEmptyState(
                        title = "Inicia sesión",
                        message = "Conecta tu cuenta de YouTube Music para ver tus recomendaciones y reproducir música en tu TV.",
                        onContentFocus = onContentFocus,
                        actionLabel = "Iniciar sesión",
                        onAction = onSignIn,
                    )
                }

                emptyLibrary -> item(key = "empty") {
                    TvEmptyState(
                        title = "Nada por aquí todavía",
                        message = "Tu biblioteca está vacía. Ajusta tus preferencias o inicia sesión para descubrir música.",
                        onContentFocus = onContentFocus,
                        actionLabel = "Ir a Ajustes",
                        onAction = onGoToSettings,
                    )
                }

                else -> {
                    if (hero != null) {
                        item(key = "hero") {
                            TvHero(
                                item = hero.item,
                                caption = hero.caption,
                                onPlay = { onPlayItem(hero.item, false) },
                                onShuffle = { onPlayItem(hero.item, true) },
                                onOpenDetail = { onOpenDetail(hero.item.stableId) },
                                onContentFocus = onContentFocus,
                            )
                        }
                    }

                    itemsIndexed(sections, key = { _, section -> "section_${section.id}" }) { index, section ->
                        TvShelf(
                            title = section.title,
                            items = section.items,
                            onOpen = { openItem(it, onPlay, onOpenDetail) },
                            onPlayCollection = onPlayCollection,
                            onTrackActions = onTrackActions,
                            onContentFocus = onContentFocus,
                        )
                        if (index == sections.lastIndex) Spacer(Modifier.height(12.dp))
                    }

                    if (sections.isEmpty() && state !is LoadState.Content) {
                        item(key = "loading") { TvLoadingState(state) }
                    }
                }
            }
        }
    }
}

private fun openItem(item: CatalogItem, onPlay: (Track) -> Unit, onOpenDetail: (String) -> Unit) {
    when (item) {
        is CatalogItem.Song -> onPlay(item.track)
        else -> onOpenDetail(item.stableId)
    }
}

@Composable
private fun TvHero(
    item: CatalogItem,
    caption: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onOpenDetail: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().height(340.dp).padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                caption.uppercase(),
                style = TvTokens.tvEyebrow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(item.title, style = TvTokens.tvDisplay, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                catalogSubtitle(item),
                style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .focusRequester(focusRequester)
                        .tvClickable(TvFocusStyle.Button, PillShape, onFocus = onContentFocus, onClick = onPlay)
                        .clip(PillShape)
                        .background(accent)
                        .padding(horizontal = 30.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reproducir", color = Color.Black, style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold))
                }
                Box(
                    Modifier
                        .tvClickable(TvFocusStyle.Button, PillShape, onFocus = onContentFocus, onClick = onShuffle)
                        .clip(PillShape)
                        .background(CanopyColors.Glass.copy(alpha = 0.12f))
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aleatorio", color = CanopyColors.Text, style = TvTokens.tvBody)
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        ArtworkTile(
            item.artworkUrl,
            item.title,
            Modifier.size(280.dp).tvClickable(
                TvFocusStyle.Card,
                RoundedCornerShape(TvTokens.HeroArtworkRadius),
                onFocus = onContentFocus,
                onClick = onOpenDetail,
            ),
            TvTokens.HeroArtworkRadius.value.toInt(),
        )
    }
}

@Composable
private fun TvShelf(
    title: String,
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    onPlayCollection: (String, String) -> Unit,
    onTrackActions: (Track) -> Unit,
    onContentFocus: () -> Unit,
) {
    if (items.isEmpty()) return
    Column {
        Text(title, style = TvTokens.tvShelf, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(TvTokens.ShelfGap),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "${item.stableId}_$index" }) { _, item ->
                TvMediaCard(
                    item = item,
                    onClick = { onOpen(item) },
                    onLongPress = {
                        when (item) {
                            is CatalogItem.Song -> onTrackActions(item.track)
                            else -> onPlayCollection(item.stableId, item.title)
                        }
                    },
                    onContentFocus = onContentFocus,
                )
            }
        }
    }
}

@Composable
private fun TvMediaCard(
    item: CatalogItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val round = item is CatalogItem.Performer
    val shape = if (round) CircleShape else CardShape
    Column(
        Modifier.width(CardWidth).tvLongClickable(TvFocusStyle.Card, shape, onFocus = onContentFocus, onClick = onClick, onLongClick = onLongPress),
    ) {
        if (round) {
            Box(Modifier.size(CardWidth).clip(CircleShape).background(CanopyColors.Surface), contentAlignment = Alignment.Center) {
                ArtworkTile(item.artworkUrl, item.title, Modifier.fillMaxSize(), radius = (CardWidth.value / 2).toInt())
                Text(
                    item.title,
                    style = TvTokens.tvBody.copy(color = CanopyColors.Text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        } else {
            ArtworkTile(item.artworkUrl, item.title, Modifier.size(CardWidth).clip(CardShape), TvTokens.CardRadius.value.toInt())
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = TvTokens.tvBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(catalogSubtitle(item), style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvLoadingState(state: LoadState<List<CatalogSection>>) {
    val message = when (state) {
        is LoadState.Loading -> "Cargando recomendaciones…"
        is LoadState.Empty -> state.message
        is LoadState.Error -> state.message
        else -> ""
    }
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Text(message, style = TvTokens.tvBody.copy(color = CanopyColors.Muted))
    }
}
