// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import dev.sfg.orchard.connect.BuildConfig
import dev.sfg.orchard.mobile.UpdateState
import dev.sfg.orchard.mobile.auth.AuthState
import dev.sfg.orchard.mobile.model.AudioQuality
import dev.sfg.orchard.mobile.model.OrchardSettings
import dev.sfg.orchard.mobile.settings.CacheManager
import dev.sfg.orchard.mobile.ui.screens.label
import dev.sfg.orchard.mobile.ui.tv.prefs.TvAccentSwatches
import dev.sfg.orchard.mobile.ui.tv.prefs.TvBackdropIntensity
import dev.sfg.orchard.mobile.ui.tv.prefs.TvMenuBackground
import dev.sfg.orchard.mobile.ui.tv.prefs.TvNightCorner
import dev.sfg.orchard.mobile.ui.tv.prefs.TvNightWindow
import dev.sfg.orchard.mobile.ui.tv.prefs.TvPlayerPanel
import dev.sfg.orchard.mobile.ui.tv.prefs.TvUiState

val TvSettingsCategories: List<TvSettingsCategory> = listOf(
    TvSettingsCategory("account", "Cuenta", Icons.Rounded.Person),
    TvSettingsCategory("appearance", "Apariencia", Icons.Rounded.Palette),
    TvSettingsCategory("playback", "Reproducción", Icons.Rounded.PlayCircle),
    TvSettingsCategory("lyrics", "Letras", Icons.AutoMirrored.Rounded.Subject),
    TvSettingsCategory("storage", "Almacenamiento", Icons.Rounded.Storage),
    TvSettingsCategory("integrations", "Integraciones", Icons.Rounded.Extension),
    TvSettingsCategory("updates", "Actualizaciones", Icons.Rounded.SystemUpdate),
    TvSettingsCategory("about", "Acerca de", Icons.Rounded.Info),
)

/** Everything the pages read, plus the callbacks they fire. Built once per recomposition by the route. */
class TvSettingsModel(
    val settings: OrchardSettings,
    val ui: TvUiState,
    val auth: AuthState,
    val updateState: UpdateState,
    val cacheSizeBytes: Long,
    val downloadedBytes: Long,
    val isClearingCache: Boolean,
    val onSettings: (OrchardSettings) -> Unit,
    val onUi: ((TvUiState) -> TvUiState) -> Unit,
    val onAutoplayEnabled: (Boolean) -> Unit,
    val onSignIn: () -> Unit,
    val onSwitchAccount: () -> Unit,
    val onSignOut: () -> Unit,
    val onWelcome: () -> Unit,
    val onHomeLayout: () -> Unit,
    val onClearCache: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onDeleteAllDownloads: () -> Unit,
    val onConnectSpotify: () -> Unit,
    val onConnectQobuz: () -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onInstallUpdate: () -> Unit,
    val onAdvanced: () -> Unit,
)

fun tvSettingsItems(category: TvSettingsCategory, m: TvSettingsModel): List<TvSettingItem> = when (category.id) {
    "account" -> accountItems(m)
    "appearance" -> appearanceItems(m)
    "playback" -> playbackItems(m)
    "lyrics" -> lyricsItems(m)
    "storage" -> storageItems(m)
    "integrations" -> integrationItems(m)
    "updates" -> updateItems(m)
    else -> aboutItems(m)
}

private fun accountItems(m: TvSettingsModel): List<TvSettingItem> = buildList {
    when (val auth = m.auth) {
        is AuthState.SignedIn -> {
            add(TvSettingItem.Action(auth.displayName ?: "Tu cuenta", "Cuenta de YouTube Music conectada", m.onSwitchAccount))
            add(TvSettingItem.Action("Cambiar de cuenta", null, m.onSwitchAccount))
            add(TvSettingItem.Action("Cerrar sesión", null, m.onSignOut, destructive = true))
        }
        else -> add(TvSettingItem.Action("Iniciar sesión", "Escanea un código QR con tu teléfono", m.onSignIn))
    }
    add(TvSettingItem.Action("Asistente de bienvenida", "Vuelve a ver la configuración inicial", m.onWelcome))
}

