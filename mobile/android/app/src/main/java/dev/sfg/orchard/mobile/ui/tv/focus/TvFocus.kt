// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.focus

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sfg.orchard.mobile.ui.theme.LocalAccent

/** The six canonical focus treatments; no ad-hoc focus styles are allowed (TV-INV-3). */
enum class TvFocusStyle { Card, Button, Row, Key, NavItem, Transport }

/**
 * The single focus treatment for the TV tree. It observes the focus target that follows it, so it
 * must come before `clickable`/`focusable` and before any `clip` or `background`: those then scale
 * with the node instead of clipping the glow and border away. Scaling lives in [graphicsLayer] so
 * layout never moves, and the focused node raises its [zIndex] above its neighbours.
 */
@Composable
fun Modifier.tvFocus(
    style: TvFocusStyle,
    shape: Shape,
    onFocus: (() -> Unit)? = null,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    val scaleTarget = when (style) {
        TvFocusStyle.Card -> 1.05f
        TvFocusStyle.Button -> 1.05f
        TvFocusStyle.Row -> 1.02f
        TvFocusStyle.Key -> 1.08f
        TvFocusStyle.NavItem -> 1.05f
        TvFocusStyle.Transport -> 1.06f
    }
    val borderWidth = when (style) {
        TvFocusStyle.Card -> 2.dp
        TvFocusStyle.Row -> 1.5.dp
        TvFocusStyle.Transport -> 2.5.dp
        TvFocusStyle.Button, TvFocusStyle.Key, TvFocusStyle.NavItem -> 0.dp
    }
    val glowElevation = when (style) {
        TvFocusStyle.Card -> 28.dp
        TvFocusStyle.Button -> 20.dp
        TvFocusStyle.Row -> 12.dp
        TvFocusStyle.Key -> 16.dp
        TvFocusStyle.NavItem -> 20.dp
        TvFocusStyle.Transport -> 24.dp
    }

    val scale by animateFloatAsState(
        targetValue = if (focused) scaleTarget else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "TvFocusScale",
    )
    val border by animateDpAsState(if (focused) borderWidth else 0.dp, tween(180), label = "TvFocusBorder")
    val glow by animateDpAsState(if (focused) glowElevation else 0.dp, tween(180), label = "TvFocusGlow")

    return this
        .onFocusChanged { state ->
            focused = state.isFocused
            if (state.isFocused) onFocus?.invoke()
        }
        .zIndex(if (focused) 2f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (glow > 0.dp) {
                Modifier.shadow(
                    glow,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.40f),
                    spotColor = accent.copy(alpha = 0.40f),
                )
            } else {
                Modifier
            },
        )
        .border(border, if (focused) accent.copy(alpha = 0.9f) else Color.Transparent, shape)
}

/** Focus treatment plus one clickable focus target. No ripple: the focus treatment is the feedback. */
@Composable
fun Modifier.tvClickable(
    style: TvFocusStyle,
    shape: Shape,
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .tvFocus(style, shape, onFocus = onFocus)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Focus treatment plus a plain focus target, for nodes that handle their own keys. */
@Composable
fun Modifier.tvFocusable(
    style: TvFocusStyle,
    shape: Shape,
    onFocus: (() -> Unit)? = null,
): Modifier = this
    .tvFocus(style, shape, onFocus = onFocus)
    .focusable()
