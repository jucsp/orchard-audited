// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.YouTubeSession
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal const val TEST_COOKIE = "SAPISID=abc123secret; SID=sidsecret; HSID=hsid"

internal fun testSession(name: String = "Ana") = YouTubeSession(
    cookie = TEST_COOKIE,
    visitorData = "visitor",
    dataSyncId = "sync",
    accountIndex = 1,
    displayName = name,
    avatarUrl = "https://example.invalid/a.png",
)

class PairingMessagesTest {

    @Test
    fun helloRoundTripQr() {
        val token = PairingCrypto.randomBytes(16)
        val commitment = PairingCrypto.randomBytes(32)
        val parsed = PairingMessages.parseHelloRequest(PairingMessages.helloRequestJson(PairingMessages.MODE_QR, token, commitment))
        assertEquals(PairingMessages.MODE_QR, parsed.mode)
        assertArrayEquals(token, parsed.token)
        assertArrayEquals(commitment, parsed.commitment)
    }

    @Test
    fun helloRoundTripSasHasNoToken() {
        val parsed = PairingMessages.parseHelloRequest(PairingMessages.helloRequestJson(PairingMessages.MODE_SAS, null, PairingCrypto.randomBytes(32)))
        assertNull(parsed.token)
        expectProtocolError {
            PairingMessages.parseHelloRequest(
                JSONObject().put("v", 1).put("mode", "sas").put("c", PairingCodec.encode(ByteArray(32)))
                    .put("t", PairingCodec.encode(ByteArray(16))).toString(),
            )
        }
    }

    @Test
    fun helloResponseKeyAndSessionRoundTrip() {
        val sid = PairingCrypto.randomBytes(8)
        val pub = PairingCrypto.generateKeyPair().publicKey
        val nonce = PairingCrypto.randomBytes(16)
        val hello = PairingMessages.parseHelloResponse(PairingMessages.helloResponseJson(sid, pub, nonce, "Orchard TV"))
        assertArrayEquals(sid, hello.sessionId)
        assertArrayEquals(pub, hello.tvPublicKey)
        assertEquals("Orchard TV", hello.name)

        val key = PairingMessages.parseKeyRequest(PairingMessages.keyRequestJson(sid, pub, nonce))
        assertArrayEquals(pub, key.phonePublicKey)
        PairingMessages.parseKeyResponse(PairingMessages.keyResponseJson())

        val sealed = PairingCrypto.randomBytes(12 + 40)
        val session = PairingMessages.parseSessionRequest(PairingMessages.sessionRequestJson(sid, sealed))
        assertArrayEquals(sealed, session.ivAndCiphertext)
        assertEquals("pending_tv_confirm", PairingMessages.parseSessionResponse(PairingMessages.sessionResponseJson("pending_tv_confirm")))
        assertEquals("bad_token", PairingMessages.parseErrorCode(PairingMessages.errorJson("bad_token")))
        assertNull(PairingMessages.parseErrorCode("not json"))
    }

    @Test
    fun rejectsWrongVersionUnknownAndMissingFields() {
        val good = JSONObject(PairingMessages.helloRequestJson(PairingMessages.MODE_SAS, null, ByteArray(32)))
        expectProtocolError { PairingMessages.parseHelloRequest(JSONObject(good.toString()).put("v", 2).toString()) }
        expectProtocolError { PairingMessages.parseHelloRequest(JSONObject(good.toString()).put("extra", 1).toString()) }
        expectProtocolError { PairingMessages.parseHelloRequest(JSONObject(good.toString()).apply { remove("c") }.toString()) }
        expectProtocolError { PairingMessages.parseHelloRequest(JSONObject(good.toString()).put("mode", "nfc").toString()) }
    }

