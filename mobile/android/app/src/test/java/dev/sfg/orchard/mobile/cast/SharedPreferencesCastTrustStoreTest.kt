// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.cast

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SharedPreferencesCastTrustStoreTest {
    private class MemoryPrefs : SharedPreferences {
        val values = HashMap<String, Any?>()
        var commits = 0

        override fun getAll(): Map<String, *> = HashMap(values)
        override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }
            override fun putStringSet(key: String, values: Set<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = this
            override fun clear() = this
            override fun commit(): Boolean {
                values.putAll(pending)
                commits++
                return true
            }
            override fun apply() = Unit
        }
    }

    private class PrefsContext(private val prefs: MemoryPrefs) : ContextWrapper(null) {
        val opened = mutableListOf<String>()

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            opened += name
            return prefs
        }
    }

    @Test
    fun pinSurvivesANewStoreInstance() {
        val prefs = MemoryPrefs()
        val context = PrefsContext(prefs)

        SharedPreferencesCastTrustStore(context).setFingerprint("device-a", "AB12")
        val reopened = SharedPreferencesCastTrustStore(context)

        assertEquals("AB12", reopened.getFingerprint("device-a"))
        assertNull(reopened.getFingerprint("device-b"))
        assertEquals(1, prefs.commits)
        assertEquals(SharedPreferencesCastTrustStore.PREFS_NAME, context.opened.first())
        assertEquals("orchard_cast_tofu_pins", SharedPreferencesCastTrustStore.PREFS_NAME)
    }

    @Test
    fun preferencesAreNotOpenedUntilFirstUse() {
        val context = PrefsContext(MemoryPrefs())

        val store = SharedPreferencesCastTrustStore(context)
        assertEquals(emptyList<String>(), context.opened)

        store.getFingerprint("device-a")
        assertEquals(listOf(SharedPreferencesCastTrustStore.PREFS_NAME), context.opened)
    }

    @Test
    fun pinFromAPreviousRunRejectsADifferentCertificate() {
        val context = PrefsContext(MemoryPrefs())
        val first = FakeX509Certificate(byteArrayOf(1, 2, 3, 4), 1L, "CN=cast-device")
        val second = FakeX509Certificate(byteArrayOf(5, 6, 7, 8), 2L, "CN=cast-device")
        CastTrustManager("device-a", SharedPreferencesCastTrustStore(context)).checkServerTrusted(arrayOf(first), "TLS")

        val afterRestart = CastTrustManager("device-a", SharedPreferencesCastTrustStore(context))
        afterRestart.checkServerTrusted(arrayOf(first), "TLS")
        try {
            afterRestart.checkServerTrusted(arrayOf(second), "TLS")
            fail("expected CastSecurityException")
        } catch (expected: CastSecurityException) {
            assertTrue(expected.message!!.contains("mismatch"))
        }
    }
}
