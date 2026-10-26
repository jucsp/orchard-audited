// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.qobuz

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QobuzRepositoryTest {

    private class MemoryPrefs : SharedPreferences {
        val values = HashMap<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(values)
        override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removed = HashSet<String>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value; removed.remove(key); return this
            }
            override fun putStringSet(key: String, values: Set<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value; removed.remove(key); return this
            }
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value; removed.remove(key); return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                removed.add(key); pending.remove(key); return this
            }
            override fun clear(): SharedPreferences.Editor {
                removed.addAll(values.keys); pending.clear(); return this
            }
            override fun commit(): Boolean {
                values.putAll(pending)
                for (key in removed) values.remove(key)
                return true
            }
            override fun apply() = Unit
        }
    }

    private class PrefsContext(private val prefs: MemoryPrefs) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
    }

    private class FakeCipher : QobuzTokenCipher {
        private val encryptedPrefix = "enc:v1:"
        override fun encrypt(value: String): String = encryptedPrefix + value
        override fun decrypt(value: String): String = value.removePrefix(encryptedPrefix)
        override fun isEncrypted(value: String): Boolean = value.startsWith(encryptedPrefix)
    }

    private fun repository(prefs: MemoryPrefs, cipher: QobuzTokenCipher = FakeCipher()): QobuzRepository =
        QobuzRepository(PrefsContext(prefs), CoroutineScope(Dispatchers.Unconfined), cipher)

    @Test
    fun migratesPlainTokenToEncryptedOnFirstRead() {
        val prefs = MemoryPrefs()
        prefs.values["qobuz_token"] = "plain-token"
        prefs.values["qobuz_user_id"] = 123L
        prefs.values["qobuz_enabled"] = true

        val repo = repository(prefs)

        assertEquals(QobuzSession("plain-token", 123L), repo.getCredentials())
        assertEquals("enc:v1:plain-token", prefs.values["qobuz_token"])
        assertTrue(prefs.getString("qobuz_token", "").orEmpty().startsWith("enc:v1:"))
        assertTrue(repo.status.value.enabled)
    }

    @Test
    fun connectStoresOnlyEncryptedToken() {
        val prefs = MemoryPrefs()
        val repo = repository(prefs)

        repo.connect("new-token", 456L)

        assertEquals(QobuzSession("new-token", 456L), repo.getCredentials())
        assertEquals("enc:v1:new-token", prefs.values["qobuz_token"])
        assertEquals(456L, prefs.values["qobuz_user_id"])
        assertTrue(repo.status.value.enabled)
    }

    @Test
    fun getCredentialsReturnsNullWhenAbsent() {
        assertNull(repository(MemoryPrefs()).getCredentials())
    }
}
