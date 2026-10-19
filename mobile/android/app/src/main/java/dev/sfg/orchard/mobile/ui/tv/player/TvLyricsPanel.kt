// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.LyricLine
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val LyricRowShape = RoundedCornerShape(10.dp)

/**
 * Synced lyrics panel. Lines are focusable: OK seeks to the line's [LyricLine.startMs]. The active
 * line follows [LocalPlayerClock] and auto-scrolls to ~40% of the viewport; manual navigation is
 * handled by the caller re-invoking this composable with a stable keyed list.
 */
@Composable
fun TvLyricsPanel(
    lyrics: LoadState<List<LyricLine>>,
    onSeek: (Long) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val lines = (lyrics as? LoadState.Content)?.value.orEmpty()
    val clock = LocalPlayerClock.current
    val activeIndex by remember(lines) {
        derivedStateOf {
            val position = clock.reported().positionMs
            lines.indexOfLast { it.startMs != null && it.startMs <= position }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0F1011).copy(alpha = 0.55f),
                    0.14f to Color.Transparent,
                    0.86f to Color.Transparent,
                    1f to Color(0xFF0F1011).copy(alpha = 0.55f),
                )
            ),
    ) {
        if (lines.isEmpty()) {
            Text(
                "No hay letra sincronizada",
                style = TvTokens.tvMeta,
                color = CanopyColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            )
            return@Box
        }

        val listState = rememberLazyListState()
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val active = index == activeIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .tvClickable(TvFocusStyle.Row, LyricRowShape, onFocus = onContentFocus) {
                            line.startMs?.let(onSeek)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        line.text,
                        style = if (active) TvTokens.tvLyricActive else TvTokens.tvLyric.copy(color = Color.White.copy(alpha = 0.55f)),
                        color = if (active) CanopyColors.Text else Color.White.copy(alpha = 0.55f),
                    )
                }
                if (active) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .background(accent.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                            .padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