    @Test
    fun rejectsWrongLengths() {
        expectProtocolError { PairingMessages.parseHelloRequest(PairingMessages.helloRequestJson(PairingMessages.MODE_SAS, null, ByteArray(31))) }
        expectProtocolError {
            PairingMessages.parseKeyRequest(PairingMessages.keyRequestJson(ByteArray(7), ByteArray(65), ByteArray(16)))
        }
        expectProtocolError {
            PairingMessages.parseHelloResponse(PairingMessages.helloResponseJson(ByteArray(8), ByteArray(65), ByteArray(16), "x".repeat(41)))
        }
        expectProtocolError {
            PairingMessages.parseSessionRequest(
                JSONObject().put("v", 1).put("sid", PairingCodec.encode(ByteArray(8))).put("iv", PairingCodec.encode(ByteArray(12)))
                    .put("ct", PairingCodec.encode(ByteArray(PairingMessages.MAX_PAYLOAD_BYTES + 1))).toString(),
            )
        }
        expectProtocolError {
            PairingMessages.parseHelloRequest(JSONObject().put("v", 1).put("mode", "sas").put("c", "***").toString())
        }
    }

    @Test
    fun payloadRoundTripWithAndWithoutSpotify() {
        val withSpotify = PairingSessionPayload.fromJson(PairingSessionPayload.from(testSession(), "spdc-secret").toJson())
        assertEquals(testSession(), withSpotify.toYouTubeSession())
        assertEquals("spdc-secret", withSpotify.spotify?.spDc)

        val without = PairingSessionPayload.fromJson(PairingSessionPayload.from(testSession(), "  ").toJson())
        assertNull(without.spotify)
        assertFalse(JSONObject(PairingSessionPayload.from(testSession(), null).toJson()).has("spotify"))
    }

    @Test
    fun payloadRejectsCookieWithoutSapisidAndUnknownFields() {
        val noLogin = PairingSessionPayload.from(testSession().copy(cookie = "PREF=1; NID=2"), null).toJson()
        expectProtocolError { PairingSessionPayload.fromJson(noLogin) }

        val base = JSONObject(PairingSessionPayload.from(testSession(), null).toJson())
        expectProtocolError { PairingSessionPayload.fromJson(JSONObject(base.toString()).put("qobuz", "x").toString()) }
        expectProtocolError {
            PairingSessionPayload.fromJson(JSONObject(base.toString()).apply { getJSONObject("youtube").put("password", "x") }.toString())
        }
        expectProtocolError { PairingSessionPayload.fromJson(JSONObject(base.toString()).put("v", 3).toString()) }
        expectProtocolError {
            PairingSessionPayload.fromJson(JSONObject(base.toString()).put("spotify", JSONObject().put("spDc", "a").put("x", 1)).toString())
        }
    }

    @Test
    fun payloadRejectsOversizedFields() {
        val bigCookie = TEST_COOKIE + "; X=" + "a".repeat(PairingMessages.MAX_COOKIE_BYTES)
        expectProtocolError { PairingSessionPayload.fromJson(PairingSessionPayload.from(testSession().copy(cookie = bigCookie), null).toJson()) }
        val bigName = testSession(name = "n".repeat(PairingMessages.MAX_FIELD_BYTES + 1))
        expectProtocolError { PairingSessionPayload.fromJson(PairingSessionPayload.from(bigName, null).toJson()) }
        expectProtocolError {
            PairingSessionPayload.fromJson(PairingSessionPayload.from(testSession(), "s".repeat(PairingMessages.MAX_SPDC_BYTES + 1)).toJson())
        }
    }

    @Test
    fun toStringNeverLeaksSecrets() {
        val token = PairingCrypto.randomBytes(16)
        val payload = PairingSessionPayload.from(testSession(), "spdc-secret")
        val key = PairingKeyExchange(PairingCrypto.randomBytes(32), PairingCrypto.randomBytes(32), "123-456")
        val texts = listOf(
            payload.toString(),
            payload.youtube.toString(),
            payload.spotify.toString(),
            HelloRequest(PairingMessages.MODE_QR, token, ByteArray(32)).toString(),
            PairingUri.QrInfo("192.168.1.5", 4000, token, ByteArray(32), "TV").toString(),
            key.toString(),
        )
        val secrets = listOf("123-456", "abc123secret", "sidsecret", "spdc-secret", PairingCodec.encode(token), PairingCodec.encode(key.key))
        texts.forEach { text -> secrets.forEach { secret -> assertFalse("$text leaks $secret", text.contains(secret)) } }
    }
}
