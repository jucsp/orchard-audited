// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val TabShape = RoundedCornerShape(50)

/** Border tabs: Lyrics, Queue and Hide. Selecting one changes the now-playing panel state. */
@Composable
fun TvPanelTabs(
    active: PanelState,
    queueCount: Int,
    onSelect: (PanelState) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvTab("‹ Letras", active == PanelState.Lyrics, accent, onContentFocus) { onSelect(PanelState.Lyrics) }
        TvTab("Cola · $queueCount", active == PanelState.Queue, accent, onContentFocus) { onSelect(PanelState.Queue) }
        TvTab("Ocultar ›", active == PanelState.Hidden, accent, onContentFocus) { onSelect(PanelState.Hidden) }
    }
}

@Composable
private fun TvTab(
    label: String,
    selected: Boolean,
    accent: Color,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .tvClickable(TvFocusStyle.Button, TabShape, onFocus = onContentFocus, onClick = onClick)
            .background(
                if (selected) accent.copy(alpha = 0.28f) else Color.Transparent,
                TabShape,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TvTokens.tvBody.copy(color = if (selected) CanopyColors.Text else CanopyColors.MutedStrong),
        )
    }
}
