// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import android.content.Context
import android.content.SharedPreferences

/**
 * TOFU pins kept across restarts (CAST-INV-7). Kept apart from `CastTrust.kt`, which stays free of
 * `android.*` so it runs in JVM tests.
 */
class SharedPreferencesCastTrustStore(context: Context) : CastTrustStore {
    private val appContext = context.applicationContext

    // Opened on first use, which is the TLS handshake thread, not the main thread.
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getFingerprint(deviceId: String): String? = prefs.getString(deviceId, null)

    override fun setFingerprint(deviceId: String, sha256Fingerprint: String) {
        // Written synchronously so a crash right after the first handshake does not lose the pin.
        prefs.edit().putString(deviceId, sha256Fingerprint).commit()
    }

    companion object {
        const val PREFS_NAME = "orchard_cast_tofu_pins"
    }
}
