// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.YouTubeSession
import dev.sfg.orchard.mobile.auth.YouTubeSessionAuth
import org.json.JSONObject

/** Wire models and strict JSON codec for `orchard-pair-v1`. No Android types (PAIR-INV-3). */
object PairingMessages {
    const val VERSION = 1
    const val MODE_QR = "qr"
    const val MODE_SAS = "sas"

    const val MAX_COOKIE_BYTES = 16 * 1024
    const val MAX_SPDC_BYTES = 1024
    const val MAX_FIELD_BYTES = 512
    const val MAX_PAYLOAD_BYTES = 32 * 1024
    const val MAX_TV_NAME = 40

    // --- request / response helpers ---------------------------------------------------------

    fun helloRequestJson(mode: String, token: ByteArray?, commitment: ByteArray): String {
        require(mode in setOf(MODE_QR, MODE_SAS)) { "unknown mode" }
        val obj = JSONObject()
        obj.put("v", VERSION)
        obj.put("mode", mode)
        obj.put("c", PairingCodec.encode(commitment))
        if (mode == MODE_QR) {
            require(token != null) { "QR mode requires a token" }
            obj.put("t", PairingCodec.encode(token))
        } else if (token != null) {
            throw PairingProtocolException("SAS mode must not carry a token")
        }
        return obj.toString()
    }

    fun helloResponseJson(sid: ByteArray, tvPub: ByteArray, nT: ByteArray, name: String): String =
        JSONObject()
            .put("v", VERSION)
            .put("sid", PairingCodec.encode(sid))
            .put("tvPub", PairingCodec.encode(tvPub))
            .put("nT", PairingCodec.encode(nT))
            .put("name", name)
            .toString()

    fun keyRequestJson(sid: ByteArray, phonePub: ByteArray, nP: ByteArray): String =
        JSONObject()
            .put("v", VERSION)
            .put("sid", PairingCodec.encode(sid))
            .put("phonePub", PairingCodec.encode(phonePub))
            .put("nP", PairingCodec.encode(nP))
            .toString()

    fun keyResponseJson(): String = JSONObject().put("v", VERSION).put("ok", true).toString()

    fun sessionRequestJson(sid: ByteArray, ivAndCt: ByteArray): String =
        JSONObject()
            .put("v", VERSION)
            .put("sid", PairingCodec.encode(sid))
            .put("iv", PairingCodec.encode(ivAndCt.copyOfRange(0, 12)))
            .put("ct", PairingCodec.encode(ivAndCt.copyOfRange(12, ivAndCt.size)))
            .toString()

    fun sessionResponseJson(status: String): String =
        JSONObject().put("v", VERSION).put("status", status).toString()

    fun errorJson(code: String): String = JSONObject().put("v", VERSION).put("error", code).toString()

    // --- parsing ---------------------------------------------------------------------------