private fun appearanceItems(m: TvSettingsModel): List<TvSettingItem> {
    val s = m.settings
    val ui = m.ui
    return listOf(
        TvSettingItem.Header("Fondo"),
        TvSettingItem.Toggle("Fondo animado", "La carátula se mueve detrás del reproductor", s.animatedBackground) {
            m.onSettings(s.copy(animatedBackground = it))
        },
        TvSettingItem.Toggle("Carátula animada", "Portadas en movimiento cuando existen", s.animatedArtwork) {
            m.onSettings(s.copy(animatedArtwork = it))
        },
        TvSettingItem.Choice("Intensidad del fondo", ui.intensity.label) {
            m.onUi { it.copy(intensity = it.intensity.next()) }
        },
        TvSettingItem.Choice("Fondo de los menús", ui.menuBackground.label) {
            m.onUi { it.copy(menuBackground = it.menuBackground.next()) }
        },
        TvSettingItem.Header("Color"),
        TvSettingItem.Toggle("Colores del sistema", "Usar el color de acento de Android", s.useSystemColors) {
            m.onSettings(s.copy(useSystemColors = it))
        },
        TvSettingItem.Action("Diseño de Inicio", "Ordena y oculta las secciones", m.onHomeLayout),
        TvSettingItem.Swatches("Color de acento", listOf<Int?>(null) + TvAccentSwatches, ui.accentArgb) { argb ->
            m.onUi { it.copy(accentArgb = argb) }
            // A manual swatch only shows if the system accent is not overriding it.
            if (argb != null && s.useSystemColors) m.onSettings(s.copy(useSystemColors = false))
        },
        TvSettingItem.Header("Modo nocturno"),
        TvSettingItem.Toggle("Modo nocturno programado", "Entra solo al abrir el reproductor en este horario", ui.nightScheduled) { on ->
            m.onUi { it.copy(nightScheduled = on) }
        },
        TvSettingItem.Choice("Horario", ui.nightWindow.label) {
            m.onUi { it.copy(nightWindow = it.nightWindow.next()) }
        },
        TvSettingItem.Choice("Esquina del mini reproductor", ui.nightCorner.label) {
            m.onUi { it.copy(nightCorner = it.nightCorner.next()) }
        },
    )
}

private fun playbackItems(m: TvSettingsModel): List<TvSettingItem> {
    val s = m.settings
    return listOf(
        TvSettingItem.Choice("Calidad de audio", s.audioQuality.label) {
            m.onSettings(s.copy(audioQuality = s.audioQuality.next()))
        },
        TvSettingItem.Toggle("Reproducción automática", "Sigue con canciones similares al terminar la cola", s.autoplayEnabled) {
            m.onAutoplayEnabled(it)
        },
        TvSettingItem.Toggle("Crossfade", "Funde el final de una canción con la siguiente", s.crossfadeEnabled) {
            m.onSettings(s.copy(crossfadeEnabled = it))
        },
        TvSettingItem.Choice("Duración del crossfade", "${s.crossfadeSeconds} s") {
            val next = if (s.crossfadeSeconds >= OrchardSettings.MAX_CROSSFADE_SECONDS) {
                OrchardSettings.MIN_CROSSFADE_SECONDS
            } else {
                s.crossfadeSeconds + 1
            }
            m.onSettings(s.copy(crossfadeSeconds = next))
        },
        TvSettingItem.Toggle("Normalizar volumen", null, s.volumeNormalizationEnabled) {
            m.onSettings(s.copy(volumeNormalizationEnabled = it))
        },
        TvSettingItem.Toggle("Spotify Canvas", "Videos cortos de fondo cuando existen", s.spotifyCanvasEnabled) {
            m.onSettings(s.copy(spotifyCanvasEnabled = it))
        },
        TvSettingItem.Toggle("Enviar historial a YouTube", null, s.sendYouTubeHistory) {
            m.onSettings(s.copy(sendYouTubeHistory = it))
        },
    )
}

private fun lyricsItems(m: TvSettingsModel): List<TvSettingItem> = listOf(
    TvSettingItem.Choice("Panel al abrir el reproductor", m.ui.playerPanel.label()) {
        m.onUi { it.copy(playerPanel = it.playerPanel.next()) }
    },
    TvSettingItem.Toggle("Traducir letras", null, m.settings.translateLyrics) {
        m.onSettings(m.settings.copy(translateLyrics = it))
    },
    TvSettingItem.Action("Proveedor de traducción", "Se configura en Ajustes avanzados", m.onAdvanced),
)

