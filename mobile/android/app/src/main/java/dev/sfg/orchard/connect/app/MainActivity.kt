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

package dev.sfg.orchard.connect.app

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.orchard.mobile.app.OrchardApp
import dev.sfg.orchard.mobile.app.OrchardViewModel
import dev.sfg.orchard.mobile.auth.OrchardAccountService
import dev.sfg.orchard.mobile.pairing.PairingLinkInbox
import dev.sfg.orchard.mobile.pairing.PairingUri
import dev.sfg.orchard.mobile.ui.theme.OrchardTheme
import dev.sfg.orchard.mobile.ui.tv.isTvDevice

/** Thin Android entry point; app behavior lives in repositories and state holders. */
class MainActivity : FragmentActivity() {
    private val viewModel by viewModels<OrchardViewModel>()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Media3 remains usable in-app if the user declines notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.sfg.orchard.mobile.discord.DiscordNative.attach(this)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermission()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            OrchardTheme(useSystemColors = settings.useSystemColors) {
                OrchardApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            // Assistant sends the spoken words as the query, and narrows them into the focus
            // extras when it recognised a specific artist or album. An empty query is the
            // documented "just play something" request.
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().ifBlank {
                listOfNotNull(
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE),
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST),
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM),
                ).joinToString(" ")
            }
            viewModel.playFromSearch(query)
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (isPairingLink(sharedText.trim())) {
                if (!isTvDevice(this)) PairingLinkInbox.offer(sharedText.trim())
                return
            }
            if (sharedText.isNotBlank()) {
                viewModel.handleIncomingLink(sharedText)
            }
            return
        }

        val uri = intent.data
        val dataString = uri?.toString().orEmpty()
        if (dataString.isBlank()) return
        if (uri?.scheme == packageName && uri.host == "account" && uri.path == "/callback") {
            OrchardAccountService.get(this).handleAuthCallback(uri)
            return
        }
        // The pairing link carries a one-time token: never hand it to the shared-link resolver.
        // A TV does not send sessions, so it drops the link (SDD-tv-qr-pairing §6.3).
        if (isPairingLink(dataString)) {
            if (!isTvDevice(this)) PairingLinkInbox.offer(dataString)
            return
        }
        viewModel.handleIncomingLink(dataString)
    }

    private fun isPairingLink(value: String): Boolean =
        value.startsWith("${PairingUri.SCHEME}://${PairingUri.HOST}", ignoreCase = true)

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
