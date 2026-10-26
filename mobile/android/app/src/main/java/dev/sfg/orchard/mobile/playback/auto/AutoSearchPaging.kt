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

package dev.sfg.orchard.mobile.playback.auto

/**
 * Search shaping for the in-car screen. Kept free of Android types so the session callback's search
 * behaviour can be exercised in a plain JVM test.
 */
object AutoSearchPaging {
    const val MAX_QUERY_LENGTH = 200
    const val MAX_PAGE_SIZE = 100

    /** Caps the on-screen query so a pasted URL cannot flood the provider or the logs. */
    fun sanitizeQuery(query: String): String = query.take(MAX_QUERY_LENGTH)

    /** A page of [items], never larger than [MAX_PAGE_SIZE] (AUTO-INV-5). */
    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        val capped = pageSize.coerceAtMost(MAX_PAGE_SIZE)
        return items.drop(page * capped).take(capped)
    }
}
