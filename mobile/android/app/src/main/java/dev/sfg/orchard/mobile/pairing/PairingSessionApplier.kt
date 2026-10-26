// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.ProvisionException
import kotlinx.coroutines.CancellationException

/** Outcome of applying one service on the TV. */
sealed interface PairingApplyStatus {
    object Success : PairingApplyStatus
    data class Failed(val error: String) : PairingApplyStatus
}

data class PairingServiceResult(val service: PairingService, val status: PairingApplyStatus)

data class PairingApplyResult(val results: List<PairingServiceResult>) {
    fun failed(): List<PairingServiceResult> = results.filter { it.status is PairingApplyStatus.Failed }
    fun statusFor(service: PairingService): PairingApplyStatus? = results.firstOrNull { it.service == service }?.status
}

/**
 * TV side of HU-ORCH-21 §5.5: applies each present block in order and isolates failures per
 * service (PAIR-CONN-INV-8). Pure JVM so the order, retry and isolation logic run in unit tests.
 * The one-time Connect code is redeemed last, retried only on transient failures and never after a
 * definitive Worker answer; the [binding] buffer is wiped once applied (PAIR-CONN-INV-6).
 */
class PairingSessionApplier(
    private val applyYouTube: suspend (PairingYoutubeSession) -> Unit,
    private val applySpotify: suspend (PairingSpotifySession) -> Unit,
    private val applyQobuz: suspend (PairingQobuzSession) -> Unit,
    private val applyTranslationKey: suspend (PairingTranslationKey) -> Unit,
    private val redeemConnect: suspend (code: String, binding: ByteArray, deviceName: String) -> Unit,
    private val deviceName: () -> String = { "Android TV" },
    private val maxConnectRetries: Int = MAX_CONNECT_RETRIES,
) {

    suspend fun apply(payload: PairingSessionPayload, binding: ByteArray): PairingApplyResult {
        val results = mutableListOf<PairingServiceResult>()
        try {
            payload.youtube?.let { block ->
                results += runService(PairingService.YOUTUBE) { applyYouTube(block) }
            }
            payload.spotify?.let { block ->
                results += runService(PairingService.SPOTIFY) { applySpotify(block) }
            }
            payload.qobuz?.let { block ->
                results += runService(PairingService.QOBUZ) { applyQobuz(block) }
            }
            if (payload.translationKeys.isNotEmpty()) {
                results += runService(PairingService.TRANSLATION_KEYS) {
                    for (key in payload.translationKeys) applyTranslationKey(key)
                }
            }
            payload.orchardConnect?.let { grant ->
                results += applyConnect(grant, binding)
            }
            return PairingApplyResult(results)
        } finally {
            PairingCrypto.wipe(binding)
        }
    }

    private suspend fun applyConnect(grant: PairingConnectGrant, binding: ByteArray): PairingServiceResult {
        var attempts = 0
        while (true) {
            attempts++
            try {
                redeemConnect(grant.provisionCode, binding, deviceName())
                return PairingServiceResult(PairingService.ORCHARD_CONNECT, PairingApplyStatus.Success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val retriable = error is ProvisionException && error.retriable
                if (!retriable || attempts > maxConnectRetries) {
                    return PairingServiceResult(
                        PairingService.ORCHARD_CONNECT,
                        PairingApplyStatus.Failed(error.message ?: "Orchard Connect could not be activated"),
                    )
                }
            }
        }
    }

    private suspend fun runService(service: PairingService, block: suspend () -> Unit): PairingServiceResult = try {
        block()
        PairingServiceResult(service, PairingApplyStatus.Success)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        PairingServiceResult(service, PairingApplyStatus.Failed(error.message ?: "unknown error"))
    }

    companion object {
        /** Maximum retries after the first attempt (SDD §5.5: "se reintenta como máximo 2 veces"). */
        const val MAX_CONNECT_RETRIES = 2
    }
}
