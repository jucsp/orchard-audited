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
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val LOGIN_WEB = "login_web"
    const val SPOTIFY_LOGIN = "spotify_login"
    const val QOBUZ_LOGIN = "qobuz_login"
    const val ACCOUNT_SWITCH = "account_switch"
    const val DOWNLOADS = "downloads"
    const val SETTINGS_HOME_LAYOUT = "settings_home_layout"
    /** Phone settings embedded in the safe area, for options the TV screen does not cover. */
    const val SETTINGS_ADVANCED = "settings_advanced"
    const val MUSIC_VIDEO = "music_video"
    const val LISTENING_PARTY = "listening_party"

    const val DETAIL_ARG = "id"
    const val DETAIL_PATTERN = "$DETAIL/{$DETAIL_ARG}"

    fun detail(id: String) = "$DETAIL/${Uri.encode(id)}"

    /** Rail destinations; everything else is pushed on top of one of these. */
    val topLevel = setOf(HOME, SEARCH, LIBRARY, SETTINGS)

    /** Full-screen destinations: the rail is hidden and the backdrop switches to the player role. */
    val immersive = setOf(NOW_PLAYING, MUSIC_VIDEO)
}
