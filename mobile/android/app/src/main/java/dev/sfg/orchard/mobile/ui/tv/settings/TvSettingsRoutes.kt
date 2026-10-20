// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.settings

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.sfg.orchard.mobile.UpdateState
import dev.sfg.orchard.mobile.app.OrchardViewModel
import dev.sfg.orchard.mobile.settings.CacheManager
import dev.sfg.orchard.mobile.ui.screens.SettingsScreen
import dev.sfg.orchard.mobile.ui.tv.TvRoutes
import dev.sfg.orchard.mobile.ui.tv.TvSafeContent
import dev.sfg.orchard.mobile.ui.tv.prefs.TvUiPrefs

/**
 * Settings destinations of the TV NavHost:
 *  - [TvRoutes.SETTINGS]: the TV two-column screen (mockup 3A).
 *  - [TvRoutes.SETTINGS_HOME_LAYOUT]: the move-mode editor (mockup 3B).
 *  - [TvRoutes.SETTINGS_ADVANCED]: the phone [SettingsScreen] inside the safe area, for the long
 *    tail (Last.fm, ListenBrainz token, Qobuz quality, translation provider...).
 */
fun NavGraphBuilder.tvSettingsRoutes(
    nav: NavHostController,
    viewModel: OrchardViewModel,
    tvPrefs: TvUiPrefs,
    collapseRail: () -> Unit,
) {
    composable(TvRoutes.SETTINGS) {
        val context = LocalContext.current
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val auth by viewModel.auth.collectAsStateWithLifecycle()
        val accounts by viewModel.accounts.saved.collectAsStateWithLifecycle()
        val updateState by viewModel.updateState.collectAsStateWithLifecycle()
        val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
        val totalBytesUsed by viewModel.totalBytesUsed.collectAsStateWithLifecycle()
        val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
        val ui by tvPrefs.state.collectAsStateWithLifecycle()
        var selectedId by rememberSaveable { mutableStateOf(TvSettingsCategories.first().id) }
        val selected = TvSettingsCategories.first { it.id == selectedId }

        val model = TvSettingsModel(
            settings = settings,
            ui = ui,
            auth = auth,
            accounts = accounts,
            updateState = updateState,
            cacheSizeBytes = cacheSizeBytes,
            downloadedBytes = totalBytesUsed,
            isClearingCache = isClearingCache,
            onSettings = viewModel::updateSettings,
            onUi = tvPrefs::update,
            onAutoplayEnabled = viewModel::setAutoplayEnabled,
            onSignIn = { nav.navigate(TvRoutes.LOGIN) },
            onSwitchAccount = { nav.navigate(TvRoutes.ACCOUNT_SWITCH) },
            onSignOut = viewModel::signOut,
            onSelectAccount = viewModel.accounts::switchTo,
            onRemoveAccount = viewModel.accounts::remove,
            onWelcome = { nav.navigate(TvRoutes.WELCOME) },
            onHomeLayout = { nav.navigate(TvRoutes.SETTINGS_HOME_LAYOUT) },
            onClearCache = {
                viewModel.clearCache { bytes ->
                    Toast.makeText(context, "Caché limpiada (${CacheManager.formatStorageSize(bytes)} liberados)", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenDownloads = { nav.navigate(TvRoutes.DOWNLOADS) },
            onDeleteAllDownloads = {
                viewModel.removeAllDownloads()
                Toast.makeText(context, "Descargas borradas", Toast.LENGTH_SHORT).show()
            },
            onConnectSpotify = { nav.navigate(TvRoutes.SPOTIFY_LOGIN) },
            onConnectQobuz = { nav.navigate(TvRoutes.SETTINGS_ADVANCED) },
            onCheckForUpdates = viewModel::checkForUpdates,
            onInstallUpdate = { (updateState as? UpdateState.Available)?.let { viewModel.installUpdate(it.metadata) } },
            onAdvanced = { nav.navigate(TvRoutes.SETTINGS_ADVANCED) },
        )

        TvSettingsScreen(
            categories = TvSettingsCategories,
            selected = selected,
            onSelect = {
                selectedId = it.id
                if (it.id == "storage") viewModel.refreshCacheSize()
            },
            items = { tvSettingsItems(it, model) },
            onContentFocus = collapseRail,
        )
    }

    composable(TvRoutes.SETTINGS_HOME_LAYOUT) {
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val auth by viewModel.auth.collectAsStateWithLifecycle()
        TvHomeLayoutScreen(
            settings = settings,
            auth = auth,
            onSettings = viewModel::updateSettings,
            onBack = { nav.popBackStack() },
            onContentFocus = collapseRail,
        )
    }

    composable(TvRoutes.SETTINGS_ADVANCED) {
        val context = LocalContext.current
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val auth by viewModel.auth.collectAsStateWithLifecycle()
        val updateState by viewModel.updateState.collectAsStateWithLifecycle()
        val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
        val totalBytesUsed by viewModel.totalBytesUsed.collectAsStateWithLifecycle()
        val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
        val qobuzStatus by viewModel.qobuzStatus.collectAsStateWithLifecycle()
        val discordStatus by viewModel.discordStatus.collectAsStateWithLifecycle()
        val lastfmState by viewModel.lastfmState.collectAsStateWithLifecycle()
        val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
        TvSafeContent(collapseRail) {
            SettingsScreen(
                settings = settings,
                auth = auth,
                discordStatus = discordStatus,
                lastfmState = lastfmState,
                listenBrainzState = listenBrainzState,
                updateState = updateState,
                cacheSizeBytes = cacheSizeBytes,
                isClearingCache = isClearingCache,
                onClearCache = {
                    viewModel.clearCache { bytes ->
                        Toast.makeText(context, "Caché limpiada (${CacheManager.formatStorageSize(bytes)} liberados)", Toast.LENGTH_SHORT).show()
                    }
                },
                downloadedBytes = totalBytesUsed,
                onDeleteAllDownloads = {
                    viewModel.removeAllDownloads()
                    Toast.makeText(context, "Descargas borradas", Toast.LENGTH_SHORT).show()
                },
                onRefreshCacheSize = viewModel::refreshCacheSize,
                onSettings = viewModel::updateSettings,
                onAutoplayEnabled = viewModel::setAutoplayEnabled,
                onSignIn = { nav.navigate(TvRoutes.LOGIN) },
                onSwitchAccount = { nav.navigate(TvRoutes.ACCOUNT_SWITCH) },
                onSignOut = viewModel::signOut,
                onConnectLastfm = { viewModel.connectLastfm(context) },
                onCompleteLastfm = viewModel::completeLastfmConnection,
                onDisconnectLastfm = viewModel::disconnectLastfm,
                onConnectListenBrainz = viewModel::connectListenBrainz,
                onDisconnectListenBrainz = viewModel::disconnectListenBrainz,
                onConnectSpotify = { nav.navigate(TvRoutes.SPOTIFY_LOGIN) },
                qobuzStatus = qobuzStatus,
                onConnectQobuz = { nav.navigate(TvRoutes.QOBUZ_LOGIN) },
                onDisconnectQobuz = viewModel::disconnectQobuz,
                onQobuzEnabledChange = viewModel::setQobuzEnabled,
                onQobuzQualityChange = viewModel::setQobuzQuality,
                onWelcome = { nav.navigate(TvRoutes.WELCOME) },
                onCheckForUpdates = viewModel::checkForUpdates,
                onInstallUpdate = viewModel::installUpdate,
                onHomeLayout = { nav.navigate(TvRoutes.SETTINGS_HOME_LAYOUT) },
            )
        }
    }
}
