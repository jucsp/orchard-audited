// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.pairing

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sfg.orchard.mobile.pairing.PairingAdvertiser
import dev.sfg.orchard.mobile.pairing.PairingServerState
import dev.sfg.orchard.mobile.pairing.PairingSessionPayload
import dev.sfg.orchard.mobile.pairing.PairingUri
import dev.sfg.orchard.mobile.pairing.QrMatrix
import dev.sfg.orchard.mobile.pairing.TvPairingServer
import dev.sfg.orchard.mobile.pairing.ZxingQrMatrixEncoder
import dev.sfg.orchard.mobile.pairing.localTvIpv4
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class PairPhase { Starting, Waiting, Comparing, Received, Expired, NoNetwork }

private val QrShape = RoundedCornerShape(12.dp)
private val QrSide = 360.dp

/**
 * TV sign-in by phone (SDD-tv-qr-pairing §6.2). Each generation owns a fresh [TvPairingServer] and
 * NSD announcement; both are torn down when the generation ends, on ON_STOP and on dispose
 * (PAIR-INV-4). The received session is applied only after [TvPairingConfirmDialog] (PAIR-INV-2).
 */
@Composable
fun TvQrLoginScreen(
    onSession: (PairingSessionPayload) -> Unit,
    onUseWebLogin: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val advertiser = remember(context) { PairingAdvertiser(context) }
    val encoder = remember { ZxingQrMatrixEncoder() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val tvName = remember { "Orchard TV ${Build.MODEL.orEmpty()}".trim().take(40) }

    var generation by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var phase by remember { mutableStateOf(PairPhase.Starting) }
    var qr by remember { mutableStateOf<QrMatrix?>(null) }
    var sasCode by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var received by remember { mutableStateOf<PairingSessionPayload?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            advertiser.stop()
        }
    }

    // Cancelling this effect (new generation, ON_STOP, dispose) is what closes the server.
    LaunchedEffect(generation, started) {
        if (!started) return@LaunchedEffect
        phase = PairPhase.Starting
        qr = null
        sasCode = null
        received = null
        val address = withContext(Dispatchers.IO) { localTvIpv4(context) }
        if (address == null) {
            phase = PairPhase.NoNetwork
            return@LaunchedEffect
        }
        val gen = generation
        var closingByScreen = false
        val listener = object : TvPairingServer.Listener {
            override fun onState(state: PairingServerState) {
                main.post {
                    if (gen != generation || closingByScreen) return@post
                    if (state == PairingServerState.Waiting && phase == PairPhase.Comparing) {
                        phase = PairPhase.Waiting
                        sasCode = null
                    }
                    // Five failures: fresh material and a new QR (SDD §5.5).
                    if (state == PairingServerState.Closed && phase != PairPhase.Received) {
                        generation += 1
                    }
                }
            }

            override fun onSas(sas: String) {
                main.post {
                    if (gen != generation || closingByScreen) return@post
                    phase = PairPhase.Comparing
                    sasCode = sas
                }
            }

            override fun onSessionReceived(payload: PairingSessionPayload) {
                main.post {
                    if (gen != generation || closingByScreen) return@post
                    advertiser.stop()
                    phase = PairPhase.Received
                    received = payload
                }
            }
        }
        // Built on this thread so a cancellation can never orphan an open socket.
        val server = runCatching { TvPairingServer(address, tvName, listener) }.getOrElse {
            phase = PairPhase.NoNetwork
            return@LaunchedEffect
        }
        try {
            val link = PairingUri.build(address.hostAddress.orEmpty(), server.port, server.token, server.fingerprint, tvName)
            qr = withContext(Dispatchers.Default) { encoder.encode(link) }
            advertiser.start(server.port)
            phase = PairPhase.Waiting
            val deadline = SystemClock.elapsedRealtime() + TvPairingServer.MAX_LIFETIME_MS
            while (phase != PairPhase.Received) {
                val left = deadline - SystemClock.elapsedRealtime()
                remainingSeconds = ((left + 999) / 1000).toInt().coerceAtLeast(0)
                if (left <= 0) {
                    phase = PairPhase.Expired
                    qr = null
                    break
                }
                delay(250)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            qr = null
            phase = PairPhase.NoNetwork
        } finally {
            closingByScreen = true
            advertiser.stop()
            server.close()
        }
    }

    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        QrPanel(qr, phase)
        Spacer(Modifier.width(40.dp))
        PairInstructions(
            phase = phase,
            tvName = tvName,
            sas = sasCode,
            remainingSeconds = remainingSeconds,
            onRegenerate = { generation++ },
            onUseWebLogin = onUseWebLogin,
        )
    }

    received?.let { payload ->
        TvPairingConfirmDialog(
            displayName = payload.youtube.displayName.ifBlank { "YouTube Music" },
            includesSpotify = payload.spotify != null,
            onConfirm = {
                received = null
                onSession(payload)
            },
            onCancel = {
                received = null
                generation++
            },
        )
    }
}

