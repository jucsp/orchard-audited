// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val CardShape = RoundedCornerShape(TvTokens.CardRadius)
private val PillShape = RoundedCornerShape(50)
private val HeroShape = RoundedCornerShape(TvTokens.HeroCardRadius)

/**
 * Home (mockup 2A): a glass "featured" card inside the safe area, then shelves of five wide cards
 * whose titles wrap to two lines. No opaque background: the app backdrop shows through.
 */
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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal),
        contentPadding = PaddingValues(top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical + 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        TvHeroCard(
                            item = hero.item,
                            caption = hero.caption,
                            onPlay = { onPlayItem(hero.item, false) },
                            onShuffle = { onPlayItem(hero.item, true) },
                            onOpenDetail = { onOpenDetail(hero.item.stableId) },
                            onContentFocus = onContentFocus,
                        )
                    }
                }
                itemsIndexed(sections, key = { _, section -> "section_${section.id}" }) { _, section ->
                    TvShelf(
                        title = section.title,
                        items = section.items,
                        onOpen = { openItem(it, onPlay, onOpenDetail) },
                        onPlayCollection = onPlayCollection,
                        onTrackActions = onTrackActions,
                        onContentFocus = onContentFocus,
                    )
                }
                if (sections.isEmpty() && state !is LoadState.Content) {
                    item(key = "loading") { TvLoadingState(state) }
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
private fun TvHeroCard(
    item: CatalogItem,
    caption: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onOpenDetail: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(HeroShape)
            .background(TvTokens.GlassSurface)
            .border(1.dp, TvTokens.GlassBorder, HeroShape)
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(caption.uppercase(), style = TvTokens.tvEyebrow.copy(color = accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = TvTokens.tvDisplay, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(catalogSubtitle(item), style = TvTokens.tvBody.copy(color = Color.White.copy(alpha = 0.86f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton("Reproducir", { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(15.dp), tint = it) }, primary = true, onClick = onPlay, onContentFocus = onContentFocus, modifier = Modifier.focusRequester(playFocus))
                HeroButton("Aleatorio", { Icon(Icons.Rounded.Shuffle, null, Modifier.size(15.dp), tint = it) }, primary = false, onClick = onShuffle, onContentFocus = onContentFocus)
            }
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.size(170.dp), contentAlignment = Alignment.CenterEnd) {
            ArtworkTile(
                item.artworkUrl,
                "",
                Modifier.size(150.dp).graphicsLayer { rotationZ = -4f; translationX = -26f; alpha = 0.55f },
                TvTokens.HeroArtworkRadius.value.toInt(),
            )
            ArtworkTile(
                item.artworkUrl,
                item.title,
                Modifier.size(160.dp).tvClickable(TvFocusStyle.Card, RoundedCornerShape(TvTokens.HeroArtworkRadius), onFocus = onContentFocus, onClick = onOpenDetail),
                TvTokens.HeroArtworkRadius.value.toInt(),
            )
        }
    }
}

/** Pill button that inverts to white on focus (mockup 4). Primary rests in the accent colour. */
@Composable
internal fun HeroButton(
    label: String,
    icon: @Composable (Color) -> Unit,
    primary: Boolean,
    onClick: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> CanopyColors.Text
        primary -> accent
        else -> Color.White.copy(alpha = 0.18f)
    }
    val content = if (focused || primary) CanopyColors.Chrome else Color.White
    Row(
        modifier
            .tvClickable(TvFocusStyle.Button, PillShape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(PillShape)
            .background(background)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon(content)
        Text(label, color = content, style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
        Spacer(Modifier.height(11.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Five cards fill the row exactly; the sixth peeks in as the scroll hint.
            val gaps = TvTokens.ShelfGap * (TvTokens.ShelfColumns - 1)
            val cardWidth = (maxWidth - gaps) / TvTokens.ShelfColumns
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(TvTokens.ShelfGap),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                itemsIndexed(items, key = { index, item -> "${item.stableId}_$index" }) { _, item ->
                    TvMediaCard(
                        item = item,
                        width = cardWidth,
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
}

@Composable
private fun TvMediaCard(
    item: CatalogItem,
    width: Dp,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onContentFocus: () -> Unit,
) {
    val round = item is CatalogItem.Performer
    val shape = if (round) CircleShape else CardShape
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        ArtworkTile(
            item.artworkUrl,
            item.title,
            Modifier
                .size(width)
                .tvLongClickable(TvFocusStyle.Card, shape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick, onLongClick = onLongPress)
                .clip(shape),
            if (round) (width.value / 2).toInt() else TvTokens.CardRadius.value.toInt(),
        )
        Spacer(Modifier.height(9.dp))
        Text(
            item.title,
            style = TvTokens.tvCardTitle.copy(
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                color = if (focused) Color.White else Color(0xFFE2E7EC),
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
        )
        Text(catalogSubtitle(item), style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
