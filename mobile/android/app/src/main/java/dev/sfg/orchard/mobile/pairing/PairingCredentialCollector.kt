// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.ProvisionCode
import dev.sfg.orchard.mobile.auth.YouTubeSession
import dev.sfg.orchard.mobile.qobuz.QobuzSession

/** Services the QR pairing can hand to a TV, in the order the TV applies them. */
enum class PairingService { YOUTUBE, SPOTIFY, QOBUZ, TRANSLATION_KEYS, ORCHARD_CONNECT }

/**
 * One consent row (PAIR-CONN-INV-3): what is sent and which account, never the secret itself.
 * [selectedByDefault] follows D3: everything on except BYOK keys, which carry billing.
 */
data class PairingOffer(val service: PairingService, val title: String, val account: String, val selectedByDefault: Boolean)

/** Signed-in Orchard Connect identity shown in the dialog and on the TV. */
data class ConnectIdentity(val email: String, val name: String)

/** Reads what this phone has signed in. Each reader returns null or empty when the service is off. */
class PairingCredentialSources(
    val youtube: () -> YouTubeSession?,
    val spotifySpdc: () -> String?,
    val connect: () -> ConnectIdentity?,
    val qobuz: () -> QobuzSession?,
    val translationKeys: () -> List<PairingTranslationKey>,
    /** Asks the account Worker for a one-time code; only called after the user confirmed. */
    val requestProvisionCode: suspend (binding: ByteArray) -> ProvisionCode,
)

/**
 * Phone side of HU-ORCH-21: lists what can be sent and, once the user confirms, builds the `v=2`
 * payload with only the selected services. The Orchard Connect code is requested last, after
 * consent, and bound to this pairing.
 */
class PairingCredentialCollector(private val sources: PairingCredentialSources) {

    fun offers(): List<PairingOffer> = buildList {
        sources.youtube()?.let {
            add(PairingOffer(PairingService.YOUTUBE, "YouTube Music", it.displayName.ifBlank { "Cuenta activa" }, true))
        }
        sources.connect()?.let {
            add(PairingOffer(PairingService.ORCHARD_CONNECT, "Orchard Connect", it.email.ifBlank { it.name }, true))
        }
        if (!sources.spotifySpdc().isNullOrBlank()) {
            add(PairingOffer(PairingService.SPOTIFY, "Spotify", "Canvas y letras", true))
        }
        sources.qobuz()?.let {
            add(PairingOffer(PairingService.QOBUZ, "Qobuz", "usuario ${it.userId.toString().take(4)}…", true))
        }
        val keys = sources.translationKeys()
        if (keys.isNotEmpty()) {
            add(
                PairingOffer(
                    PairingService.TRANSLATION_KEYS,
                    "Claves de traducción",
                    keys.joinToString { it.provider.label },
                    selectedByDefault = false,
                ),
            )
        }
    }

    /**
     * Payload with only [selected] services. Throws when nothing selected is still available, and
     * propagates a provisioning failure so the screen can offer to send without Connect.
     */
    suspend fun buildPayload(selected: Set<PairingService>, binding: () -> ByteArray): PairingSessionPayload {
        val youtube = sources.youtube().takeIf { PairingService.YOUTUBE in selected }
        val spdc = sources.spotifySpdc()?.takeIf { it.isNotBlank() && PairingService.SPOTIFY in selected }
        val qobuz = sources.qobuz().takeIf { PairingService.QOBUZ in selected }
        val keys = if (PairingService.TRANSLATION_KEYS in selected) sources.translationKeys() else emptyList()
        val connect = sources.connect().takeIf { PairingService.ORCHARD_CONNECT in selected }?.let { identity ->
            val bindingBytes = binding()
            val code = try {
                sources.requestProvisionCode(bindingBytes)
            } finally {
                PairingCrypto.wipe(bindingBytes)
            }
            PairingConnectGrant(
                code.code,
                identity.email.take(PairingSessionPayload.MAX_CONNECT_TEXT),
                identity.name.take(PairingSessionPayload.MAX_CONNECT_TEXT),
            )
        }
        val payload = PairingSessionPayload(
            youtube = youtube?.let(PairingSessionPayload::youtubeOf),
            spotify = spdc?.let(::PairingSpotifySession),
            orchardConnect = connect,
            qobuz = qobuz?.let { PairingQobuzSession(it.token, it.userId) },
            translationKeys = keys.take(PairingSessionPayload.MAX_TRANSLATION_KEYS),
        )
        if (payload.isEmpty) throw PairingProtocolException("nothing selected to send")
        return payload
    }
}
