// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.social.PartyPeer
import dev.sfg.orchard.mobile.social.PartyRole
import dev.sfg.orchard.mobile.social.PartyState
import dev.sfg.orchard.mobile.social.PartyStatus
import dev.sfg.orchard.mobile.social.cleanRoomCode
import dev.sfg.orchard.mobile.ui.screens.ROOM_CODE_LENGTH
import dev.sfg.orchard.mobile.ui.screens.copyToClipboard
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val KeyShape = RoundedCornerShape(TvTokens.KeyboardKeyRadius)
private val PillShape = RoundedCornerShape(50)
private val CodeShape = RoundedCornerShape(12.dp)

private val codeAlphabet = ('A'..'Z').map(Char::toString) + ('2'..'9').map(Char::toString)

/**
 * TV screen for Listening Party: large room code, on-screen code keyboard, join/create and roster.
 * Reachable from the now-playing controls (TvRoutes.LISTENING_PARTY).
 */
@Composable
fun TvListeningPartyScreen(
    party: PartyState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var code by rememberSaveable { mutableStateOf("") }

    Box(
        modifier
            .fillMaxSize()
            .background(CanopyColors.Chrome.copy(alpha = 0.85f)),
    ) {
        TvSafeContent(onContentFocus) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Groups, null, tint = LocalAccent.current, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Listening Party", style = TvTokens.tvHeadline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PartyPill("Volver", onBack)
                }

                Spacer(Modifier.height(18.dp))

                if (party.isActive) {
                    ActiveParty(party, onLeave)
                } else {
                    InactiveParty(code, { code = it }, onCreate, onJoin)
                }
            }
        }
    }
}

@Composable
private fun InactiveParty(
    code: String,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
) {
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Escucha la misma música en varios dispositivos. Introduce un código de sala o crea una nueva.",
            style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
        )
        Spacer(Modifier.height(18.dp))
        RoomCodeDisplay(code.ifBlank { "      " })
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            codeAlphabet.chunked(10).forEachIndexed { rowIndex, rowChars ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowChars.forEach { ch ->
                        PartyKey(ch, first = rowIndex == 0 && ch == "A", keyFocus) {
                            onCodeChange(cleanRoomCode(code + ch).take(ROOM_CODE_LENGTH))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartyKey("Borrar", first = false, null) { onCodeChange(code.dropLast(1)) }
                PartyPill("Crear sala", onCreate)
                PartyPill(
                    "Unirse",
                    enabled = code.length == ROOM_CODE_LENGTH,
                ) { onJoin(code) }
            }
        }
    }
}

@Composable
private fun ActiveParty(party: PartyState, onLeave: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val statusText = when (party.status) {
        PartyStatus.CONNECTING -> "Conectando con la sala…"
        PartyStatus.OFFLINE -> "Reconectando…"
        else -> if (party.isHost) "Eres el anfitrión · todos siguen tu cola" else "Sincronizado · siguiendo al anfitrión"
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            statusText,
            style = TvTokens.tvBody.copy(color = if (party.status == PartyStatus.CONNECTED) accent else CanopyColors.MutedStrong),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text("CÓDIGO DE SALA", style = TvTokens.tvEyebrow)
        Spacer(Modifier.height(8.dp))
        RoomCodeDisplay(party.code)
        Spacer(Modifier.height(14.dp))
        if (party.code.isNotBlank()) {
            PartyPill("Copiar código") {
                copyToClipboard(context, party.code, label = "Orchard Room Code")
                Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            when (party.peers.size) {
                0 -> "Aún no hay otros oyentes en la sala"
                1 -> "1 otro oyente"
                else -> "${party.peers.size} otros oyentes"
            },
            style = TvTokens.tvShelf.copy(color = CanopyColors.Muted),
        )
        Spacer(Modifier.height(10.dp))
        party.peers.forEach { peer ->
            Spacer(Modifier.height(6.dp))
            PartyPeerRow(peer)
        }
        Spacer(Modifier.weight(1f))
        PartyPill(
            if (party.isHost) "Cerrar sala" else "Salir de la sala",
            onLeave,
            danger = true,
        )
    }
}

@Composable
private fun RoomCodeDisplay(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        code.padEnd(ROOM_CODE_LENGTH, ' ').take(ROOM_CODE_LENGTH).forEach { ch ->
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CodeShape)
                    .background(CanopyColors.Glass.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch.toString(),
                    style = TvTokens.tvDisplay.copy(fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PartyKey(
    label: String,
    first: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val focusModifier = if (first && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        focusModifier
            .height(40.dp)
            .tvClickable(TvFocusStyle.Key, KeyShape, onClick = onClick)
            .clip(KeyShape)
            .background(CanopyColors.Glass.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvBody.copy(color = CanopyColors.Text), maxLines = 1)
    }
}

@Composable
private fun PartyPill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val accent = LocalAccent.current
    val container = when {
        !enabled -> CanopyColors.Glass.copy(alpha = 0.08f)
        danger -> CanopyColors.Danger.copy(alpha = 0.16f)
        else -> accent
    }
    val content = when {
        !enabled -> CanopyColors.Muted
        danger -> CanopyColors.Danger
        else -> Color.Black
    }
    Box(
        Modifier
            .height(40.dp)
            .tvClickable(TvFocusStyle.Button, PillShape, enabled = enabled, onClick = onClick)
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TvTokens.tvBody.copy(color = content, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun PartyPeerRow(peer: PartyPeer) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CanopyColors.Glass.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(if (peer.open) accent else CanopyColors.Muted),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            peer.name.ifBlank { "Listener" },
            style = TvTokens.tvBody.copy(color = CanopyColors.Text, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (peer.role == PartyRole.HOST) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(CircleShape).background(accent).padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("HOST", style = TvTokens.tvMeta.copy(color = Color.Black, fontWeight = FontWeight.Bold))
            }
        }
    }
}
