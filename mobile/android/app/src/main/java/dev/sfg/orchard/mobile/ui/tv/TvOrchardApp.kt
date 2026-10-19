// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import dev.sfg.orchard.mobile.app.OrchardViewModel
import dev.sfg.orchard.mobile.app.openTopLevel
import dev.sfg.orchard.mobile.model.CatalogKind
import dev.sfg.orchard.mobile.model.LibraryFilter
import dev.sfg.orchard.mobile.model.LibrarySnapshot
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.ui.components.ArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.KawarpArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.components.UpdateDialog
import dev.sfg.orchard.mobile.ui.components.WarningBanner
import dev.sfg.orchard.mobile.ui.components.rememberArtworkPalette
import dev.sfg.orchard.mobile.ui.components.rememberPlayerPresentation
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.tv.detail.TvCollectionScreen
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusable
import dev.sfg.orchard.mobile.ui.tv.home.TvHomeScreen
import dev.sfg.orchard.mobile.ui.tv.library.TvLibraryScreen
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
    val warning by viewModel.warning.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val presentation = rememberPlayerPresentation(playback, playbackClock, null)
    val shownPlayback = presentation.playback
    val artworkUrl = shownPlayback.currentTrack?.artworkUrl.orEmpty()

    var railExpanded by remember { mutableStateOf(false) }
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
                startDestination = TvRoutes.HOME,
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
                    )
                }
                composable(TvRoutes.SETTINGS) {
                    TvPendingScreen("Ajustes todavía no está disponible en TV", collapseRail)
                }
                composable(TvRoutes.NOW_PLAYING) {
                    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
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

/** Placeholder for destinations whose TV adaptation is pending; focusable so focus never vanishes. */
@Composable
private fun TvPendingScreen(message: String, onContentFocus: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(TvTokens.ListRowRadius)
    Box(
        Modifier
            .fillMaxSize()
            .padding(start = TvTokens.ContentStartX, end = TvTokens.OverscanHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
            modifier = Modifier
                .focusRequester(focus)
                .tvFocusable(TvFocusStyle.Row, shape, onFocus = onContentFocus)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
