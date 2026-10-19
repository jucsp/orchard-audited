// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.connect.R
import dev.sfg.orchard.mobile.ui.theme.CanopyColors

/**
 * Single source of truth for the Android TV "Glass Rail" metrics and typography (Spec §4).
 *
 * Margins live here and only here (TV-INV-2). Screens never hardcode overscan values.
 */
object TvTokens {
    /** Safe-area insets: 48 dp sides, 27 dp top/bottom (TV-INV-2). */
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    /** The collapsed rail is icon-only; expanded it overlays the content without resizing it. */
    val RailCollapsedWidth = 68.dp
    val RailExpandedWidth = 210.dp

    /** Content always starts at x = 92 dp, whether or not the rail is expanded. */
    val ContentStartX = 92.dp

    val ShelfColumns = 6
    val ShelfGap = 16.dp

    val CardRadius = 7.dp
    val HeroArtworkRadius = 12.dp
    val ListRowRadius = 8.dp
    val KeyboardKeyRadius = 7.dp

    /** Inter is already bundled in res/font; ExtraBold is not, so Bold stands in for 800. */
    val TvFontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    val tvDisplay = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
        color = CanopyColors.Text,
    )

    val tvHeadline = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        color = CanopyColors.Text,
    )

    val tvTitleNowPlaying = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        color = CanopyColors.Text,
    )

    val tvShelf = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        color = CanopyColors.Text,
    )

    val tvLyricActive = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = CanopyColors.Text,
    )

    val tvLyric = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = CanopyColors.Text,
    )

    val tvBody = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = CanopyColors.Text,
    )

    val tvMeta = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = CanopyColors.Muted,
    )

    val tvEyebrow = TextStyle(
        fontFamily = TvFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp,
        color = CanopyColors.Eyebrow,
    )
}
