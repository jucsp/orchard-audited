/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.orchard.mobile.discord

import dev.sfg.orchard.connect.BuildConfig
import java.net.URI

/** 0 when the build sets no `orchardDiscordAppId`; presence then stays off. */
val DISCORD_APPLICATION_ID: Long = BuildConfig.DISCORD_APPLICATION_ID
const val DISCORD_ORCHARD_PROJECT_URL = "https://github.com/jucsp/orchard-audited"
const val DISCORD_LISTENING = 2

fun trimDiscordText(value: String?, fallback: String = "", maxLen: Int = 128): String {
    val text = (value?.takeIf(String::isNotBlank) ?: fallback).replace("\\s+".toRegex(), " ").trim()
    return if (text.length > maxLen) text.substring(0, maxLen).trim() else text
}

fun normalizeDiscordUrl(value: String?): String {
    val text = value?.trim().orEmpty()
    if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
        return ""
    }
    return runCatching { URI(text).toASCIIString() }.getOrDefault("")
}

fun normalizeDiscordImageUrl(value: String?): String {
    val text = normalizeDiscordUrl(value)
    if (text.isBlank()) return ""
    return runCatching {
        val uri = URI(text)
        val path = uri.path.lowercase()
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") || path.endsWith(".m4v")) {
            ""
        } else {
            text
        }
    }.getOrDefault("")
}
