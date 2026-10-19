// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicVideo
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val MenuShape = RoundedCornerShape(18.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/**
 * "⋯ Más opciones" of the player. Holds what used to be the secondary row (video, Listening Party)
 * plus night mode and the track actions. Focus is trapped in the dialog; Back closes it.
 */
@Composable
fun TvPlayerMenu(
    hasVideo: Boolean,
    onDismiss: () -> Unit,
    onNightMode: () -> Unit,
    onOpenMusicVideo: () -> Unit,
    onOpenListeningParty: () -> Unit,
    onTrackActions: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(MenuShape)
                .background(Color(0xFF14121C).copy(alpha = 0.96f))
                .border(1.dp, TvTokens.GlassBorder, MenuShape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Más opciones", style = TvTokens.tvShelf, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 6.dp))
            MenuItem(Icons.Rounded.Bedtime, "Modo nocturno", "Pantalla negra con un mini reproductor", onNightMode, Modifier.focusRequester(first))
            if (hasVideo) MenuItem(Icons.Rounded.MusicVideo, "Ver video musical", null, onOpenMusicVideo)
            MenuItem(Icons.Rounded.Groups, "Listening Party", null, onOpenListeningParty)
            MenuItem(Icons.Rounded.MoreVert, "Acciones de la canción", "Agregar a playlist, a la cola…", onTrackActions)
            Spacer(Modifier.height(4.dp))
            MenuItem(Icons.Rounded.Close, "Cerrar", null, onDismiss)
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val content = if (focused) CanopyColors.Chrome else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .tvClickable(TvFocusStyle.Row, ItemShape, onFocusChange = { focused = it }, onClick = onClick)
            .clip(ItemShape)
            .background(if (focused) CanopyColors.Text else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = content, style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold))
            if (hint != null) {
                Text(hint, style = TvTokens.tvMeta.copy(color = if (focused) CanopyColors.Chrome.copy(alpha = 0.7f) else TvTokens.tvMeta.color))
            }
        }
    }
}
