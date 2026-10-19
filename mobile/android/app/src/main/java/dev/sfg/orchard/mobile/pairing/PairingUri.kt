// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import dev.sfg.orchard.mobile.cast.parsePrivateIpv4
import java.io.ByteArrayOutputStream
import java.net.URI

/** Builds and strictly parses the `orchard://pair` deep link. No Android types (PAIR-INV-3). */
object PairingUri {
    const val SCHEME = "orchard"
    const val HOST = "pair"

    data class QrInfo(
        val host: String,
        val port: Int,
        val token: ByteArray,
        val fingerprint: ByteArray,
        val tvName: String,
    ) {
        override fun toString(): String = "QrInfo(host=$host, port=$port, name=$tvName, token=${token.size}B, fp=${fingerprint.size}B)"
    }

    fun build(host: String, port: Int, token: ByteArray, fingerprint: ByteArray, tvName: String): String {
        require(parsePrivateIpv4(host) != null) { "host must be a private IPv4 address" }
        require(port in 1024..65535) { "port must be 1024..65535" }
        require(token.size == 16) { "token must be 16 bytes" }
        require(fingerprint.size == 32) { "fingerprint must be 32 bytes" }
        require(tvName.length <= PairingMessages.MAX_TV_NAME) { "TV name too long" }
        val query = buildString {
            append("v=1")
            append("&h=").append(percentEncode(host))
            append("&p=").append(port)
            append("&t=").append(PairingCodec.encode(token))
            append("&f=").append(PairingCodec.encode(fingerprint))
            append("&n=").append(percentEncode(tvName))
        }
        return "$SCHEME://$HOST?$query"
    }

    fun parse(uri: String): QrInfo? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (!parsed.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!parsed.host.equals(HOST, ignoreCase = true)) return null
        val rawQuery = parsed.rawQuery ?: return null

        val params = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val separator = part.indexOf('=')
            val key = percentDecode(if (separator < 0) part else part.substring(0, separator)) ?: return null
            val value = if (separator < 0) "" else percentDecode(part.substring(separator + 1)) ?: return null
            if (params.containsKey(key)) return null
            params[key] = value
        }
        if (params.keys != setOf("v", "h", "p", "t", "f", "n")) return null
        if (params["v"] != "1") return null

        val host = params["h"] ?: return null
        if (parsePrivateIpv4(host) == null) return null
        val port = params["p"]?.toIntOrNull() ?: return null
        if (port !in 1024..65535) return null
        val token = decodeExact(params["t"], 16) ?: return null
        val fingerprint = decodeExact(params["f"], 32) ?: return null
        val tvName = params["n"] ?: return null
        if (tvName.isBlank() || tvName.length > PairingMessages.MAX_TV_NAME) return null

        return QrInfo(host, port, token, fingerprint, tvName)
    }

    private fun decodeExact(value: String?, expected: Int): ByteArray? {
        if (value == null) return null
        val decoded = runCatching { PairingCodec.decode(value) }.getOrNull() ?: return null
        return decoded.takeIf { it.size == expected }
    }

    private fun percentEncode(value: String): String {
        val out = ByteArrayOutputStream()
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val ch = unsigned.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.write(unsigned)
            } else {
                out.write('%'.code)
                out.write(HEX[unsigned ushr 4].code)
                out.write(HEX[unsigned and 0x0f].code)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun percentDecode(value: String): String? {
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '%') {
                if (index + 2 >= value.length) return null
                val high = Character.digit(value[index + 1], 16)
                val low = Character.digit(value[index + 2], 16)
                if (high < 0 || low < 0) return null
                out.write((high shl 4) + low)
                index += 3
            } else if (ch.code < 0x80) {
                out.write(ch.code)
                index++
            } else {
                // Non-ASCII must arrive percent-encoded; reject raw UTF-8 in the query.
                return null
            }
        }
        return runCatching { out.toString(Charsets.UTF_8.name()) }.getOrNull()
    }

    private const val HEX = "0123456789ABCDEF"
}