    fun parseHelloRequest(json: String): HelloRequest {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("mode", "c"), setOf("t"))
        val mode = obj.getString("mode")
        if (mode !in setOf(MODE_QR, MODE_SAS)) throw PairingProtocolException("unknown mode")
        val commitment = exactBytes(obj.optString("c"), 32, "c")
        val token = when (mode) {
            MODE_QR -> exactBytes(obj.optString("t"), 16, "t")
            else -> if (obj.has("t")) throw PairingProtocolException("SAS mode must not carry a token") else null
        }
        return HelloRequest(mode, token, commitment)
    }

    fun parseHelloResponse(json: String): HelloResponse {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("sid", "tvPub", "nT", "name"))
        return HelloResponse(
            sessionId = exactBytes(obj.optString("sid"), 8, "sid"),
            tvPublicKey = rangedBytes(obj.optString("tvPub"), 1, 256, "tvPub"),
            tvNonce = exactBytes(obj.optString("nT"), 16, "nT"),
            name = boundedText(obj.optString("name"), MAX_TV_NAME, "name"),
        )
    }

    fun parseKeyRequest(json: String): KeyRequest {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("sid", "phonePub", "nP"))
        return KeyRequest(
            sessionId = exactBytes(obj.optString("sid"), 8, "sid"),
            phonePublicKey = rangedBytes(obj.optString("phonePub"), 1, 256, "phonePub"),
            phoneNonce = exactBytes(obj.optString("nP"), 16, "nP"),
        )
    }

    fun parseKeyResponse(json: String) {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("ok"))
        if (!obj.optBoolean("ok", false)) throw PairingProtocolException("key exchange not acknowledged")
    }

    fun parseSessionRequest(json: String): SessionRequest {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("sid", "iv", "ct"))
        val iv = exactBytes(obj.optString("iv"), 12, "iv")
        val ct = rangedBytes(obj.optString("ct"), 16, MAX_PAYLOAD_BYTES, "ct")
        return SessionRequest(exactBytes(obj.optString("sid"), 8, "sid"), iv + ct)
    }

    fun parseSessionResponse(json: String): String {
        val obj = JSONObject(json)
        requireVersion(obj)
        requireKeys(obj, setOf("status"))
        val status = obj.getString("status")
        if (status.isBlank()) throw PairingProtocolException("missing status")
        return status
    }

    /** Returns the error code, or null when the body is not a protocol error envelope. */
    fun parseErrorCode(json: String): String? {
        return runCatching {
            val obj = JSONObject(json)
            if (obj.optInt("v", -1) == VERSION && obj.has("error")) obj.getString("error") else null
        }.getOrNull()
    }

    // --- strict validation helpers ---------------------------------------------------------

    private fun requireVersion(obj: JSONObject) {
        if (obj.optInt("v", -1) != VERSION) throw PairingProtocolException("unsupported protocol version")
    }

    private fun requireKeys(obj: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
        val keys = obj.keys().asSequence().toSet()
        val allowed = required + optional + "v"
        val unknown = keys - allowed
        if (unknown.isNotEmpty()) throw PairingProtocolException("unknown fields: ${unknown.sorted().joinToString(",")}")
        required.forEach { if (!obj.has(it)) throw PairingProtocolException("missing field: $it") }
    }

    private fun exactBytes(value: String?, expected: Int, field: String): ByteArray {
        if (value == null) throw PairingProtocolException("missing field: $field")
        val decoded = runCatching { PairingCodec.decode(value) }
            .getOrElse { throw PairingProtocolException("$field is not base64url") }
        if (decoded.size != expected) throw PairingProtocolException("$field must be $expected bytes")
        return decoded
    }

    private fun rangedBytes(value: String?, min: Int, max: Int, field: String): ByteArray {
        if (value == null) throw PairingProtocolException("missing field: $field")
        val decoded = runCatching { PairingCodec.decode(value) }
            .getOrElse { throw PairingProtocolException("$field is not base64url") }
        if (decoded.size !in min..max) throw PairingProtocolException("$field must be $min..$max bytes")
        return decoded
    }

    internal fun boundedText(value: String?, max: Int, field: String, allowBlank: Boolean = true): String {
        if (value == null) throw PairingProtocolException("missing field: $field")
        if (!allowBlank && value.isBlank()) throw PairingProtocolException("$field must not be blank")
        if (value.length > max) throw PairingProtocolException("$field exceeds $max characters")
        return value
    }
}

/** First exchange: the phone announces itself. `commitment` = SHA-256(phonePublicKey). */
data class HelloRequest(
    val mode: String,
    val token: ByteArray?,
    val commitment: ByteArray,
) {
    override fun toString(): String = "HelloRequest(mode=$mode, token=${token?.size ?: 0}B, commitment=${commitment.size}B)"
}

data class HelloResponse(
    val sessionId: ByteArray,
    val tvPublicKey: ByteArray,
    val tvNonce: ByteArray,
    val name: String,
) {
    override fun toString(): String = "HelloResponse(sid=${sessionId.size}B, tvPub=${tvPublicKey.size}B, name=$name)"
}

data class KeyRequest(
    val sessionId: ByteArray,
    val phonePublicKey: ByteArray,
    val phoneNonce: ByteArray,
) {
    override fun toString(): String = "KeyRequest(sid=${sessionId.size}B, phonePub=${phonePublicKey.size}B)"
}

data class SessionRequest(
    val sessionId: ByteArray,
    val ivAndCiphertext: ByteArray,
) {
    override fun toString(): String = "SessionRequest(sid=${sessionId.size}B, sealed=${ivAndCiphertext.size}B)"
}

