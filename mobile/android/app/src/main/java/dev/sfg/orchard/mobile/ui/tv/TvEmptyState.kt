// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val ActionShape = RoundedCornerShape(50)

/**
 * Friendly empty/signed-out state for the TV tree. The optional action is the first focus target,
 * so a D-pad user is never left on a screen with no reachable way forward (TV-INV-1/TV-INV-3).
 */
@Composable
fun TvEmptyState(
    title: String,
    message: String,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val focus = remember { FocusRequester() }
    val hasAction = actionLabel != null && onAction != null

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 72.dp)
            .then(
                if (hasAction) Modifier else Modifier
                    .focusRequester(focus)
                    .tvFocusable(TvFocusStyle.Row, RoundedCornerShape(TvTokens.ListRowRadius), onFocus = onContentFocus)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TvTokens.tvHeadline, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = TvTokens.tvBody.copy(color = CanopyColors.MutedStrong),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        if (hasAction) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .focusRequester(focus)
                    .tvClickable(TvFocusStyle.Button, ActionShape, onFocus = onContentFocus, onClick = onAction!!)
                    .clip(ActionShape)
                    .background(accent)
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionLabel!!,
                    color = Color.Black,
                    style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
