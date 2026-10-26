// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.ProvisionException
import dev.sfg.orchard.mobile.model.LyricTranslationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSessionApplierTest {

    private class Harness(maxConnectRetries: Int = PairingSessionApplier.MAX_CONNECT_RETRIES) {
        val order = mutableListOf<PairingService>()
        val connectCalls = mutableListOf<String>()
        var failYoutube: Exception? = null
        var failQobuz: Exception? = null
        var connectError: ((Int) -> Exception?)? = null

        val applier = PairingSessionApplier(
            applyYouTube = { order += PairingService.YOUTUBE; failYoutube?.let { throw it } },
            applySpotify = { order += PairingService.SPOTIFY },
            applyQobuz = { order += PairingService.QOBUZ; failQobuz?.let { throw it } },
            applyTranslationKey = { order += PairingService.TRANSLATION_KEYS },
            redeemConnect = { code, _, _ ->
                order += PairingService.ORCHARD_CONNECT
                connectCalls += code
                connectError?.invoke(connectCalls.size)?.let { throw it }
            },
            maxConnectRetries = maxConnectRetries,
        )
    }

    private fun payload() = PairingSessionPayload(
        youtube = PairingYoutubeSession(cookie = TEST_COOKIE),
        spotify = PairingSpotifySession("spdc"),
        qobuz = PairingQobuzSession("token", 123L),
        translationKeys = listOf(PairingTranslationKey(LyricTranslationProvider.GEMINI, "key")),
        orchardConnect = PairingConnectGrant(PairingCodec.encode(ByteArray(32)), "a@b.c", "Ana"),
    )

    @Test
    fun appliesServicesInOrder() = runBlocking {
        val harness = Harness()
        val result = harness.applier.apply(payload(), ByteArray(32))

        assertEquals(
            listOf(PairingService.YOUTUBE, PairingService.SPOTIFY, PairingService.QOBUZ, PairingService.TRANSLATION_KEYS, PairingService.ORCHARD_CONNECT),
            harness.order,
        )
        assertEquals(5, result.results.size)
        assertTrue(result.failed().isEmpty())
    }

    @Test
    fun failureIsIsolatedPerService() = runBlocking {
        val harness = Harness()
        harness.failQobuz = IllegalArgumentException("qobuz down")
        val result = harness.applier.apply(payload(), ByteArray(32))

        assertEquals(5, result.results.size)
        assertEquals(PairingApplyStatus.Failed("qobuz down"), result.statusFor(PairingService.QOBUZ))
        assertTrue(result.statusFor(PairingService.YOUTUBE) is PairingApplyStatus.Success)
        assertTrue(result.statusFor(PairingService.SPOTIFY) is PairingApplyStatus.Success)
        assertTrue(result.statusFor(PairingService.TRANSLATION_KEYS) is PairingApplyStatus.Success)
        assertTrue(result.statusFor(PairingService.ORCHARD_CONNECT) is PairingApplyStatus.Success)
        assertEquals(1, result.failed().size)
    }

    @Test
    fun connectExpiredIsNotRetried() = runBlocking {
        val harness = Harness()
        harness.connectError = { ProvisionException("El código de Orchard Connect expiró o ya se usó", retriable = false) }
        val result = harness.applier.apply(payload(), ByteArray(32))

        assertEquals(1, harness.connectCalls.size)
        assertTrue(result.statusFor(PairingService.ORCHARD_CONNECT) is PairingApplyStatus.Failed)
    }

    @Test
    fun connectTransientErrorRetriesUpToTwoTimes() = runBlocking {
        val harness = Harness()
        harness.connectError = { attempt -> if (attempt < 3) ProvisionException("Sin conexión", retriable = true) else null }
        val result = harness.applier.apply(payload(), ByteArray(32))

        assertEquals(3, harness.connectCalls.size)
        assertTrue(result.statusFor(PairingService.ORCHARD_CONNECT) is PairingApplyStatus.Success)
    }

    @Test
    fun connectTransientErrorExhaustsRetries() = runBlocking {
        val harness = Harness()
        harness.connectError = { ProvisionException("Sin conexión", retriable = true) }
        val result = harness.applier.apply(payload(), ByteArray(32))

        assertEquals(3, harness.connectCalls.size)
        assertTrue(result.statusFor(PairingService.ORCHARD_CONNECT) is PairingApplyStatus.Failed)
    }

    @Test
    fun bindingIsWipedAfterApply() = runBlocking {
        val harness = Harness()
        val binding = ByteArray(32) { 7 }
        harness.applier.apply(payload(), binding)

        assertArrayEquals(ByteArray(32), binding)
    }

    @Test
    fun absentServicesProduceNoResults() = runBlocking {
        val harness = Harness()
        val result = harness.applier.apply(PairingSessionPayload(youtube = PairingYoutubeSession(cookie = TEST_COOKIE)), ByteArray(32))

        assertEquals(1, result.results.size)
        assertEquals(PairingService.YOUTUBE, result.results.single().service)
    }
}
