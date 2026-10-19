// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import android.app.ActivityManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.sfg.orchard.mobile.ui.components.ArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.KawarpArtworkBackdrop
import dev.sfg.orchard.mobile.ui.components.rememberArtworkPalette
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.prefs.TvMenuBackground
import dev.sfg.orchard.mobile.ui.tv.prefs.TvUiState
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/** Which screen family the backdrop sits behind. */
enum class TvBackdropRole { Player, Menu, Night }

private enum class BackdropMode { Artwork, Glow, Dark, Night }

/**
 * App-wide backdrop (mockups 1–4):
 * - Player: the cover's colours, bright and moving; scrim set by "Intensidad del fondo".
 * - Menu: the same cover a little darker while something plays, accent glows when idle, or plain
 *   dark if the user chose it in "Fondo de los menús".
 * - Night: absolute black, nothing animates (mockup 1D).
 * Kawarp runs at half resolution and 30 fps and never on low-RAM devices (TV-INV-5).
 */
@Composable
fun TvAmbientBackdrop(
    artworkUrl: String,
    isPlaying: Boolean,
    animatedSetting: Boolean,
    role: TvBackdropRole,
    ui: TvUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
    val hasArtwork = artworkUrl.isNotBlank()
    val mode = when (role) {
        TvBackdropRole.Night -> BackdropMode.Night
        TvBackdropRole.Player -> if (hasArtwork) BackdropMode.Artwork else BackdropMode.Glow
        TvBackdropRole.Menu -> when (ui.menuBackground) {
            TvMenuBackground.Dark -> BackdropMode.Dark
            TvMenuBackground.Glow -> BackdropMode.Glow
            // Auto: the cover only while music is actually playing; glows when idle or paused.
            TvMenuBackground.Auto -> if (hasArtwork && isPlaying) BackdropMode.Artwork else BackdropMode.Glow
            TvMenuBackground.Artwork -> if (hasArtwork) BackdropMode.Artwork else BackdropMode.Glow
        }
    }
    val scrim = if (role == TvBackdropRole.Player) ui.intensity.playerScrim else TvTokens.MenuArtworkScrim

    Crossfade(targetState = mode, animationSpec = tween(400), modifier = modifier, label = "TvBackdropMode") { m ->
        when (m) {
            BackdropMode.Night -> Box(Modifier.fillMaxSize().background(Color.Black))
            BackdropMode.Dark -> Box(Modifier.fillMaxSize().background(CanopyColors.Chrome))
            BackdropMode.Glow -> AccentGlow()
            BackdropMode.Artwork -> ArtworkLayer(
                artworkUrl = artworkUrl,
                animate = isPlaying && animatedSetting,
                lowRam = lowRam,
                scrim = scrim,
                forPlayer = role == TvBackdropRole.Player,
            )
        }
    }
}

@Composable
private fun ArtworkLayer(
    artworkUrl: String,
    animate: Boolean,
    lowRam: Boolean,
    scrim: Float,
    forPlayer: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        val palette = rememberArtworkPalette(artworkUrl)
        ArtworkBackdrop(palette = palette, animated = animate && !lowRam)
        if (!lowRam) {
            KawarpArtworkBackdrop(
                artworkUrl = artworkUrl,
                isPlaying = animate,
                renderScale = 0.5f,
                fullFrameRate = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(TvTokens.PlayerScrimColor.copy(alpha = scrim)))
        if (forPlayer) {
            // Only a light vignette where text and controls sit; the rest stays vivid.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to TvTokens.PlayerScrimColor.copy(alpha = 0.32f),
                    ),
                ),
            )
        }
    }
}

/** Idle backdrop: dark base with soft glows in the active accent colour. */
@Composable
private fun AccentGlow() {
    val accent = LocalAccent.current
    Box(
        Modifier
            .fillMaxSize()
            .background(CanopyColors.Chrome)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.5f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.55f, size.height * 0.5f),
                        radius = size.width * 0.38f,
                    ),
                )
            },
    )
}
