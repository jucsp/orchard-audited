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

package dev.sfg.orchard.mobile.qobuz

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dev.sfg.orchard.mobile.security.AndroidKeystoreCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PREFS_NAME = "orchard_qobuz"
private const val KEY_TOKEN = "qobuz_token"
private const val KEY_USER_ID = "qobuz_user_id"
private const val KEY_ENABLED = "qobuz_enabled"
private const val KEY_QUALITY = "qobuz_quality"
private const val TOKEN_KEY_ALIAS = "orchard_qobuz_token_v1"

/** Seam over the Keystore envelope so the migration runs in JVM tests. */
interface QobuzTokenCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
    fun isEncrypted(value: String): Boolean
}

private class KeystoreQobuzTokenCipher(private val cipher: AndroidKeystoreCipher) : QobuzTokenCipher {
    override fun encrypt(value: String): String = cipher.encrypt(value)
    override fun decrypt(value: String): String = cipher.decrypt(value)
    override fun isEncrypted(value: String): Boolean = cipher.isEncrypted(value)
}

/**
 * Qobuz credentials. `qobuz_token` is stored encrypted with a non-exportable Keystore key; a token
 * saved in clear by an older build is re-encrypted on first read (SDD-tv-qr-pairing-connect D4).
 */
@SuppressLint("UseKtx") // Token writes use commit(): a failed write must not look like a saved credential.
class QobuzRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val cipher: QobuzTokenCipher = KeystoreQobuzTokenCipher(AndroidKeystoreCipher(TOKEN_KEY_ALIAS)),
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _status = MutableStateFlow(loadInitialStatus())
    val status: StateFlow<QobuzStatus> = _status.asStateFlow()

    private fun loadInitialStatus(): QobuzStatus {
        val token = readToken()
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val qualityId = prefs.getString(KEY_QUALITY, QobuzQuality.AUTO.id)
        val quality = QobuzQuality.fromId(qualityId)
        return QobuzStatus(
            status = if (token.isNotBlank()) "connected" else "disconnected",
            enabled = enabled && token.isNotBlank(),
            quality = quality,
        )
    }

    /** Plain token, migrating a clear-text value in place. Blank when absent or unreadable. */
    private fun readToken(): String = synchronized(lock) {
        val stored = prefs.getString(KEY_TOKEN, "").orEmpty()
        when {
            stored.isBlank() -> ""
            cipher.isEncrypted(stored) -> runCatching { cipher.decrypt(stored) }.getOrElse {
                // A restored preference without its Keystore key cannot be read: drop it.
                prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).putBoolean(KEY_ENABLED, false).commit()
                ""
            }
            else -> stored.also { prefs.edit().putString(KEY_TOKEN, cipher.encrypt(it)).commit() }
        }
    }

    fun getCredentials(): QobuzSession? {
        val token = readToken()
        val userId = prefs.getLong(KEY_USER_ID, -1L)
        if (token.isBlank() || userId <= 0) return null
        return QobuzSession(token = token, userId = userId)
    }

    fun connect(token: String, userId: Long) {
        val encrypted = cipher.encrypt(token)
        synchronized(lock) {
            check(
                prefs.edit()
                    .putString(KEY_TOKEN, encrypted)
                    .putLong(KEY_USER_ID, userId)
                    .putBoolean(KEY_ENABLED, true)
                    .commit(),
            ) { "Qobuz token storage failed" }
        }

        _status.value = _status.value.copy(
            status = "connected",
            enabled = true,
            lastError = "",
        )
    }

    fun disconnect() {
        synchronized(lock) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).putBoolean(KEY_ENABLED, false).commit()
        }

        _status.value = _status.value.copy(
            status = "disconnected",
            enabled = false,
            lastError = "",
        )
    }

    fun setEnabled(enabled: Boolean) {
        val hasToken = prefs.getString(KEY_TOKEN, "").orEmpty().isNotBlank()
        val effective = enabled && hasToken
        prefs.edit().putBoolean(KEY_ENABLED, effective).apply()
        _status.value = _status.value.copy(enabled = effective)
    }

    fun setQuality(quality: QobuzQuality) {
        prefs.edit().putString(KEY_QUALITY, quality.id).apply()
        _status.value = _status.value.copy(quality = quality)
    }

    fun setLastError(error: String) {
        _status.value = _status.value.copy(lastError = error)
    }
}
