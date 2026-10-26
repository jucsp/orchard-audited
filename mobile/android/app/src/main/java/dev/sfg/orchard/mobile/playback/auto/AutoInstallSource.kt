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

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reads, locally and without any network call, where this app was installed from.
 *
 * Android Auto only lists media apps that came from Google Play unless the user enables its
 * developer option for unknown sources. Orchard ships as a signed GitHub release APK, so the phone
 * settings need this answer to show the right guidance.
 */
class AutoInstallSource(
    private val packageManager: PackageManager,
    private val packageName: String,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    fun installingPackage(): String? = when {
        sdkInt >= 30 -> runCatching { packageManager.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        else -> runCatching { packageManager.getInstallerPackageName(packageName) }.getOrNull()
    }

    fun isFromPlayStore(): Boolean = installingPackage() == PLAY_STORE_PACKAGE

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"

        /** Pure predicate so the decision is testable without a [PackageManager]. */
        fun isPlayStore(installingPackage: String?): Boolean = installingPackage == PLAY_STORE_PACKAGE
    }
}
