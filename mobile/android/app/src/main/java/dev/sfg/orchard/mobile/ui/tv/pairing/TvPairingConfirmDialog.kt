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
import dev.sfg.orchard.mobile.pairing.PairingApplyStatus
import dev.sfg.orchard.mobile.pairing.PairingService
import dev.sfg.orchard.mobile.pairing.PairingServiceResult
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val SheetShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)

/**
 * Last gate before a received session is applied on the TV (PAIR-INV-2). Focus starts on
 * "Cancelar" so a stray OK press never signs in, and Back cancels too. Once the phone's `v=2`
 * payload is applied, the same dialog reports per-service success/failure (PAIR-CONN-INV-8).
 */
@Composable
fun TvPairingConfirmDialog(
    displayName: String,
    services: List<String>,
    applying: Boolean,
    results: List<PairingServiceResult>?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Surface(shape = SheetShape, color = CanopyColors.Surface, modifier = Modifier.width(520.dp).wrapContentHeight()) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
                when {
                    results != null -> ResultBody(results, onDismissResult)
                    applying -> ApplyingBody()
                    else -> ConfirmBody(displayName, services, onConfirm, onCancel)
                }
            }
        }
    }
}

@Composable
private fun ConfirmBody(
    displayName: String,
    services: List<String>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    Text(
        "¿Iniciar sesión como $displayName?",
        style = TvTokens.tvTitleNowPlaying,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(10.dp))
    Text("Tu teléfono envió:", style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong))
    services.forEach { service ->
        Text("• $service", style = TvTokens.tvBody)
    }
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DialogPill("Cancelar", primary = false, modifier = Modifier.focusRequester(cancelFocus), onClick = onCancel)
        DialogPill("Iniciar sesión", primary = true, onClick = onConfirm)
    }
}

@Composable
private fun ApplyingBody() {
    Text("Aplicando sesión…", style = TvTokens.tvTitleNowPlaying)
    Spacer(Modifier.height(10.dp))
    Text("Conectando los servicios seleccionados.", style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong))
}

@Composable
private fun ResultBody(
    results: List<PairingServiceResult>,
    onDismissResult: () -> Unit,
) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { continueFocus.requestFocus() }
    Text("Resultado de la vinculación", style = TvTokens.tvTitleNowPlaying, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
    if (results.isEmpty()) {
        Text("No se pudo aplicar la sesión.", style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong))
    } else {
        results.forEach { result ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(serviceLabel(result.service), style = TvTokens.tvBody, modifier = Modifier.weight(1f))
                Text(
                    when (val status = result.status) {
                        PairingApplyStatus.Success -> "Listo"
                        is PairingApplyStatus.Failed -> "Falló · ${status.error}"
                    },
                    style = TvTokens.tvBody.copy(
                        color = if (result.status is PairingApplyStatus.Success) CanopyColors.Text else Color(0xFFFF7777),
                    ),
                )
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DialogPill("Continuar", primary = true, modifier = Modifier.focusRequester(continueFocus), onClick = onDismissResult)
    }
}

private fun serviceLabel(service: PairingService): String = when (service) {
    PairingService.YOUTUBE -> "YouTube Music"
    PairingService.SPOTIFY -> "Spotify"
    PairingService.QOBUZ -> "Qobuz"
    PairingService.TRANSLATION_KEYS -> "Claves de traducción"
    PairingService.ORCHARD_CONNECT -> "Orchard Connect"
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
