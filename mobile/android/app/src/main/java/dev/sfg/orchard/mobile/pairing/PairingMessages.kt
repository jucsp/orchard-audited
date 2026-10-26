// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

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
