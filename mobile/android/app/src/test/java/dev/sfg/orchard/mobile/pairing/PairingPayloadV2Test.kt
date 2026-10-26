// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.model.LyricTranslationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PairingPayloadV2Test {

    private fun validCode(): String = PairingCodec.encode(ByteArray(32) { it.toByte() })

    private fun fullPayload() = PairingSessionPayload(
        youtube = PairingYoutubeSession(
            cookie = TEST_COOKIE,
            visitorData = "visitor",
            dataSyncId = "sync",
            accountIndex = 1,
            displayName = "Ana",
            avatarUrl = "https://example.invalid/a.png",
        ),
        spotify = PairingSpotifySession("spdc-secret"),
        orchardConnect = PairingConnectGrant(validCode(), "ana@example.com", "Ana"),
        qobuz = PairingQobuzSession("qobuz-token", 123456L),
        translationKeys = listOf(PairingTranslationKey(LyricTranslationProvider.GEMINI, "api-key-secret")),
    )

    @Test
    fun roundTripsAllBlocks() {
        val parsed = PairingSessionPayload.fromJson(fullPayload().toJson())
        assertEquals(TEST_COOKIE, parsed.youtube?.cookie)
        assertEquals("spdc-secret", parsed.spotify?.spDc)
        assertEquals("ana@example.com", parsed.orchardConnect?.email)
        assertEquals("qobuz-token", parsed.qobuz?.token)
        assertEquals(123456L, parsed.qobuz?.userId)
        assertEquals(LyricTranslationProvider.GEMINI, parsed.translationKeys.single().provider)
        assertEquals("api-key-secret", parsed.translationKeys.single().key)
    }

    @Test
    fun connectOnlyBlockRoundTrips() {
        val payload = PairingSessionPayload(orchardConnect = PairingConnectGrant(validCode(), "a@b.c", "Ana"))
        val parsed = PairingSessionPayload.fromJson(payload.toJson())
        assertEquals(payload.orchardConnect?.provisionCode, parsed.orchardConnect?.provisionCode)
        assertNull(parsed.youtube)
        assertNull(parsed.spotify)
        assertNull(parsed.qobuz)
        assertTrue(parsed.translationKeys.isEmpty())
    }

    @Test
    fun emptyPayloadIsRejected() {
        expectProtocolError { PairingSessionPayload.fromJson("""{"v":2}""") }
        try {
            PairingSessionPayload().toJson()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun rejectsUnknownTranslationProviderAndLocal() {
        val base = JSONObject(fullPayload().toJson())
        base.put("translationKeys", JSONArray().put(JSONObject().put("provider", "unknown").put("key", "k")))
        expectProtocolError { PairingSessionPayload.fromJson(base.toString()) }

        base.put("translationKeys", JSONArray().put(JSONObject().put("provider", "local").put("key", "k")))
        expectProtocolError { PairingSessionPayload.fromJson(base.toString()) }
    }

    @Test
    fun rejectsDuplicateTranslationProviders() {
        val base = JSONObject(fullPayload().toJson())
        base.put(
            "translationKeys",
            JSONArray()
                .put(JSONObject().put("provider", "gemini").put("key", "a"))
                .put(JSONObject().put("provider", "gemini").put("key", "b")),
        )
        expectProtocolError { PairingSessionPayload.fromJson(base.toString()) }
    }

    @Test
    fun rejectsShortProvisionCode() {
        val base = JSONObject(fullPayload().toJson())
        base.getJSONObject("orchardConnect").put("provisionCode", PairingCodec.encode(ByteArray(31)))
        expectProtocolError { PairingSessionPayload.fromJson(base.toString()) }
    }

    @Test
    fun rejectsNonPositiveUserId() {
        val base = JSONObject(fullPayload().toJson())
        base.getJSONObject("qobuz").put("userId", 0)
        expectProtocolError { PairingSessionPayload.fromJson(base.toString()) }
    }

    @Test
    fun v1PayloadIsStillAccepted() {
        val v1 = JSONObject()
            .put("v", 1)
            .put("youtube", JSONObject().put("cookie", TEST_COOKIE).put("displayName", "Ana"))
            .put("spotify", JSONObject().put("spDc", "spdc"))
            .toString()
        val parsed = PairingSessionPayload.fromJson(v1)
        assertEquals(TEST_COOKIE, parsed.youtube?.cookie)
        assertEquals("spdc", parsed.spotify?.spDc)
        assertNull(parsed.orchardConnect)
        assertNull(parsed.qobuz)
    }

    @Test
    fun rejectsUnknownVersionAndUnknownFields() {
        expectProtocolError { PairingSessionPayload.fromJson(JSONObject(fullPayload().toJson()).put("v", 3).toString()) }
        expectProtocolError { PairingSessionPayload.fromJson(JSONObject(fullPayload().toJson()).put("extra", true).toString()) }
    }

    @Test
    fun toStringAndJsonNeverExposeSecrets() {
        val payload = fullPayload()
        val provisionCode = payload.orchardConnect!!.provisionCode
        val secrets = listOf("abc123secret", "sidsecret", "spdc-secret", "qobuz-token", "api-key-secret", provisionCode)

        secrets.forEach { secret -> assertFalse("payload toString leaks $secret", payload.toString().contains(secret)) }
        assertFalse(payload.youtube!!.toString().contains("abc123secret"))
        assertFalse(payload.spotify!!.toString().contains("spdc-secret"))
        assertFalse(payload.qobuz!!.toString().contains("qobuz-token"))
        assertFalse(payload.orchardConnect!!.toString().contains(provisionCode))

        val json = payload.toJson()
        assertFalse(json.contains("refresh_token"))
        assertFalse(json.contains("access_token"))
    }
}
