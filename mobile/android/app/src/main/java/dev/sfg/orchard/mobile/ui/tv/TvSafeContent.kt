// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusWithin
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/**
 * Safe-area shell for phone screens embedded in the TV tree. It applies the TV overscan insets with
 * the rail's start offset (TV-INV-2) and collapses the rail on entry and whenever D-pad focus moves
 * into the embedded screen, since those screens never call back into the TV focus treatment.
 */
@Composable
fun TvSafeContent(
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(Unit) { onContentFocus() }
    Box(
        modifier
            .fillMaxSize()
            .padding(
                start = TvTokens.ContentStartX,
                end = TvTokens.OverscanHorizontal,
                top = TvTokens.OverscanVertical,
                bottom = TvTokens.OverscanVertical,
            )
            .tvFocusWithin(onContentFocus),
        content = content,
    )
}