private fun storageItems(m: TvSettingsModel): List<TvSettingItem> = listOf(
    TvSettingItem.Action(
        if (m.isClearingCache) "Limpiando caché…" else "Limpiar caché",
        "En uso: ${CacheManager.formatStorageSize(m.cacheSizeBytes)}",
        m.onClearCache,
    ),
    TvSettingItem.Action("Descargas", "En uso: ${CacheManager.formatStorageSize(m.downloadedBytes)}", m.onOpenDownloads),
    TvSettingItem.Toggle("Descargar carátulas animadas", null, m.settings.downloadAnimatedArtwork) {
        m.onSettings(m.settings.copy(downloadAnimatedArtwork = it))
    },
    TvSettingItem.Action("Borrar todas las descargas", null, m.onDeleteAllDownloads, destructive = true),
)

private fun integrationItems(m: TvSettingsModel): List<TvSettingItem> = listOf(
    TvSettingItem.Toggle("Discord Rich Presence", "Muestra lo que escuchas en Discord", m.settings.discordPresenceEnabled) {
        m.onSettings(m.settings.copy(discordPresenceEnabled = it))
    },
    TvSettingItem.Action(
        "Spotify",
        if (m.settings.spotifySpdc.isNullOrBlank()) "Conectar para Canvas y letras" else "Conectado · Volver a conectar",
        m.onConnectSpotify,
    ),
    TvSettingItem.Action("Qobuz", "Conectar o cambiar la calidad", m.onConnectQobuz),
    TvSettingItem.Action("Last.fm y ListenBrainz", "Scrobbling · en Ajustes avanzados", m.onAdvanced),
)

private fun updateItems(m: TvSettingsModel): List<TvSettingItem> = buildList {
    val state = m.updateState
    val status = when (state) {
        is UpdateState.Available -> "Disponible: ${state.metadata.version}"
        is UpdateState.Downloading -> "Descargando…"
        is UpdateState.ReadyToInstall -> "Lista para instalar"
        is UpdateState.Failed -> "Falló la última búsqueda"
        else -> "Versión actual ${BuildConfig.VERSION_NAME}"
    }
    add(TvSettingItem.Action("Buscar actualizaciones", status, m.onCheckForUpdates))
    if (state is UpdateState.Available || state is UpdateState.ReadyToInstall) {
        add(TvSettingItem.Action("Instalar actualización", null, m.onInstallUpdate))
    }
    add(TvSettingItem.Toggle("Canal beta", "Recibe versiones de prueba antes", m.settings.betaChannelEnabled) {
        m.onSettings(m.settings.copy(betaChannelEnabled = it))
    })
}

private fun aboutItems(m: TvSettingsModel): List<TvSettingItem> = listOf(
    TvSettingItem.Action("Orchard ${BuildConfig.VERSION_NAME}", "Licencia AGPL-3.0-or-later", {}),
    TvSettingItem.Action("Ajustes avanzados", "Todas las opciones de la app del teléfono", m.onAdvanced),
)

private fun TvPlayerPanel.label() = when (this) {
    TvPlayerPanel.Lyrics -> "Letras"
    TvPlayerPanel.Queue -> "Cola"
    TvPlayerPanel.Hidden -> "Inmersivo"
}

private fun TvMenuBackground.next() = TvMenuBackground.entries[(ordinal + 1) % TvMenuBackground.entries.size]
private fun TvBackdropIntensity.next() = TvBackdropIntensity.entries[(ordinal + 1) % TvBackdropIntensity.entries.size]
private fun TvNightWindow.next() = TvNightWindow.entries[(ordinal + 1) % TvNightWindow.entries.size]
private fun TvNightCorner.next() = TvNightCorner.entries[(ordinal + 1) % TvNightCorner.entries.size]
private fun TvPlayerPanel.next() = TvPlayerPanel.entries[(ordinal + 1) % TvPlayerPanel.entries.size]
/** MAX needs a linked Qobuz account, so the TV cycle skips it; it stays selectable in Ajustes avanzados. */
private fun AudioQuality.next(): AudioQuality {
    val choices = AudioQuality.entries.filter { it != AudioQuality.MAX }
    return choices[(choices.indexOf(this) + 1) % choices.size]
}
