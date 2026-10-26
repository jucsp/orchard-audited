// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.playback.CastTrustPrompt

/**
 * The only path that can replace a Cast pin (CAST-ROT-INV-3). Focus starts on "Cancelar" so a
 * stray D-Pad OK never re-trusts, and dismissing counts as cancel. Fingerprints are 8 hex chars.
 */
@Composable
fun CastTrustChangedDialog(
    prompt: CastTrustPrompt,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    val device = prompt.device
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (prompt.isDeviceKey) "La identidad de ${device.name} cambió"
                else "No se pudo verificar ${device.name}",
            )
        },
        text = {
            Column {
                device.model?.takeIf { it.isNotBlank() }?.let {
                    Text("Modelo: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Fingerprint("Huella anterior", prompt.previousPinShort)
                Fingerprint("Huella nueva", prompt.newPinShort)
                Spacer(Modifier.height(12.dp))
                Text(explanation(prompt))
                Spacer(Modifier.height(8.dp))
                Text("Si no reiniciaste ni cambiaste este parlante, cancela.")
            }
        },
        dismissButton = {
            // Composed inside the dialog window, where the requester is attached.
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancelar") }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confiar en este dispositivo") }
        },
    )
}

@Composable
private fun Fingerprint(label: String, pin: String?) {
    if (pin == null) return
    Text(
        "$label: $pin",
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

private fun explanation(prompt: CastTrustPrompt): String = when {
    prompt.isDeviceKey ->
        "El parlante presentó otra clave de dispositivo. Pasa tras un restablecimiento de fábrica " +
            "o si otro equipo se hace pasar por él."
    prompt.reason == NO_RESPONSE_REASON ->
        "El parlante no respondió a la verificación de identidad. Si confías, quedará marcado como " +
            "\"Sin autenticación de dispositivo\"."
    else -> "La respuesta no pudo verificarse; podría ser otro equipo suplantando al parlante."
}

/** Reason set by `CastSession` when `deviceauth` times out. */
private const val NO_RESPONSE_REASON = "no deviceauth response"