/** White card with the QR; the matrix already carries the 4-module quiet zone. */
@Composable
private fun QrPanel(matrix: QrMatrix?, phase: PairPhase) {
    Box(
        Modifier.size(QrSide).clip(QrShape).background(if (matrix != null) Color.White else CanopyColors.Glass.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.minDimension / matrix.size
                for (y in 0 until matrix.size) {
                    for (x in 0 until matrix.size) {
                        if (matrix.isDark(x, y)) {
                            // A hair of overlap keeps sub-pixel seams out of dark runs.
                            drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
            }
        } else {
            Text(
                when (phase) {
                    PairPhase.Expired -> "Código expirado"
                    PairPhase.NoNetwork -> "Sin red local"
                    PairPhase.Received -> "Sesión recibida"
                    else -> "Generando código…"
                },
                style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
            )
        }
    }
}

@Composable
private fun PairInstructions(
    phase: PairPhase,
    tvName: String,
    sas: String?,
    remainingSeconds: Int,
    onRegenerate: () -> Unit,
    onUseWebLogin: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(Modifier.widthIn(max = 420.dp)) {
        Text("Inicia sesión con tu teléfono", style = TvTokens.tvHeadline)
        Spacer(Modifier.height(14.dp))
        Text(
            "1. Abre Orchard en tu teléfono con tu cuenta de YouTube Music.\n" +
                "2. Escanea el código con la cámara, o ve a Ajustes › Conexiones › Vincular con Android TV.\n" +
                "3. Confirma en el teléfono y luego aquí.",
            style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong, lineHeight = 22.sp),
        )
        Spacer(Modifier.height(18.dp))
        Text(tvName, style = TvTokens.tvMeta)
        Spacer(Modifier.height(6.dp))
        Text(statusLine(phase, remainingSeconds), style = TvTokens.tvShelf)
        if (phase == PairPhase.Comparing && sas != null) {
            Spacer(Modifier.height(10.dp))
            Text("Comprueba que tu teléfono muestre este código:", style = TvTokens.tvBody)
            Spacer(Modifier.height(6.dp))
            Text(sas, style = TvTokens.tvDisplay.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp))
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogPill(
                "Generar código nuevo",
                primary = phase == PairPhase.Expired || phase == PairPhase.NoNetwork,
                modifier = Modifier.focusRequester(firstFocus),
                onClick = onRegenerate,
            )
            DialogPill("Iniciar sesión con teclado/mouse", primary = false, onClick = onUseWebLogin)
        }
    }
}

private fun statusLine(phase: PairPhase, remainingSeconds: Int): String = when (phase) {
    PairPhase.Starting -> "Preparando…"
    PairPhase.Waiting -> "Esperando teléfono · %d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
    PairPhase.Comparing -> "Teléfono conectado · %d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
    PairPhase.Received -> "Sesión recibida"
    PairPhase.Expired -> "El código expiró. Genera uno nuevo."
    PairPhase.NoNetwork -> "Conecta la TV a Wi-Fi o Ethernet, o inicia sesión con teclado/mouse."
}
