// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.sfg.orchard.mobile.app.OrchardViewModel
import dev.sfg.orchard.mobile.auth.AuthState
import dev.sfg.orchard.mobile.ui.screens.NativeLoginScreen
import dev.sfg.orchard.mobile.ui.tv.pairing.TvQrLoginScreen

/**
 * YouTube sign-in on TV: [TvRoutes.LOGIN] pairs with a phone (HU-ORCH-13) and [TvRoutes.LOGIN_WEB]
 * keeps the WebView login for keyboard or mouse users.
 */
internal fun NavGraphBuilder.tvLoginRoutes(
    nav: NavHostController,
    viewModel: OrchardViewModel,
    collapseRail: () -> Unit,
) {
    composable(TvRoutes.LOGIN) {
        TvSafeContent(collapseRail) {
            TvQrLoginScreen(
                onSession = { payload ->
                    val youtube = payload.youtube
                    viewModel.completeSignIn(
                        youtube.cookie, youtube.visitorData, youtube.dataSyncId, youtube.accountIndex,
                        pageAvatarUrl = youtube.avatarUrl,
                    )
                    payload.spotify?.let { spotify ->
                        viewModel.updateSettings(viewModel.settings.value.copy(spotifySpdc = spotify.spDc))
                    }
                    nav.popBackStack(TvRoutes.LOGIN, inclusive = true)
                },
                onUseWebLogin = { nav.navigate(TvRoutes.LOGIN_WEB) { launchSingleTop = true } },
            )
        }
    }
    composable(TvRoutes.LOGIN_WEB) {
        val auth by viewModel.auth.collectAsStateWithLifecycle()
        TvSafeContent(collapseRail) {
            NativeLoginScreen(
                auth = auth,
                onBegin = viewModel::beginSignIn,
                onSession = { cookie, visitorData, dataSyncId, accountIndex, avatarUrl ->
                    viewModel.completeSignIn(cookie, visitorData, dataSyncId, accountIndex, pageAvatarUrl = avatarUrl)
                },
                onCancel = viewModel::cancelSignIn,
                // Signed in: leave both login entries. Cancelled: back to the QR screen.
                onComplete = {
                    val target = if (viewModel.auth.value is AuthState.SignedIn) TvRoutes.LOGIN else TvRoutes.LOGIN_WEB
                    nav.popBackStack(target, inclusive = true)
                },
            )
        }
    }
}
