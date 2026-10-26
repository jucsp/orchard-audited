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
import dev.sfg.orchard.mobile.pairing.PairingOffer
import dev.sfg.orchard.mobile.pairing.PairingService

/**
 * The phone's consent gate (PAIR-INV-2): nothing secret is sent until "Vincular". Every detected
 * service is its own opt-in row; BYOK translation keys default off because they carry billing (D3).
 */
@Composable
fun PairConfirmDialog(
    tvName: String,
    offers: List<PairingOffer>,
    sas: String?,
    onConfirm: (Set<PairingService>) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember(offers) { mutableStateOf(offers.filter { it.selectedByDefault }.map { it.service }.toSet()) }
    val cancelFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Vincular con $tvName") },
        text = {
            Column {
                if (offers.isEmpty()) {
                    Text("No hay cuentas disponibles para vincular en este teléfono. Inicia sesión en YouTube Music u Orchard Connect y vuelve a intentarlo.")
                } else {
                    Text("Se enviarán a la TV los servicios que marques:")
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
                    Spacer(Modifier.height(8.dp))
                    offers.forEach { offer ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = offer.service in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + offer.service else selected - offer.service
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(offer.title)
                                if (offer.account.isNotBlank()) {
                                    Text(offer.account, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
            TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancelar") }
        },
        confirmButton = {
            if (offers.isNotEmpty()) {
                Button(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                    Text(if (sas != null) "Coincide, vincular" else "Vincular")
                }
            }
        },
    )
}
