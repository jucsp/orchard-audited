// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.auth.YouTubeSession
import dev.sfg.orchard.mobile.auth.YouTubeSessionAuth
import dev.sfg.orchard.mobile.model.LyricTranslationProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cleartext payload sealed inside AES-GCM before it leaves the phone (PAIR-INV-1). Every block is
 * optional but at least one is present (PAIR-CONN-INV-4). The phone always writes `v=2`; the TV
 * also reads `v=1`, which carried only YouTube and Spotify. No refresh or access token of Orchard
 * Connect ever travels here, only a one-time provisioning code (PAIR-CONN-INV-1).
 */
data class PairingSessionPayload(
    val youtube: PairingYoutubeSession? = null,
    val spotify: PairingSpotifySession? = null,
    val orchardConnect: PairingConnectGrant? = null,
    val qobuz: PairingQobuzSession? = null,
    val translationKeys: List<PairingTranslationKey> = emptyList(),
) {
    val isEmpty: Boolean
        get() = youtube == null && spotify == null && orchardConnect == null && qobuz == null && translationKeys.isEmpty()

    override fun toString(): String =
        "PairingSessionPayload(youtube=${youtube != null}, spotify=${spotify != null}, " +
            "orchardConnect=${orchardConnect != null}, qobuz=${qobuz != null}, " +
            "translationKeys=${translationKeys.map { it.provider.key }})"

    fun toJson(): String {
        require(!isEmpty) { "payload has no blocks" }
        val root = JSONObject().put("v", VERSION)
        youtube?.let {
            root.put(
                "youtube",
                JSONObject()
                    .put("cookie", it.cookie)
                    .put("visitorData", it.visitorData)
                    .put("dataSyncId", it.dataSyncId)
                    .put("accountIndex", it.accountIndex)
                    .put("displayName", it.displayName)
                    .put("avatarUrl", it.avatarUrl),
            )
        }
        spotify?.let { root.put("spotify", JSONObject().put("spDc", it.spDc)) }
        orchardConnect?.let {
            root.put(
                "orchardConnect",
                JSONObject().put("provisionCode", it.provisionCode).put("email", it.email).put("name", it.name),
            )
        }
        qobuz?.let { root.put("qobuz", JSONObject().put("token", it.token).put("userId", it.userId)) }
        if (translationKeys.isNotEmpty()) {
            val array = JSONArray()
            translationKeys.forEach { array.put(JSONObject().put("provider", it.provider.key).put("key", it.key)) }
            root.put("translationKeys", array)
        }
        return root.toString()
    }

    fun toYouTubeSession(): YouTubeSession? = youtube?.let {
        YouTubeSession(
            cookie = it.cookie,
            visitorData = it.visitorData,
            dataSyncId = it.dataSyncId,
            accountIndex = it.accountIndex,
            displayName = it.displayName,
            avatarUrl = it.avatarUrl,
        )
    }

    companion object {
        const val VERSION = 2
        const val LEGACY_VERSION = 1
        const val MAX_CONNECT_TEXT = 256
        const val MAX_QOBUZ_TOKEN = 512
        const val MAX_TRANSLATION_KEYS = 4
        const val MAX_TRANSLATION_KEY = 256
        private val PROVISION_CODE = Regex("^[A-Za-z0-9_-]{43}$")
        private val V1_KEYS = setOf("v", "youtube", "spotify")
        private val V2_KEYS = V1_KEYS + setOf("orchardConnect", "qobuz", "translationKeys")
        private val YOUTUBE_KEYS = setOf("cookie", "visitorData", "dataSyncId", "accountIndex", "displayName", "avatarUrl")

        fun youtubeOf(session: YouTubeSession): PairingYoutubeSession = PairingYoutubeSession(
            cookie = session.cookie,
            visitorData = session.visitorData,
            dataSyncId = session.dataSyncId,
            accountIndex = session.accountIndex,
            displayName = session.displayName,
            avatarUrl = session.avatarUrl,
        )

        fun from(session: YouTubeSession, spotifySpdc: String?): PairingSessionPayload =
            PairingSessionPayload(
                youtube = youtubeOf(session),
                spotify = spotifySpdc?.takeIf { it.isNotBlank() }?.let(::PairingSpotifySession),
            )

        /** Strict parser: unknown versions, blocks, fields or providers are rejected (`bad_payload`). */
        fun fromJson(json: String): PairingSessionPayload {
            if (json.toByteArray(Charsets.UTF_8).size > PairingMessages.MAX_PAYLOAD_BYTES) {
                throw PairingProtocolException("payload exceeds ${PairingMessages.MAX_PAYLOAD_BYTES} bytes")
            }
            val root = JSONObject(json)
            val version = root.opt("v")
            val allowed = when (version) {
                LEGACY_VERSION -> V1_KEYS
                VERSION -> V2_KEYS
                else -> throw PairingProtocolException("unsupported payload version")
            }
            requireExactKeys(root, allowed, "payload")
            if (version == LEGACY_VERSION && !root.has("youtube")) throw PairingProtocolException("missing youtube payload")

            val payload = PairingSessionPayload(
                youtube = root.optJSONObjectStrict("youtube")?.let(::parseYoutube),
                spotify = root.optJSONObjectStrict("spotify")?.let(::parseSpotify),
                orchardConnect = root.optJSONObjectStrict("orchardConnect")?.let(::parseConnect),
                qobuz = root.optJSONObjectStrict("qobuz")?.let(::parseQobuz),
                translationKeys = if (root.has("translationKeys")) parseTranslationKeys(root.get("translationKeys")) else emptyList(),
            )
            if (payload.isEmpty) throw PairingProtocolException("payload has no blocks")
            return payload
        }

        private fun parseYoutube(obj: JSONObject): PairingYoutubeSession {
            requireExactKeys(obj, YOUTUBE_KEYS, "youtube", required = setOf("cookie"))
            val cookie = PairingMessages.boundedText(obj.optString("cookie"), PairingMessages.MAX_COOKIE_BYTES, "cookie", allowBlank = false)
            if (YouTubeSessionAuth.loginCookieValue(cookie) == null) {
                throw PairingProtocolException("cookie lacks a signed-in session id")
            }
            return PairingYoutubeSession(
                cookie = cookie,
                visitorData = PairingMessages.boundedText(obj.optString("visitorData"), PairingMessages.MAX_FIELD_BYTES, "visitorData"),
                dataSyncId = PairingMessages.boundedText(obj.optString("dataSyncId"), PairingMessages.MAX_FIELD_BYTES, "dataSyncId"),
                accountIndex = obj.optInt("accountIndex", 0).coerceAtLeast(0),
                displayName = PairingMessages.boundedText(obj.optString("displayName"), PairingMessages.MAX_FIELD_BYTES, "displayName"),
                avatarUrl = PairingMessages.boundedText(obj.optString("avatarUrl"), PairingMessages.MAX_FIELD_BYTES, "avatarUrl"),
            )
        }

        private fun parseSpotify(obj: JSONObject): PairingSpotifySession {
            requireExactKeys(obj, setOf("spDc"), "spotify", required = setOf("spDc"))
            return PairingSpotifySession(
                PairingMessages.boundedText(obj.optString("spDc"), PairingMessages.MAX_SPDC_BYTES, "spDc", allowBlank = false),
            )
        }

        private fun parseConnect(obj: JSONObject): PairingConnectGrant {
            requireExactKeys(obj, setOf("provisionCode", "email", "name"), "orchardConnect", required = setOf("provisionCode"))
            val code = obj.optString("provisionCode")
            val decoded = runCatching { PairingCodec.decode(code) }.getOrNull()
            if (!PROVISION_CODE.matches(code) || decoded?.size != 32) {
                throw PairingProtocolException("provisionCode must be 32 bytes base64url")
            }
            PairingCrypto.wipe(decoded)
            return PairingConnectGrant(
                provisionCode = code,
                email = PairingMessages.boundedText(obj.optString("email"), MAX_CONNECT_TEXT, "email"),
                name = PairingMessages.boundedText(obj.optString("name"), MAX_CONNECT_TEXT, "name"),
            )
        }

        private fun parseQobuz(obj: JSONObject): PairingQobuzSession {
            requireExactKeys(obj, setOf("token", "userId"), "qobuz", required = setOf("token", "userId"))
            val userId = when (val raw = obj.get("userId")) {
                is Int -> raw.toLong()
                is Long -> raw
                else -> throw PairingProtocolException("userId must be an integer")
            }
            if (userId <= 0) throw PairingProtocolException("userId must be positive")
            return PairingQobuzSession(
                token = PairingMessages.boundedText(obj.optString("token"), MAX_QOBUZ_TOKEN, "token", allowBlank = false),
                userId = userId,
            )
        }

        private fun parseTranslationKeys(raw: Any): List<PairingTranslationKey> {
            val array = raw as? JSONArray ?: throw PairingProtocolException("translationKeys must be an array")
            if (array.length() !in 1..MAX_TRANSLATION_KEYS) {
                throw PairingProtocolException("translationKeys must hold 1..$MAX_TRANSLATION_KEYS entries")
            }
            val keys = (0 until array.length()).map { index ->
                val obj = array.opt(index) as? JSONObject ?: throw PairingProtocolException("translation key must be an object")
                requireExactKeys(obj, setOf("provider", "key"), "translation key", required = setOf("provider", "key"))
                val providerKey = obj.opt("provider") as? String ?: throw PairingProtocolException("provider must be a string")
                val provider = LyricTranslationProvider.entries
                    .firstOrNull { it.key == providerKey && it != LyricTranslationProvider.LOCAL }
                    ?: throw PairingProtocolException("unsupported translation provider")
                PairingTranslationKey(
                    provider,
                    PairingMessages.boundedText(obj.optString("key"), MAX_TRANSLATION_KEY, "key", allowBlank = false),
                )
            }
            if (keys.map { it.provider }.toSet().size != keys.size) throw PairingProtocolException("duplicate translation provider")
            return keys
        }

        private fun requireExactKeys(obj: JSONObject, allowed: Set<String>, block: String, required: Set<String> = emptySet()) {
            val keys = obj.keys().asSequence().toSet()
            val unknown = keys - allowed
            if (unknown.isNotEmpty()) throw PairingProtocolException("unknown $block fields: ${unknown.sorted().joinToString(",")}")
            required.forEach { if (!obj.has(it)) throw PairingProtocolException("missing $block field: $it") }
        }

        private fun JSONObject.optJSONObjectStrict(name: String): JSONObject? {
            if (!has(name)) return null
            return opt(name) as? JSONObject ?: throw PairingProtocolException("$name must be an object")
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

/** One-time code redeemed by the TV for its own Orchard Connect device. [email] and [name] are display only. */
data class PairingConnectGrant(val provisionCode: String, val email: String = "", val name: String = "") {
    override fun toString(): String = "PairingConnectGrant(email=${email.isNotBlank()}, code=${provisionCode.length} chars)"
}

data class PairingQobuzSession(val token: String, val userId: Long) {
    override fun toString(): String = "PairingQobuzSession(token=${token.length} chars)"
}

data class PairingTranslationKey(val provider: LyricTranslationProvider, val key: String) {
    override fun toString(): String = "PairingTranslationKey(provider=${provider.key}, key=${key.length} chars)"
}
