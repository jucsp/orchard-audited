// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sfg.orchard.mobile.OrchardGraph
import dev.sfg.orchard.mobile.app.OrchardViewModel
import dev.sfg.orchard.mobile.app.openTopLevel
import dev.sfg.orchard.mobile.auth.AuthState
import dev.sfg.orchard.mobile.model.CatalogKind
import dev.sfg.orchard.mobile.model.LibraryFilter
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.KawarpArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.components.UpdateDialog
import dev.sfg.orchard.mobile.ui.components.WarningBanner
import dev.sfg.orchard.mobile.ui.components.rememberArtworkPalette
import dev.sfg.orchard.mobile.ui.components.rememberPlayerPresentation
import dev.sfg.orchard.mobile.ui.screens.DownloadsScreen
import dev.sfg.orchard.mobile.ui.screens.NativeLoginScreen
import dev.sfg.orchard.mobile.ui.screens.QobuzLoginScreen
import dev.sfg.orchard.mobile.ui.screens.SettingsHomeLayout
import dev.sfg.orchard.mobile.ui.screens.SettingsScreen
import dev.sfg.orchard.mobile.ui.screens.SpotifyLoginScreen
import dev.sfg.orchard.mobile.ui.screens.WelcomeScreen
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.tv.detail.TvCollectionScreen
import dev.sfg.orchard.mobile.ui.tv.home.TvHomeScreen
import dev.sfg.orchard.mobile.ui.tv.library.TvLibraryScreen
import dev.sfg.orchard.mobile.ui.tv.player.TvMusicVideoScreen
import dev.sfg.orchard.mobile.ui.tv.player.TvNowPlaying
import dev.sfg.orchard.mobile.ui.tv.rail.TvNavigationRail
import dev.sfg.orchard.mobile.ui.tv.search.TvSearchScreen
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/**
 * Root of the Android TV tree: ambient backdrop, NavHost and the rail on top of it, then the
 * warning banner and the updater dialog. Shares [OrchardViewModel] with the phone tree and adds no
 * state of its own beyond navigation and rail expansion.
 */
