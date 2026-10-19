// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The phone's consent gate (PAIR-INV-2): nothing secret is sent until "Vincular". Spotify is
 * opt-in, hidden without a `sp_dc`, and in SAS mode the user compares [sas] with the TV.
 */
@Composable
fun PairConfirmDialog(
    tvName: String,
    accountName: String,
    hasSpotify: Boolean,
    sas: String?,
    onConfirm: (includeSpotify: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var includeSpotify by remember { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Vincular con $tvName") },
        text = {
            Column {
                Text("Se enviará la cuenta $accountName de YouTube Music a esta TV.")
                if (sas != null) {
                    Spacer(Modifier.height(14.dp))
                    Text("¿La TV muestra este mismo código?", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sas,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        ),
                    )
                }
                if (hasSpotify) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeSpotify, onCheckedChange = { includeSpotify = it })
                        Spacer(Modifier.width(4.dp))
                        Text("Incluir Spotify")
                    }
                }
            }
        },
        dismissButton = {
            // Composed inside the dialog window, where the requester is attached.
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancelar") }
        },
        confirmButton = {
            Button(onClick = { onConfirm(includeSpotify && hasSpotify) }) {
                Text(if (sas != null) "Coincide, vincular" else "Vincular")
            }
        },
    )
}
