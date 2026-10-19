// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.net.Uri

/** Destinations of the TV NavHost. Search is a real destination here, unlike on the phone (ADR-4). */
object TvRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"
    const val DETAIL = "detail"

    const val DETAIL_ARG = "id"
    const val DETAIL_PATTERN = "$DETAIL/{$DETAIL_ARG}"

    fun detail(id: String) = "$DETAIL/${Uri.encode(id)}"

    /** Rail destinations; everything else is pushed on top of one of these. */
    val topLevel = setOf(HOME, SEARCH, LIBRARY, SETTINGS)
}
