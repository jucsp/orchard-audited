// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.auth.AuthState
import dev.sfg.orchard.mobile.model.BuiltInHomeSection
import dev.sfg.orchard.mobile.model.HomeSectionConfig
import dev.sfg.orchard.mobile.model.OrchardSettings
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.player.TvKeyHint
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/**
 * Home layout editor for the remote (mockup 3B). Drag-and-drop does not exist on a D-pad, so rows
 * use a "move mode": OK picks a row up, ▲▼ moves it, OK drops it, Back cancels and restores the
 * original position. ▶ on a row (outside move mode) toggles its visibility.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeLayoutScreen(
    settings: OrchardSettings,
    auth: AuthState,
    onSettings: (OrchardSettings) -> Unit,
    onBack: () -> Unit,
    onContentFocus: () -> Unit,
) {
    var offline by remember { mutableStateOf(false) }
    val saved = if (offline) settings.homeLayoutOffline else settings.homeLayoutOnline
    var working by remember(offline, saved) { mutableStateOf(saved) }
    var moving by remember(offline) { mutableIntStateOf(-1) }
    var original by remember(offline) { mutableStateOf<List<HomeSectionConfig>>(emptyList()) }
    val requesters = remember(working.size) { List(working.size) { FocusRequester() } }
    val listState = rememberLazyListState()

    fun commit(list: List<HomeSectionConfig>) {
        onSettings(if (offline) settings.copy(homeLayoutOffline = list) else settings.copy(homeLayoutOnline = list))
    }

    BackHandler(enabled = moving >= 0) {
        working = original
        moving = -1
    }
    BackHandler(enabled = moving < 0, onBack = onBack)

    LaunchedEffect(moving, working) {
        if (moving >= 0) {
            listState.animateScrollToItem(moving)
            runCatching { requesters[moving].requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
    ) {
        Text("Diseño de Inicio", style = TvTokens.tvHeadline)
        Spacer(Modifier.height(6.dp))
        Text("Elige qué secciones aparecen en Inicio y en qué orden.", style = TvTokens.tvMeta)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("Con conexión", !offline, onContentFocus) { offline = false }
            Segment("Sin conexión", offline, onContentFocus) { offline = true }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (moving >= 0) {
                TvKeyHint("▲▼", "Mover")
                TvKeyHint("OK", "Soltar")
                TvKeyHint("Atrás", "Cancelar")
            } else {
                TvKeyHint("OK", "Mover")
                TvKeyHint("▶", "Mostrar / ocultar")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).focusRestorer(),
        ) {
            itemsIndexed(working, key = { _, c -> c.section.name }) { index, config ->
                SectionRow(
                    config = config,
                    title = config.section.tvTitle(auth),
                    lifted = index == moving,
                    modifier = Modifier
                        .focusRequester(requesters.getOrElse(index) { FocusRequester() })
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                moving == index && event.key == Key.DirectionUp && index > 0 -> {
                                    working = working.swap(index, index - 1); moving = index - 1; true
                                }
                                moving == index && event.key == Key.DirectionDown && index < working.lastIndex -> {
                                    working = working.swap(index, index + 1); moving = index + 1; true
                                }
                                // While lifted, nothing else may steal focus.
                                moving == index && event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) -> true
                                moving < 0 && event.key == Key.DirectionRight -> {
                                    val next = working.toMutableList().also { it[index] = config.copy(enabled = !config.enabled) }
                                    working = next
                                    commit(next)
                                    true
                                }
                                else -> false
                            }
                        },
                    onFocus = onContentFocus,
                    onClick = {
                        if (moving == index) {
                            moving = -1
                            commit(working)
                        } else if (moving < 0) {
                            original = working
                            moving = index
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    config: HomeSectionConfig,
    title: String,
    lifted: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(TvTokens.SettingsCardRadius)
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val inverted = focused && !lifted
    val content = if (inverted) CanopyColors.Chrome else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .tvClickable(TvFocusStyle.Row, shape, onFocus = onFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(shape)
            .background(
                when {
                    lifted -> accent.copy(alpha = 0.30f)
                    inverted -> CanopyColors.Text
                    else -> TvTokens.GlassSurface
                },
            )
            .border(if (lifted) 2.dp else 1.dp, if (lifted) accent else TvTokens.GlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DragIndicator, null, tint = content.copy(alpha = if (lifted) 1f else 0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = content.copy(alpha = if (config.enabled) 1f else 0.5f),
            style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (config.enabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (config.enabled) "Visible" else "Oculta",
            tint = content.copy(alpha = if (config.enabled) 1f else 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    val filled = selected || focused
    Text(
        label,
        color = if (filled) Color(0xFF12101A) else Color.White,
        style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .tvClickable(TvFocusStyle.Button, shape, onFocus = onFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also {
    val tmp = it[a]; it[a] = it[b]; it[b] = tmp
}

/** Spanish TV titles; kept here so the phone's SettingsHomeLayout.kt needs no change. */
private fun BuiltInHomeSection.tvTitle(auth: AuthState): String = when (this) {
    BuiltInHomeSection.YOUR_PLAYLISTS -> "Tus playlists"
    BuiltInHomeSection.SUBSCRIBED_ARTISTS -> "Artistas que sigues"
    BuiltInHomeSection.TOP_SONGS -> if (auth is AuthState.SignedIn) "Tu resumen de escucha" else "Canciones más escuchadas"
    BuiltInHomeSection.RECOMMENDATIONS -> "Recomendaciones"
    BuiltInHomeSection.DOWNLOADED_PLAYLISTS -> "Playlists descargadas"
    BuiltInHomeSection.DOWNLOADED_ARTISTS -> "Artistas descargados"
    BuiltInHomeSection.DOWNLOADED_ALBUMS -> "Álbumes descargados"
    BuiltInHomeSection.DOWNLOADED_SONGS -> "Canciones descargadas"
}
