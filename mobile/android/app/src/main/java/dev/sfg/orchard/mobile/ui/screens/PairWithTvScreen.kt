// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.orchard.mobile.auth.ProvisionException
import dev.sfg.orchard.mobile.pairing.PairingClient
import dev.sfg.orchard.mobile.pairing.PairingCredentialCollector
import dev.sfg.orchard.mobile.pairing.PairingCredentialSources
import dev.sfg.orchard.mobile.pairing.PairingDiscovery
import dev.sfg.orchard.mobile.pairing.PairingLinkInbox
import dev.sfg.orchard.mobile.pairing.PairingMessages
import dev.sfg.orchard.mobile.pairing.PairingProtocolException
import dev.sfg.orchard.mobile.pairing.PairingService
import dev.sfg.orchard.mobile.pairing.PairingTv
import dev.sfg.orchard.mobile.pairing.PreparedPairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PairStep {
    data object Idle : PairStep
    data class Connecting(val tvName: String) : PairStep
    data class Confirm(val prepared: PreparedPairing) : PairStep
    data class Sending(val tvName: String) : PairStep
    data class Done(val tvName: String) : PairStep
    data class Failed(val message: String) : PairStep
}

/**
 * Phone side of TV pairing (SDD-tv-qr-pairing §6.3): NSD list for SAS mode, or a validated QR link
 * from [PairingLinkInbox]. The user confirms in [PairConfirmDialog], then [PairingCredentialCollector]
 * builds a `v=2` payload with only the selected services; the Connect code is requested after consent.
 */
@Composable
fun PairWithTvScreen(
    sources: PairingCredentialSources,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember(context) { PairingDiscovery(context) }
    val client = remember { PairingClient() }
    val collector = remember(sources) { PairingCredentialCollector(sources) }
    val offers = remember(sources) { collector.offers() }
    val qrLink by PairingLinkInbox.pending.collectAsStateWithLifecycle()

    var tvs by remember { mutableStateOf<List<PairingTv>>(emptyList()) }
    var scanGeneration by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<PairStep>(PairStep.Idle) }
    val hasAnyService = offers.isNotEmpty()

    BackHandler(onBack = onBack)
    DisposableEffect(discovery) {
        onDispose {
            discovery.close()
            (step as? PairStep.Confirm)?.prepared?.wipe()
        }
    }

    fun connect(host: String, port: Int, tvName: String, mode: String, token: ByteArray?, fingerprint: ByteArray?) {
        discovery.stopDiscovery()
        scanning = false
        step = PairStep.Connecting(tvName)
        scope.launch {
            step = runCatching {
                withContext(Dispatchers.IO) { client.prepare(host, port, mode, token, fingerprint) }
            }.fold(
                onSuccess = { PairStep.Confirm(it) },
                onFailure = { PairStep.Failed(failureMessage(mode)) },
            )
        }
    }

    fun send(prepared: PreparedPairing, selected: Set<PairingService>) {
        step = PairStep.Sending(prepared.tvName)
        scope.launch {
            step = try {
                val payload = withContext(Dispatchers.IO) {
                    collector.buildPayload(selected) { prepared.provisionBinding() }
                }
                withContext(Dispatchers.IO) { client.send(prepared, payload) }
                PairStep.Done(prepared.tvName)
            } catch (error: PairingProtocolException) {
                if (error.errorCode == PairingClient.ERROR_BAD_PAYLOAD) {
                    PairStep.Failed("Actualiza Orchard en la TV para aceptar estos servicios, o elige menos.")
                } else {
                    PairStep.Failed("La TV rechazó la sesión. Genera un código nuevo en la TV y vuelve a intentarlo.")
                }
            } catch (error: ProvisionException) {
                PairStep.Failed(error.message ?: "No se pudo preparar Orchard Connect.")
            } catch (error: Exception) {
                PairStep.Failed("La TV rechazó la sesión. Genera un código nuevo en la TV y vuelve a intentarlo.")
            } finally {
                prepared.wipe()
            }
        }
    }

    LaunchedEffect(qrLink, hasAnyService) {
        if (qrLink == null || !hasAnyService) return@LaunchedEffect
        val info = PairingLinkInbox.take() ?: return@LaunchedEffect
        connect(info.host, info.port, info.tvName, PairingMessages.MODE_QR, info.token, info.fingerprint)
    }

    LaunchedEffect(scanGeneration, hasAnyService) {
        if (!hasAnyService || step !is PairStep.Idle) return@LaunchedEffect
        tvs = emptyList()
        scanning = runCatching { discovery.startDiscovery { tvs = it } }.isSuccess
        delay(PairingDiscovery.DEFAULT_TIMEOUT_MS)
        scanning = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
            Spacer(Modifier.width(8.dp))
            Text("Vincular con Android TV", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        if (!hasAnyService) {
            Text("Inicia sesión en YouTube Music u Orchard Connect en este teléfono para vincular una TV.")
        } else {
            Text(
                "En la TV, abre Orchard › Iniciar sesión. Elige tu TV de la lista o escanea el QR de la TV con la cámara.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            StepStatus(step)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Televisores en tu Wi-Fi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tvs.forEach { tv ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = step is PairStep.Idle || step is PairStep.Failed) {
                                connect(tv.host, tv.port, tv.name, PairingMessages.MODE_SAS, null, null)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Tv, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(tv.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (tvs.isEmpty() && !scanning) {
                    Text(
                        "No se encontró ninguna TV. Revisa que ambos estén en la misma red Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    step = PairStep.Idle
                    scanGeneration++
                },
                enabled = !scanning && step !is PairStep.Connecting && step !is PairStep.Sending,
            ) { Text("Buscar de nuevo") }
        }
    }

    (step as? PairStep.Confirm)?.let { confirm ->
        val prepared = confirm.prepared
        PairConfirmDialog(
            tvName = prepared.tvName,
            offers = offers,
            sas = prepared.sas.takeIf { prepared.mode == PairingMessages.MODE_SAS },
            onConfirm = { selected -> send(prepared, selected) },
            onCancel = {
                prepared.wipe()
                step = PairStep.Idle
            },
        )
    }
}

@Composable
private fun StepStatus(step: PairStep) {
    val text = when (step) {
        PairStep.Idle -> return
        is PairStep.Connecting -> "Conectando con ${step.tvName}…"
        is PairStep.Confirm -> "Confirma la vinculación."
        is PairStep.Sending -> "Enviando sesión cifrada a ${step.tvName}…"
        is PairStep.Done -> "Listo. Confirma el inicio de sesión en ${step.tvName} con el control remoto."
        is PairStep.Failed -> step.message
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
}

private fun failureMessage(mode: String): String = if (mode == PairingMessages.MODE_QR) {
    "No se pudo verificar la TV del código QR. Genera un código nuevo en la TV y vuelve a escanearlo."
} else {
    "No se pudo conectar con la TV. Revisa que ambos estén en la misma red Wi-Fi."
}
