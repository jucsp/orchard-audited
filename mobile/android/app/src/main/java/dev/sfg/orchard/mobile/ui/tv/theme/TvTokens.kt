// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.connect.R
import dev.sfg.orchard.mobile.ui.theme.CanopyColors

/**
 * Single source of truth for the TV metrics and typography (redesign 2026-10-19).
 * Mockup pixels are 1920x1080, so every value here is the mockup value divided by two.
 */
object TvTokens {
    /** Safe area: 48 dp sides, 27 dp top/bottom (TV-INV-2). Nothing focusable goes outside it. */
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    /** Floating rail capsule. It starts at the safe edge, so its icons never sit in the overscan. */
    val RailStart = 48.dp
    val RailCollapsedWidth = 44.dp
    val RailExpandedWidth = 180.dp

    /** Content starts after the capsule plus a 24 dp gap. */
    val ContentStartX = 116.dp

    /** The expanded rail pushes the content right by this much instead of covering it. */
    val RailPush = RailExpandedWidth - RailCollapsedWidth

    val ShelfColumns = 5
    val ShelfGap = 16.dp

    val CardRadius = 8.dp
    val HeroArtworkRadius = 14.dp
    val HeroCardRadius = 16.dp
    val ListRowRadius = 10.dp
    val KeyboardKeyRadius = 7.dp
    val PanelRadius = 18.dp
    val SettingsCardRadius = 13.dp

    /** Focus system (mockup 4). */
    val FocusBorder = 2.dp
    const val FocusGlowAlpha = 0.35f
    const val FocusPressedScale = 0.98f

    /** Backdrop scrims. The player stays bright; menus are a little darker. */
    const val MenuArtworkScrim = 0.52f
    val PlayerScrimColor = Color(0xFF06060C)

    /** Translucent surfaces that let the backdrop show through. */
    val GlassSurface = Color(0xFF0C0C14).copy(alpha = 0.45f)
    val GlassSurfaceFocused = Color(0xFF2E2E42).copy(alpha = 0.82f)
    val GlassPanel = Color(0xFF0C0A14).copy(alpha = 0.32f)
    val GlassBorder = Color.White.copy(alpha = 0.16f)

    /** Inter is bundled in res/font; ExtraBold is not, so Bold stands in for 800. */
    val TvFontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    val tvDisplay = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 39.sp,
        letterSpacing = (-1).sp,
        color = CanopyColors.Text,
    )

    val tvHeadline = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.7).sp,
        color = CanopyColors.Text,
    )

    val tvTitleNowPlaying = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 37.sp,
        letterSpacing = (-1).sp,
        color = CanopyColors.Text,
    )

    val tvShelf = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = CanopyColors.Text,
    )

    val tvLyricActive = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        color = CanopyColors.Text,
    )

    val tvLyric = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = CanopyColors.Text,
    )

    val tvBody = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = CanopyColors.Text,
    )

    val tvCardTitle = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = CanopyColors.Text,
    )

    val tvMeta = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = Color(0xFFB7C2CD),
    )

    val tvEyebrow = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
        color = CanopyColors.Eyebrow,
    )
}
