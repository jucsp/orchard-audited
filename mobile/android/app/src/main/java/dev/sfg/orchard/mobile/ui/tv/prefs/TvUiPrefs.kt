// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Background behind menus (Home, Library, Settings...). */
enum class TvMenuBackground(val label: String) {
    /** Animated cover while something plays, accent glows otherwise. */
    Auto("Automático"),
    Artwork("Carátula"),
    Glow("Resplandor"),
    Dark("Oscuro"),
}

/** How bright the now-playing backdrop is. The value is the scrim alpha over the cover colours. */
enum class TvBackdropIntensity(val label: String, val playerScrim: Float) {
    Subtle("Sutil", 0.42f),
    Balanced("Equilibrado", 0.30f),
    Vibrant("Vibrante", 0.18f),
}

enum class TvNightCorner(val label: String) {
    BottomEnd("Inferior derecha"),
    BottomStart("Inferior izquierda"),
    TopEnd("Superior derecha"),
}

/** Persisted side-panel choice of the player (A3). */
enum class TvPlayerPanel { Hidden, Lyrics, Queue }

/** Night-mode schedule presets, as [fromHour, toHour) in local time. */
enum class TvNightWindow(val label: String, val fromHour: Int, val toHour: Int) {
    Late("22:00 – 7:00", 22, 7),
    Night("23:00 – 7:00", 23, 7),
    Midnight("00:00 – 8:00", 0, 8),
}

data class TvUiState(
    val menuBackground: TvMenuBackground = TvMenuBackground.Auto,
    val intensity: TvBackdropIntensity = TvBackdropIntensity.Vibrant,
    /** ARGB of the chosen accent swatch; null follows the app theme (Orchard green / system). */
    val accentArgb: Int? = null,
    val playerPanel: TvPlayerPanel = TvPlayerPanel.Lyrics,
    val nightScheduled: Boolean = false,
    val nightWindow: TvNightWindow = TvNightWindow.Night,
    val nightCorner: TvNightCorner = TvNightCorner.BottomEnd,
) {
    fun isNightTime(hour: Int): Boolean {
        val from = nightWindow.fromHour
        val to = nightWindow.toHour
        return if (from <= to) hour in from until to else hour >= from || hour < to
    }
}

/**
 * TV-only UI preferences. Kept out of `OrchardSettings` on purpose so the shared data model and the
 * phone stay untouched (TV-INV-7). Backed by a private SharedPreferences file.
 */
class TvUiPrefs private constructor(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<TvUiState> = _state.asStateFlow()

    fun update(transform: (TvUiState) -> TvUiState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        write(next)
        _state.value = next
    }

    private fun read() = TvUiState(
        menuBackground = enumOr(prefs.getString(K_MENU, null), TvMenuBackground.Auto),
        intensity = enumOr(prefs.getString(K_INTENSITY, null), TvBackdropIntensity.Vibrant),
        accentArgb = if (prefs.contains(K_ACCENT)) prefs.getInt(K_ACCENT, 0) else null,
        playerPanel = enumOr(prefs.getString(K_PANEL, null), TvPlayerPanel.Lyrics),
        nightScheduled = prefs.getBoolean(K_NIGHT_SCHEDULED, false),
        nightWindow = enumOr(prefs.getString(K_NIGHT_WINDOW, null), TvNightWindow.Night),
        nightCorner = enumOr(prefs.getString(K_NIGHT_CORNER, null), TvNightCorner.BottomEnd),
    )

    private fun write(s: TvUiState) {
        prefs.edit().apply {
            putString(K_MENU, s.menuBackground.name)
            putString(K_INTENSITY, s.intensity.name)
            if (s.accentArgb == null) remove(K_ACCENT) else putInt(K_ACCENT, s.accentArgb)
            putString(K_PANEL, s.playerPanel.name)
            putBoolean(K_NIGHT_SCHEDULED, s.nightScheduled)
            putString(K_NIGHT_WINDOW, s.nightWindow.name)
            putString(K_NIGHT_CORNER, s.nightCorner.name)
        }.apply()
    }

    companion object {
        private const val FILE = "orchard_tv_ui"
        private const val K_MENU = "menu_background"
        private const val K_INTENSITY = "backdrop_intensity"
        private const val K_ACCENT = "accent_argb"
        private const val K_PANEL = "player_panel"
        private const val K_NIGHT_SCHEDULED = "night_scheduled"
        private const val K_NIGHT_WINDOW = "night_window"
        private const val K_NIGHT_CORNER = "night_corner"

        @Volatile private var instance: TvUiPrefs? = null

        fun get(context: Context): TvUiPrefs = instance ?: synchronized(this) {
            instance ?: TvUiPrefs(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
            ).also { instance = it }
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
    }
}

@Composable
fun rememberTvUiPrefs(): TvUiPrefs {
    val context = LocalContext.current
    return remember(context) { TvUiPrefs.get(context) }
}

/** Accent swatches offered in Settings → Apariencia (mockup 3A). */
val TvAccentSwatches: List<Int> = listOf(
    0xFF2FDF93.toInt(),
    0xFF8ECAE6.toInt(),
    0xFFFFC46B.toInt(),
    0xFFFF7AB0.toInt(),
    0xFFB69CFF.toInt(),
)
