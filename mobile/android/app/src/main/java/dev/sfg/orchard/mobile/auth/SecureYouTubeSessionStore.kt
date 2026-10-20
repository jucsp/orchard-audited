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

package dev.sfg.orchard.mobile.auth

import android.annotation.SuppressLint
import android.content.Context
import dev.sfg.orchard.mobile.security.AndroidKeystoreCipher
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the YouTube cookie sessions encrypted and excluded from Android backup. */
@SuppressLint("UseKtx") // commit() is intentional for security-critical writes.
class SecureYouTubeSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreCipher(KEY_ALIAS)

    /** Backwards-compatible read of the active session (the legacy single-session API). */
    @Synchronized
    fun load(): YouTubeSession? = loadActive()

    @Synchronized
    fun loadActive(): YouTubeSession? {
        ensureMigrated()
        val accounts = readAccountsLocked()
        if (accounts.isEmpty()) return null

        val activeId = readActiveIdLocked()
            ?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.first().id.also { writeActiveIdLocked(it) }
        return accounts.firstOrNull { it.id == activeId }?.session
    }

    @Synchronized
    fun loadAllAccounts(): List<YouTubeAccount> {
        ensureMigrated()
        val activeId = readActiveIdLocked()
        return readAccountsLocked().map { it.copy(isActive = it.id == activeId) }
    }

    @Synchronized
    fun saveAccount(session: YouTubeSession, makeActive: Boolean = true): YouTubeAccount {
        require(YouTubeSessionAuth.loginCookieValue(session.cookie) != null) {
            "YouTube session cookie is incomplete"
        }
        val account = YouTubeAccount(
            id = accountId(session),
            displayName = session.displayName.ifBlank { "YouTube Music" },
            avatarUrl = session.avatarUrl,
            session = session,
            lastUsedMs = System.currentTimeMillis(),
        )
        val accounts = listOf(account) + readAccountsLocked().filterNot { it.id == account.id }
        writeAccountsLocked(accounts)
        if (makeActive || readActiveIdLocked() == null) writeActiveIdLocked(account.id)
        return account
    }

    @Synchronized
    fun switchActiveAccount(accountId: String): YouTubeSession? {
        ensureMigrated()
        val accounts = readAccountsLocked()
        val account = accounts.firstOrNull { it.id == accountId } ?: return null
        writeAccountsLocked(accounts.map { if (it.id == accountId) it.copy(lastUsedMs = System.currentTimeMillis()) else it })
        writeActiveIdLocked(accountId)
        return account.session
    }

    @Synchronized
    fun removeAccount(accountId: String): Boolean {
        ensureMigrated()
        val accounts = readAccountsLocked()
        if (accounts.none { it.id == accountId }) return false
        val remaining = accounts.filterNot { it.id == accountId }
        val activeId = readActiveIdLocked()
        val nextActiveId = if (activeId == accountId) remaining.firstOrNull()?.id else activeId
        writeAccountsLocked(remaining)
        if (nextActiveId == null) writeActiveIdLocked(null) else writeActiveIdLocked(nextActiveId)
        return true
    }

    @Synchronized
    fun clearAll() {
        check(preferences.edit().remove(ACCOUNTS).remove(ACTIVE_ID).remove(SESSION).commit()) {
            "YouTube session deletion failed"
        }
    }

    /** Backwards-compatible alias used by callers that only know the single-session store. */
    @Synchronized
    fun clear() = clearAll()

    private fun ensureMigrated() {
        if (preferences.contains(ACCOUNTS)) return
        val legacy = preferences.getString(SESSION, null) ?: return
        val root = JSONObject(cipher.decrypt(legacy))
        if (!root.has("cookie")) {
            // Device-code OAuth data from older builds is incompatible with native cookie auth.
            check(preferences.edit().remove(SESSION).commit()) { "Legacy credential deletion failed" }
            return
        }
        val session = YouTubeSession(
            cookie = root.getString("cookie"),
            visitorData = root.optString("visitorData"),
            // Earlier builds stored the account's own id here, which YouTube rejects as a page id.
            dataSyncId = if (root.optInt("dataSyncIdFormat") >= DATA_SYNC_ID_FORMAT) root.optString("dataSyncId") else "",
            accountIndex = root.optInt("accountIndex").coerceAtLeast(0),
            displayName = root.optString("displayName", "YouTube Music").ifBlank { "YouTube Music" },
            avatarUrl = root.optString("avatarUrl"),
        ).takeIf { YouTubeSessionAuth.loginCookieValue(it.cookie) != null } ?: run {
            check(preferences.edit().remove(SESSION).commit()) { "Legacy credential deletion failed" }
            return
        }
        saveAccount(session, makeActive = true)
        check(preferences.edit().remove(SESSION).commit()) { "Legacy credential deletion failed" }
    }

    private fun readAccountsLocked(): List<YouTubeAccount> {
        val stored = preferences.getString(ACCOUNTS, null) ?: return emptyList()
        val root = JSONArray(cipher.decrypt(stored))
        return (0 until root.length()).mapNotNull { index ->
            val account = root.optJSONObject(index) ?: return@mapNotNull null
            val sessionJson = account.optJSONObject("session") ?: return@mapNotNull null
            val cookie = sessionJson.optString("cookie")
            if (YouTubeSessionAuth.loginCookieValue(cookie) == null) return@mapNotNull null
            val session = YouTubeSession(
                cookie = cookie,
                visitorData = sessionJson.optString("visitorData"),
                dataSyncId = sessionJson.optString("dataSyncId"),
                accountIndex = sessionJson.optInt("accountIndex").coerceAtLeast(0),
                displayName = sessionJson.optString("displayName", account.optString("displayName", "YouTube Music")).ifBlank { "YouTube Music" },
                avatarUrl = sessionJson.optString("avatarUrl", account.optString("avatarUrl")),
            )
            YouTubeAccount(
                id = account.optString("id").ifBlank { accountId(session) },
                displayName = account.optString("displayName", session.displayName).ifBlank { "YouTube Music" },
                avatarUrl = account.optString("avatarUrl", session.avatarUrl),
                session = session,
                lastUsedMs = account.optLong("lastUsedMs", System.currentTimeMillis()),
            )
        }
    }

    private fun writeAccountsLocked(accounts: List<YouTubeAccount>) {
        val root = JSONArray()
        for (account in accounts) {
            val session = account.session
            val sessionJson = JSONObject()
                .put("cookie", session.cookie)
                .put("visitorData", session.visitorData)
                .put("dataSyncId", session.dataSyncId)
                .put("accountIndex", session.accountIndex)
                .put("displayName", session.displayName)
                .put("avatarUrl", session.avatarUrl)
            root.put(
                JSONObject()
                    .put("id", account.id)
                    .put("displayName", account.displayName)
                    .put("avatarUrl", account.avatarUrl)
                    .put("lastUsedMs", account.lastUsedMs)
                    .put("session", sessionJson),
            )
        }
        check(preferences.edit().putString(ACCOUNTS, cipher.encrypt(root.toString())).commit()) {
            "YouTube session storage failed"
        }
    }

    private fun readActiveIdLocked(): String? {
        val stored = preferences.getString(ACTIVE_ID, null) ?: return null
        return runCatching { cipher.decrypt(stored) }.getOrNull()
    }

    private fun writeActiveIdLocked(accountId: String?) {
        val editor = preferences.edit()
        if (accountId == null) editor.remove(ACTIVE_ID) else editor.putString(ACTIVE_ID, cipher.encrypt(accountId))
        check(editor.commit()) { "YouTube active account storage failed" }
    }

    /** Hashed so the login cookie never leaves the store as an identifier. */
    private fun accountId(session: YouTubeSession): String {
        val raw = "${YouTubeSessionAuth.loginCookieValue(session.cookie).orEmpty()}:${session.dataSyncId}:${session.accountIndex}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FILE = "orchard_secure_oauth"
        const val SESSION = "youtube_credentials"
        const val ACCOUNTS = "youtube_accounts_v1"
        const val ACTIVE_ID = "youtube_active_account_id"
        const val KEY_ALIAS = "orchard_youtube_oauth_v1"
        /** Delegated ids only, normalized as [YouTubeSessionAuth.normalizeDataSyncId] does. */
        const val DATA_SYNC_ID_FORMAT = 2
    }
}
