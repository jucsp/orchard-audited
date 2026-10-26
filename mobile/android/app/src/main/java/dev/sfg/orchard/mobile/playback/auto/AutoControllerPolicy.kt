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
 * Decides whether a connected controller may navigate the media library.
 *
 * Navigation means the media ids a controller can browse: titles, playlist names, private library
 * structure and authenticated search results. Transport commands are unaffected by this check.
 *
 * Pure JVM logic over package names and the trust flag Media3 already computed, so it can be tested
 * without Android.
 */
object AutoControllerPolicy {
    private val allowedNavigationPackages = setOf(
        "com.google.android.projection.gearhead",
        "com.android.systemui",
        "com.google.android.carassistant",
    )

    fun canBrowse(appPackageName: String, controllerPackageName: String?, isTrusted: Boolean): Boolean {
        if (controllerPackageName.isNullOrBlank()) return false
        if (controllerPackageName == appPackageName) return true
        if (isTrusted) return true
        return controllerPackageName in allowedNavigationPackages
    }
}
