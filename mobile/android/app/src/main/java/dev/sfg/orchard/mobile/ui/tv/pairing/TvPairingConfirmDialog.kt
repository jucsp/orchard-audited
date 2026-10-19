// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val SheetShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)

/**
 * Last gate before a received session is applied on the TV (PAIR-INV-2). Focus starts on
 * "Cancelar" so a stray OK press never signs in, and Back cancels too.
 */
@Composable
fun TvPairingConfirmDialog(
    displayName: String,
    includesSpotify: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Surface(shape = SheetShape, color = CanopyColors.Surface, modifier = Modifier.width(520.dp).wrapContentHeight()) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
                Text(
                    "¿Iniciar sesión como $displayName?",
                    style = TvTokens.tvTitleNowPlaying,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (includesSpotify) {
                        "Tu teléfono envió la cuenta de YouTube Music y la conexión con Spotify."
                    } else {
                        "Tu teléfono envió la cuenta de YouTube Music."
                    },
                    style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DialogPill("Cancelar", primary = false, modifier = Modifier.focusRequester(cancelFocus), onClick = onCancel)
                    DialogPill("Iniciar sesión", primary = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
internal fun DialogPill(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val container = when {
        !enabled -> CanopyColors.Glass.copy(alpha = 0.08f)
        primary -> accent
        else -> CanopyColors.Glass.copy(alpha = 0.14f)
    }
    val content = when {
        !enabled -> CanopyColors.Muted
        primary -> Color.Black
        else -> CanopyColors.Text
    }
    Box(
        modifier
            .height(44.dp)
            .tvClickable(TvFocusStyle.Button, PillShape, enabled = enabled, onClick = onClick)
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvBody.copy(color = content, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}