/** Cleartext payload sealed inside AES-GCM before it leaves the phone (PAIR-INV-1). */
data class PairingSessionPayload(
    val youtube: PairingYoutubeSession,
    val spotify: PairingSpotifySession? = null,
) {
    override fun toString(): String = "PairingSessionPayload(youtube=$youtube, spotify=${spotify != null})"

    fun toJson(): String {
        val youtubeObj = JSONObject()
            .put("cookie", youtube.cookie)
            .put("visitorData", youtube.visitorData)
            .put("dataSyncId", youtube.dataSyncId)
            .put("accountIndex", youtube.accountIndex)
            .put("displayName", youtube.displayName)
            .put("avatarUrl", youtube.avatarUrl)
        val root = JSONObject().put("v", PairingMessages.VERSION).put("youtube", youtubeObj)
        spotify?.let { root.put("spotify", JSONObject().put("spDc", it.spDc)) }
        return root.toString()
    }

    fun toYouTubeSession(): YouTubeSession = YouTubeSession(
        cookie = youtube.cookie,
        visitorData = youtube.visitorData,
        dataSyncId = youtube.dataSyncId,
        accountIndex = youtube.accountIndex,
        displayName = youtube.displayName,
        avatarUrl = youtube.avatarUrl,
    )

    companion object {
        fun from(session: YouTubeSession, spotifySpdc: String?): PairingSessionPayload =
            PairingSessionPayload(
                youtube = PairingYoutubeSession(
                    cookie = session.cookie,
                    visitorData = session.visitorData,
                    dataSyncId = session.dataSyncId,
                    accountIndex = session.accountIndex,
                    displayName = session.displayName,
                    avatarUrl = session.avatarUrl,
                ),
                spotify = spotifySpdc?.takeIf { it.isNotBlank() }?.let(::PairingSpotifySession),
            )

        fun fromJson(json: String): PairingSessionPayload {
            val root = JSONObject(json)
            if (root.optInt("v", -1) != PairingMessages.VERSION) throw PairingProtocolException("unsupported payload version")
            val keys = root.keys().asSequence().toSet()
            val unknown = keys - setOf("v", "youtube", "spotify")
            if (unknown.isNotEmpty()) throw PairingProtocolException("unknown payload fields: ${unknown.sorted().joinToString(",")}")
            if (!root.has("youtube")) throw PairingProtocolException("missing youtube payload")

            val youtube = root.getJSONObject("youtube")
            val ytKeys = youtube.keys().asSequence().toSet()
            val ytUnknown = ytKeys - setOf("cookie", "visitorData", "dataSyncId", "accountIndex", "displayName", "avatarUrl")
            if (ytUnknown.isNotEmpty()) throw PairingProtocolException("unknown youtube fields: ${ytUnknown.sorted().joinToString(",")}")

            val cookie = PairingMessages.boundedText(youtube.optString("cookie"), PairingMessages.MAX_COOKIE_BYTES, "cookie", allowBlank = false)
            if (YouTubeSessionAuth.loginCookieValue(cookie) == null) {
                throw PairingProtocolException("cookie lacks a signed-in session id")
            }
            val payload = PairingSessionPayload(
                youtube = PairingYoutubeSession(
                    cookie = cookie,
                    visitorData = PairingMessages.boundedText(youtube.optString("visitorData"), PairingMessages.MAX_FIELD_BYTES, "visitorData"),
                    dataSyncId = PairingMessages.boundedText(youtube.optString("dataSyncId"), PairingMessages.MAX_FIELD_BYTES, "dataSyncId"),
                    accountIndex = youtube.optInt("accountIndex", 0).coerceAtLeast(0),
                    displayName = PairingMessages.boundedText(youtube.optString("displayName"), PairingMessages.MAX_FIELD_BYTES, "displayName"),
                    avatarUrl = PairingMessages.boundedText(youtube.optString("avatarUrl"), PairingMessages.MAX_FIELD_BYTES, "avatarUrl"),
                ),
                spotify = if (root.has("spotify")) {
                    val spotify = root.getJSONObject("spotify")
                    val spKeys = spotify.keys().asSequence().toSet()
                    if (spKeys != setOf("spDc")) throw PairingProtocolException("unknown spotify fields")
                    PairingSpotifySession(PairingMessages.boundedText(spotify.optString("spDc"), PairingMessages.MAX_SPDC_BYTES, "spDc", allowBlank = false))
                } else {
                    null
                },
            )
            if (payload.toJson().toByteArray(Charsets.UTF_8).size > PairingMessages.MAX_PAYLOAD_BYTES) {
                throw PairingProtocolException("payload exceeds ${PairingMessages.MAX_PAYLOAD_BYTES} bytes")
            }
            return payload
        }
    }
}

data class PairingYoutubeSession(
    val cookie: String,
    val visitorData: String = "",
    val dataSyncId: String = "",
    val accountIndex: Int = 0,
    val displayName: String = "YouTube Music",
    val avatarUrl: String = "",
) {
    override fun toString(): String = "PairingYoutubeSession(displayName=$displayName, accountIndex=$accountIndex, cookie=${cookie.length} chars)"
}

data class PairingSpotifySession(val spDc: String) {
    override fun toString(): String = "PairingSpotifySession(spDc=${spDc.length} chars)"
}
