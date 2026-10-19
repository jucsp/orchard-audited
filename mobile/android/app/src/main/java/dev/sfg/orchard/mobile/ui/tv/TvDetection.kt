// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the app runs on Android TV / Google TV. Evaluated once per root composition: no
 * scan is performed at startup and nothing here acquires a network or multicast resource.
 */
@Composable
fun isTvLayout(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTvDevice(context) }
}

/** Plain (non-Compose) variant for callers without a composition, such as the updater. */
fun isTvDevice(context: Context): Boolean {
    val uiMode = context.getSystemService(UiModeManager::class.java)
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
