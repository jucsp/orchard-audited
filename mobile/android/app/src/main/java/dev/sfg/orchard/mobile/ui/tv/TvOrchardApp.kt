// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
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
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.components.UpdateDialog
import dev.sfg.orchard.mobile.ui.components.WarningBanner
import dev.sfg.orchard.mobile.ui.components.rememberPlayerPresentation
import dev.sfg.orchard.mobile.ui.screens.DownloadsScreen
import dev.sfg.orchard.mobile.ui.screens.NativeLoginScreen
import dev.sfg.orchard.mobile.ui.screens.QobuzLoginScreen
import dev.sfg.orchard.mobile.ui.screens.SpotifyLoginScreen
import dev.sfg.orchard.mobile.ui.screens.WelcomeScreen
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.detail.TvCollectionScreen
import dev.sfg.orchard.mobile.ui.tv.home.TvHomeScreen
import dev.sfg.orchard.mobile.ui.tv.library.TvLibraryScreen
import dev.sfg.orchard.mobile.ui.tv.player.TvMusicVideoScreen
import dev.sfg.orchard.mobile.ui.tv.player.TvNowPlaying
import dev.sfg.orchard.mobile.ui.tv.prefs.rememberTvUiPrefs
import dev.sfg.orchard.mobile.ui.tv.rail.TvNavigationRail
import dev.sfg.orchard.mobile.ui.tv.search.TvSearchScreen
import dev.sfg.orchard.mobile.ui.tv.settings.tvSettingsRoutes
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens
import java.util.Calendar

/**
 * Root of the Android TV tree: ambient backdrop, NavHost, floating rail, warning banner and the
 * updater dialog. Shares [OrchardViewModel] with the phone tree. TV-only state is navigation, rail
 * expansion, the session's night mode and [dev.sfg.orchard.mobile.ui.tv.prefs.TvUiPrefs].
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
    val tvPrefs = rememberTvUiPrefs()
    val ui by tvPrefs.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val signedIn = auth is AuthState.SignedIn

    val presentation = rememberPlayerPresentation(playback, playbackClock, null)
    val shownPlayback = presentation.playback
    val artworkUrl = shownPlayback.currentTrack?.artworkUrl.orEmpty()

    val immersive = route in TvRoutes.immersive
    var railExpanded by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    // Session-only: night mode is never restored on the next launch, only by the schedule.
    var nightMode by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val collapseRail = { railExpanded = false }
    val openDetail: (String) -> Unit = { id ->
        viewModel.openDetail(id)
        nav.navigate(TvRoutes.detail(id))
    }

    // Back first closes the expanded rail and returns focus to the content it pushed aside.
    // Composed only while expanded so it registers after the NavHost's own handler and wins on
    // pushed screens (detail, downloads...), where the NavHost would otherwise pop instead.
    if (railExpanded && !immersive) {
        BackHandler {
            railExpanded = false
            focusManager.moveFocus(FocusDirection.Right)
        }
    }
    // Back dismisses the track-actions sheet first.
    BackHandler(enabled = actionTrack != null) {
        actionTrack = null
    }
    // Back exits the app only at a collapsed top-level Home, without night mode or a dialog open.
    BackHandler(enabled = route == TvRoutes.HOME && !railExpanded && !nightMode && actionTrack == null) {
        (context as? Activity)?.finish()
    }
    LaunchedEffect(immersive) { if (immersive) railExpanded = false }

    // Scheduled night mode: checked once each time the player opens, so Back still wins afterwards.
    LaunchedEffect(route) {
        when (route) {
            TvRoutes.NOW_PLAYING -> if (ui.nightScheduled && ui.isNightTime(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) {
                nightMode = true
            }
            else -> nightMode = false
        }
    }

    // Accent: a TV swatch wins unless the user asked for the system colours.
    val themeAccent = LocalAccent.current
    val accent = ui.accentArgb?.takeIf { !settings.useSystemColors }?.let { Color(it) } ?: themeAccent

    // Rail push (Spec §4): content slides right and dims instead of being covered.
    val density = LocalDensity.current
    val push by animateDpAsState(if (railExpanded && !immersive) TvTokens.RailPush else 0.dp, tween(220), label = "railPush")
    val dim by animateFloatAsState(if (railExpanded && !immersive) 0.45f else 0f, tween(220), label = "railDim")

    CompositionLocalProvider(
        LocalPlayerClock provides presentation.clock,
        LocalAccent provides accent,
    ) {
        Box(Modifier.fillMaxSize().background(CanopyColors.Chrome)) {
            TvAmbientBackdrop(
                artworkUrl = artworkUrl,
                isPlaying = shownPlayback.isPlaying,
                animatedSetting = settings.animatedBackground,
                role = when {
                    route == TvRoutes.NOW_PLAYING && nightMode -> TvBackdropRole.Night
                    immersive -> TvBackdropRole.Player
                    else -> TvBackdropRole.Menu
                },
                ui = ui,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = with(density) { push.toPx() } },
            ) {
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
                    tvSettingsRoutes(nav, viewModel, tvPrefs, collapseRail)
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
                            onOpenMusicVideo = {
                                if (!musicVideo.playing) viewModel.toggleMusicVideo()
                                nav.navigate(TvRoutes.MUSIC_VIDEO) { launchSingleTop = true }
                            },
                            onOpenListeningParty = { nav.navigate(TvRoutes.LISTENING_PARTY) { launchSingleTop = true } },
                            onTrackActions = { actionTrack = it },
                            ui = ui,
                            onPanelChange = { panel -> tvPrefs.update { it.copy(playerPanel = panel) } },
                            nightMode = nightMode,
                            onNightModeChange = { nightMode = it },
                        )
                    }
                    composable(TvRoutes.MUSIC_VIDEO) {
                        val musicVideo by viewModel.musicVideo.collectAsStateWithLifecycle()
                        val videoPlayer by viewModel.videoPlayer.collectAsStateWithLifecycle()
                        val exitVideo = {
                            if (musicVideo.playing) viewModel.toggleMusicVideo()
                            nav.popBackStack()
                        }
                        TvMusicVideoScreen(
                            musicVideo = musicVideo,
                            player = videoPlayer,
                            track = shownPlayback.currentTrack,
                            playback = shownPlayback,
                            onToggle = viewModel::togglePlayback,
                            onToggleMusicVideo = {
                                viewModel.toggleMusicVideo()
                                nav.popBackStack()
                            },
                            onSeek = viewModel::seek,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            onContentFocus = collapseRail,
                            onExit = exitVideo,
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
                // Dim the pushed content while the rail is open.
                if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            }

            if (!immersive) {
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
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = TvTokens.RailStart)
                        .zIndex(10f),
                )
            }

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
