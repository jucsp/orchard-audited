// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.LoadState
import dev.sfg.orchard.mobile.model.LyricLine
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val LyricRowShape = RoundedCornerShape(10.dp)

/** After the user moves through the lines, auto-scroll waits this long before following again. */
private const val ManualScrollHoldMs = 5_000L

/**
 * Synced lyrics (mockup 1A). Past lines at 50 %, upcoming at 78 % (legible from the couch), the
 * active line larger and white. OK on a line seeks to it. Fades at the top and bottom edges.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLyricsPanel(
    lyrics: LoadState<List<LyricLine>>,
    onSeek: (Long) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = (lyrics as? LoadState.Content)?.value.orEmpty()
    val clock = LocalPlayerClock.current
    val activeIndex by remember(lines) {
        derivedStateOf {
            val position = clock.reported().positionMs
            lines.indexOfLast { it.startMs != null && it.startMs <= position }
        }
    }
    var manualUntil by remember { mutableLongStateOf(0L) }

    Box(modifier.fillMaxSize()) {
        if (lines.isEmpty()) {
            Text(
                if (lyrics is LoadState.Loading) "Cargando letra…" else "Esta canción no tiene letra sincronizada",
                style = TvTokens.tvBody.copy(color = Color.White.copy(alpha = 0.8f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            )
            return@Box
        }

        val listState = rememberLazyListState()
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0 && SystemClock.uptimeMillis() > manualUntil) {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 40.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val active = index == activeIndex
                val alpha = when {
                    active -> 1f
                    index < activeIndex -> 0.5f
                    else -> 0.78f
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .tvClickable(
                            TvFocusStyle.Row,
                            LyricRowShape,
                            onFocus = {
                                onContentFocus()
                                manualUntil = SystemClock.uptimeMillis() + ManualScrollHoldMs
                            },
                        ) { line.startMs?.let(onSeek) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        line.text,
                        style = if (active) TvTokens.tvLyricActive else TvTokens.tvLyric,
                        color = Color.White.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}
