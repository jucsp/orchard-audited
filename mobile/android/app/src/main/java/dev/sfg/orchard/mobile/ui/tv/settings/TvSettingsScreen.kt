// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/** Left-column entry of the TV settings (mockup 3A). */
data class TvSettingsCategory(val id: String, val label: String, val icon: ImageVector)

/**
 * One cell of the right-hand grid. Pages build a flat list of these; the grid lays them out two per
 * row, and [Header] / [Swatches] span both columns.
 */
sealed interface TvSettingItem {
    val key: String

    data class Header(val text: String) : TvSettingItem {
        override val key = "h_$text"
    }

    data class Toggle(
        val title: String,
        val subtitle: String?,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : TvSettingItem {
        override val key = "t_$title"
    }

    /** OK cycles to the next value; the card shows "‹ value ›". */
    data class Choice(
        val title: String,
        val value: String,
        val onNext: () -> Unit,
    ) : TvSettingItem {
        override val key = "c_$title"
    }

    data class Action(
        val title: String,
        val subtitle: String?,
        val onClick: () -> Unit,
        val destructive: Boolean = false,
    ) : TvSettingItem {
        override val key = "a_$title"
    }

    /** Colour swatches; a null entry means "follow the app theme". */
    data class Swatches(
        val title: String,
        val swatches: List<Int?>,
        val selected: Int?,
        val onSelect: (Int?) -> Unit,
    ) : TvSettingItem {
        override val key = "s_$title"
    }
}

/**
 * Two-column TV settings (replaces the stretched phone screen). Categories select on focus, so ▲▼
 * in the left column previews the page and ▶ enters the grid. Back from the grid returns to the
 * category list through the normal focus search.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSettingsScreen(
    categories: List<TvSettingsCategory>,
    selected: TvSettingsCategory,
    onSelect: (TvSettingsCategory) -> Unit,
    items: (TvSettingsCategory) -> List<TvSettingItem>,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxSize()
            .padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal, top = TvTokens.OverscanVertical, bottom = TvTokens.OverscanVertical),
    ) {
        Column(Modifier.width(220.dp).fillMaxHeight().focusRestorer()) {
            Text("Ajustes", style = TvTokens.tvHeadline, modifier = Modifier.padding(start = 12.dp, bottom = 18.dp))
            categories.forEach { category ->
                CategoryRow(category, category.id == selected.id, onFocus = {
                    onContentFocus()
                    onSelect(category)
                })
            }
        }
        Spacer(Modifier.width(28.dp))
        Crossfade(selected, label = "TvSettingsPage", modifier = Modifier.weight(1f)) { category ->
            Column(Modifier.fillMaxSize()) {
                Text(category.label, style = TvTokens.tvShelf, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 14.dp))
                val list = items(category)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 24.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    itemsIndexed(
                        list,
                        key = { _, item -> item.key },
                        span = { _, item ->
                            GridItemSpan(if (item is TvSettingItem.Header || item is TvSettingItem.Swatches) maxLineSpan else 1)
                        },
                    ) { _, item -> SettingCell(item, onContentFocus) }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: TvSettingsCategory, selected: Boolean, onFocus: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    val bg = when {
        focused -> CanopyColors.Text
        selected -> accent.copy(alpha = 0.24f)
        else -> Color.Transparent
    }
    val content = if (focused) CanopyColors.Chrome else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .tvClickable(TvFocusStyle.NavItem, shape, onFocus = onFocus, onFocusChange = { focused = it }) { onFocus() }
            .clip(shape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(category.label, color = content, style = TvTokens.tvBody.copy(fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium))
    }
}

@Composable
private fun SettingCell(item: TvSettingItem, onContentFocus: () -> Unit) {
    when (item) {
        is TvSettingItem.Header -> Text(
            item.text.uppercase(),
            style = TvTokens.tvEyebrow.copy(color = Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        is TvSettingItem.Toggle -> TvSettingCard(item.title, item.subtitle, onContentFocus, onClick = { item.onChange(!item.checked) }) { focused ->
            TvSwitch(item.checked, focused)
        }
        is TvSettingItem.Choice -> TvSettingCard(item.title, null, onContentFocus, onClick = item.onNext) { focused ->
            Text(
                "‹  ${item.value}  ›",
                style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold),
                color = if (focused) CanopyColors.Chrome else LocalAccent.current,
            )
        }
        is TvSettingItem.Action -> TvSettingCard(item.title, item.subtitle, onContentFocus, onClick = item.onClick, destructive = item.destructive) { focused ->
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = if (focused) CanopyColors.Chrome else Color.White.copy(alpha = 0.7f),
            )
        }
        is TvSettingItem.Swatches -> TvSwatchCard(item, onContentFocus)
    }
}

/** Glass card: inverts to white on focus like every other TV control (Spec §5). */
@Composable
private fun TvSettingCard(
    title: String,
    subtitle: String?,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
    destructive: Boolean = false,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(TvTokens.SettingsCardRadius)
    var focused by remember { mutableStateOf(false) }
    val titleColor = when {
        focused -> CanopyColors.Chrome
        destructive -> Color(0xFFFF8A80)
        else -> Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .tvClickable(TvFocusStyle.Card, shape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(shape)
            .background(if (focused) CanopyColors.Text else TvTokens.GlassSurface)
            .border(1.dp, if (focused) Color.Transparent else TvTokens.GlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = TvTokens.tvMeta,
                    color = if (focused) CanopyColors.Chrome.copy(alpha = 0.7f) else TvTokens.tvMeta.color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing(focused)
    }
}

@Composable
private fun TvSwitch(checked: Boolean, focused: Boolean) {
    val accent = LocalAccent.current
    val track = when {
        checked && focused -> CanopyColors.Chrome
        checked -> accent
        focused -> CanopyColors.Chrome.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.22f)
    }
    Box(
        Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(50)).background(track).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (checked && focused) CanopyColors.Text else Color.White))
    }
}

@Composable
private fun TvSwatchCard(item: TvSettingItem.Swatches, onContentFocus: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TvTokens.SettingsCardRadius))
            .background(TvTokens.GlassSurface)
            .border(1.dp, TvTokens.GlassBorder, RoundedCornerShape(TvTokens.SettingsCardRadius))
            .padding(16.dp),
    ) {
        Text(item.title, style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item.swatches.forEach { argb ->
                Swatch(argb, argb == item.selected, onContentFocus) { item.onSelect(argb) }
            }
        }
    }
}

@Composable
private fun Swatch(argb: Int?, selected: Boolean, onContentFocus: () -> Unit, onClick: () -> Unit) {
    val color = argb?.let { Color(it) } ?: CanopyColors.Accent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(38.dp)
                .tvClickable(TvFocusStyle.Key, CircleShape, onFocus = onContentFocus, onClick = onClick)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, "Seleccionado", tint = Color(0xFF12101A), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(if (argb == null) "Tema" else " ", style = TvTokens.tvMeta)
    }
}