@Composable
fun TvOrchardApp(viewModel: OrchardViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val playbackClock = viewModel.playbackClock.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val warning by viewModel.warning.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadsList = remember(downloads) { downloads.values.toList() }
    val totalBytesUsed by viewModel.totalBytesUsed.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
    val qobuzStatus by viewModel.qobuzStatus.collectAsStateWithLifecycle()
    val discordStatus by viewModel.discordStatus.collectAsStateWithLifecycle()
    val lastfmState by viewModel.lastfmState.collectAsStateWithLifecycle()
    val listenBrainzState by viewModel.listenBrainzState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val signedIn = auth is AuthState.SignedIn

    val presentation = rememberPlayerPresentation(playback, playbackClock, null)
    val shownPlayback = presentation.playback
    val artworkUrl = shownPlayback.currentTrack?.artworkUrl.orEmpty()

    var railExpanded by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    val focusManager = LocalFocusManager.current
    val collapseRail = { railExpanded = false }
    val openDetail: (String) -> Unit = { id ->
        viewModel.openDetail(id)
        nav.navigate(TvRoutes.detail(id))
    }

    // Back first closes the expanded rail and returns focus to the content it covered.
    BackHandler(enabled = railExpanded) {
        railExpanded = false
        focusManager.moveFocus(FocusDirection.Right)
    }

    CompositionLocalProvider(LocalPlayerClock provides presentation.clock) {
        Box(Modifier.fillMaxSize().background(CanopyColors.Chrome)) {
            TvBackdrop(
                artworkUrl = artworkUrl,
                animate = shownPlayback.isPlaying && settings.animatedBackground,
            )

            NavHost(
                navController = nav,
                startDestination = if (settings.onboardingCompleted) TvRoutes.HOME else TvRoutes.WELCOME,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TvRoutes.HOME) {
                    val home by viewModel.home.collectAsStateWithLifecycle()
                    TvHomeScreen(
                        state = home,
                        library = library,
                        onPlay = { viewModel.play(it, "Inicio") },
                        onOpenDetail = openDetail,
                        onPlayCollection = { id, title -> viewModel.playCollection(id, title) },
                        onPlayItem = { item, shuffle -> viewModel.playItem(item, shuffle) },
                        onContentFocus = collapseRail,
                        signedIn = signedIn,
                        onSignIn = { nav.navigate(TvRoutes.LOGIN) },
                        onGoToSettings = { nav.openTopLevel(TvRoutes.SETTINGS) },
                        onTrackActions = { actionTrack = it },
                    )
                }
                composable(TvRoutes.SEARCH) {
                    val query by viewModel.query.collectAsStateWithLifecycle()
                    val results by viewModel.search.collectAsStateWithLifecycle()
                    TvSearchScreen(
                        query = query,
                        results = results,
                        onQueryChange = viewModel::updateQuery,
                        onOpenDetail = { id ->
                            viewModel.runSearch(query)
                            openDetail(id)
                        },
                        onPlay = { track ->
                            viewModel.runSearch(query)
                            viewModel.play(track, "Buscar")
                        },
                        onContentFocus = collapseRail,
                    )
                }
                composable(TvRoutes.LIBRARY) {
                    val filter by viewModel.libraryFilter.collectAsStateWithLifecycle()
                    TvLibraryScreen(
                        library = library,
                        // Local files are hidden on TV (D1); fall back to playlists.
                        filter = filter.takeUnless { it == LibraryFilter.LOCAL } ?: LibraryFilter.PLAYLISTS,
                        onFilterChange = viewModel::selectLibraryFilter,
                        onOpenDetail = openDetail,
                        onPlay = { viewModel.play(it, "Biblioteca") },
                        onContentFocus = collapseRail,
                        signedIn = signedIn,
                        onSignIn = { nav.navigate(TvRoutes.LOGIN) },
                        onGoToSettings = { nav.openTopLevel(TvRoutes.SETTINGS) },
                        onTrackActions = { actionTrack = it },
                        onOpenDownloads = { nav.navigate(TvRoutes.DOWNLOADS) },
                    )
                }
                composable(TvRoutes.WELCOME) {
                    TvSafeContent(collapseRail) {
                        WelcomeScreen(
                            settings = settings,
                            auth = auth,
                            onUpdateSettings = viewModel::updateSettings,
                            onSignIn = { nav.navigate(TvRoutes.LOGIN) },
                            onSignOut = viewModel::signOut,
                            onFinish = {
                                viewModel.updateSettings(settings.copy(onboardingCompleted = true))
                                nav.navigate(TvRoutes.HOME) {
                                    popUpTo(TvRoutes.WELCOME) { inclusive = true }
                                }
                            },
                        )
                    }
                }
                tvLoginRoutes(nav, viewModel, collapseRail)
                composable(TvRoutes.ACCOUNT_SWITCH) {
                    TvSafeContent(collapseRail) {
                        NativeLoginScreen(
                            auth = auth,
                            onBegin = viewModel::beginSignIn,
                            onSession = { cookie, visitorData, dataSyncId, accountIndex, avatarUrl ->
                                viewModel.completeSignIn(
                                    cookie, visitorData, dataSyncId, accountIndex,
                                    switchingAccount = true, pageAvatarUrl = avatarUrl,
                                )
                            },
                            onCancel = viewModel::cancelSignIn,
                            onComplete = { nav.popBackStack(TvRoutes.ACCOUNT_SWITCH, inclusive = true) },
                            switchingAccount = true,
                            initialSession = viewModel.youtubeSession(),
                        )
                    }
                }
                composable(TvRoutes.SPOTIFY_LOGIN) {
                    TvSafeContent(collapseRail) {
                        SpotifyLoginScreen(
                            onSpdcCaptured = { spdc ->
                                viewModel.updateSettings(settings.copy(spotifySpdc = spdc))
                                nav.popBackStack()
                            },
                            onCancel = { nav.popBackStack() },
                        )
                    }
                }
                composable(TvRoutes.QOBUZ_LOGIN) {
                    TvSafeContent(collapseRail) {
                        val graph = OrchardGraph.from(context)
                        QobuzLoginScreen(
                            qobuz = graph.qobuzResolver,
                            onSuccess = { token, userId ->
                                viewModel.connectQobuz(token, userId)
                                nav.popBackStack()
                            },
                            onCancel = { nav.popBackStack() },
                        )
                    }
                }
                composable(TvRoutes.DOWNLOADS) {
                    TvSafeContent(collapseRail) {
                        DownloadsScreen(
                            downloads = downloadsList,
                            totalBytesUsed = totalBytesUsed,
                            onPlay = { viewModel.play(it, "Downloads") },
                            onRemoveDownload = viewModel::removeDownload,
                        )
                    }
                }
                composable(TvRoutes.SETTINGS) {
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
                                viewModel.clearCache { bytesCleared ->
                                    val formatted = dev.sfg.orchard.mobile.settings.CacheManager.formatStorageSize(bytesCleared)
                                    android.widget.Toast.makeText(context, "Cache cleared ($formatted freed)", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            downloadedBytes = totalBytesUsed,
                            onDeleteAllDownloads = {
                                viewModel.removeAllDownloads()
                                android.widget.Toast.makeText(context, "Downloads deleted", android.widget.Toast.LENGTH_SHORT).show()
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
                composable(TvRoutes.SETTINGS_HOME_LAYOUT) {
                    TvSafeContent(collapseRail) {
                        SettingsHomeLayout(
                            settings = settings,
                            auth = auth,
                            onSettings = viewModel::updateSettings,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable(TvRoutes.NOW_PLAYING) {
                    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
                    val musicVideo by viewModel.musicVideo.collectAsStateWithLifecycle()
                    val track = shownPlayback.currentTrack
                    TvNowPlaying(
                        playback = shownPlayback,
                        lyrics = lyrics,
                        liked = track != null && library.likedTracks.any { it.id == track.id },
                        onToggle = viewModel::togglePlayback,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onSeek = viewModel::seek,
                        onShuffle = viewModel::toggleShuffle,
                        onRepeat = viewModel::cycleRepeat,
                        onToggleLike = { track?.let(viewModel::toggleLiked) },
                        onPlayQueueIndex = viewModel::playQueueIndex,
                        onContentFocus = collapseRail,
                        hasVideo = musicVideo.available,
                        onOpenMusicVideo = { nav.navigate(TvRoutes.MUSIC_VIDEO) { launchSingleTop = true } },
                        onOpenListeningParty = { nav.navigate(TvRoutes.LISTENING_PARTY) { launchSingleTop = true } },
                        onTrackActions = { actionTrack = it },
                    )
                }
                composable(TvRoutes.MUSIC_VIDEO) {
                    val musicVideo by viewModel.musicVideo.collectAsStateWithLifecycle()
                    val videoPlayer by viewModel.videoPlayer.collectAsStateWithLifecycle()
                    TvMusicVideoScreen(
                        musicVideo = musicVideo,
                        player = videoPlayer,
                        track = shownPlayback.currentTrack,
                        playback = shownPlayback,
                        onToggle = viewModel::togglePlayback,
                        onToggleMusicVideo = viewModel::toggleMusicVideo,
                        onSeek = viewModel::seek,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onContentFocus = collapseRail,
                        onExit = { nav.popBackStack() },
                    )
                }
                composable(TvRoutes.LISTENING_PARTY) {
                    val party by viewModel.listeningParty.collectAsStateWithLifecycle()
                    TvListeningPartyScreen(
                        party = party,
                        onBack = { nav.popBackStack() },
                        onCreate = { viewModel.createListeningParty() },
                        onJoin = { code -> viewModel.joinListeningParty(code) },
                        onLeave = { viewModel.leaveListeningParty() },
                        onContentFocus = collapseRail,
                    )
                }
                composable(
                    route = TvRoutes.DETAIL_PATTERN,
                    arguments = listOf(navArgument(TvRoutes.DETAIL_ARG) { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString(TvRoutes.DETAIL_ARG).orEmpty()
                    val detail by viewModel.detail.collectAsStateWithLifecycle()
                    // A restored back stack can outlive the loaded detail; reload the route's id.
                    LaunchedEffect(id) {
                        val current = (detail as? LoadState.Content)?.value?.id
                        if (id.isNotBlank() && current != id) viewModel.openDetail(id)
                    }
                    val loaded = (detail as? LoadState.Content)?.value
                    TvCollectionScreen(
                        state = detail,
                        isSaved = loaded != null && library.isSaved(loaded.kind, loaded.id),
                        currentTrackId = shownPlayback.currentTrack?.id,
                        onPlayAll = { tracks, source -> viewModel.playAll(tracks, contextTitle = source) },
                        onShuffle = { tracks, source -> viewModel.shuffleAll(tracks, source) },
                        onPlayTrack = { tracks, index, source ->
                            viewModel.playAll(tracks, startIndex = index, contextTitle = source)
                        },
                        onSave = { loaded?.let(viewModel::saveDetail) },
                        onContentFocus = collapseRail,
                        onTrackActions = { actionTrack = it },
                    )
                }
            }

            TvNavigationRail(
                currentRoute = route,
                expanded = railExpanded,
                nowPlayingTrack = shownPlayback.currentTrack,
                onSelect = { target ->
                    railExpanded = false
                    if (target != route) nav.openTopLevel(target)
                },
                onOpenNowPlaying = {
                    railExpanded = false
                    nav.navigate(TvRoutes.NOW_PLAYING) { launchSingleTop = true }
                },
                onRailFocus = { railExpanded = true },
                modifier = Modifier.align(Alignment.CenterStart).zIndex(10f),
            )

            WarningBanner(
                message = warning,
                onDismiss = viewModel::dismissWarning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TvTokens.OverscanVertical)
                    .zIndex(100f),
            )

            UpdateDialog(
                state = updateState,
                onInstall = viewModel::installUpdate,
                onDismiss = viewModel::dismissUpdate,
            )

            actionTrack?.let { track ->
                TvTrackActionsHost(
                    track = track,
                    liked = library.likedTracks.any { it.id == track.id },
                    playlists = viewModel.playlistChoices(track, library.savedPlaylists),
                    onDismiss = { actionTrack = null },
                    onPlay = { viewModel.play(it) },
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onAddToPlaylist = { playlistId, item -> viewModel.addTrackToPlaylist(playlistId, item) },
                    onToggleLike = viewModel::toggleLiked,
                    onOpenDetail = openDetail,
                )
            }
        }
    }
}

private fun LibrarySnapshot.isSaved(kind: CatalogKind, id: String): Boolean = when (kind) {
    CatalogKind.ALBUM -> savedAlbums.any { it.id == id }
    CatalogKind.PLAYLIST -> savedPlaylists.any { it.id == id }
    CatalogKind.ARTIST -> savedArtists.any { it.id == id }
    CatalogKind.TRACK -> false
}

/**
 * Palette wash always; the Kawarp warp only on devices that are not low-RAM, at half resolution and
 * 30 fps (TV-INV-5). The scrim keeps text over it legible.
 */
@Composable
private fun TvBackdrop(artworkUrl: String, animate: Boolean) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
    val palette = rememberArtworkPalette(artworkUrl)
    ArtworkBackdrop(palette = palette, animated = animate && !lowRam)
    if (!lowRam && artworkUrl.isNotBlank()) {
        KawarpArtworkBackdrop(
            artworkUrl = artworkUrl,
            isPlaying = animate,
            renderScale = 0.5f,
            fullFrameRate = false,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.70f),
                )
            )
        )
    }
}

