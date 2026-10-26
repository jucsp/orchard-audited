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

    override fun getDeviceKeyPin(deviceId: String): String? = prefs.getString(deviceKey(deviceId), null)

    override fun setDeviceKeyPin(deviceId: String, pinDk: String) {
        prefs.edit().putString(deviceKey(deviceId), pinDk).commit()
    }

    override fun migrateV1ToV2(deviceId: String, pinDk: String) {
        // Single synchronous Editor: atomic promotion and v1 removal (CAST-ROT-INV-7).
        prefs.edit()
            .putString(deviceKey(deviceId), pinDk)
            .remove(deviceId)
            .commit()
    }

    override fun forget(deviceId: String) {
        prefs.edit()
            .remove(deviceKey(deviceId))
            .remove(deviceId)
            .commit()
    }

    private fun deviceKey(deviceId: String): String = "dk:$deviceId"

    companion object {
        const val PREFS_NAME = "orchard_cast_tofu_pins"
    }
}
