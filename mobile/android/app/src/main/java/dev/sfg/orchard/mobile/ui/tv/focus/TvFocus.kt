// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.focus

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/** The six canonical focus treatments; no ad-hoc focus styles are allowed (TV-INV-3). */
enum class TvFocusStyle { Card, Button, Row, Key, NavItem, Transport }

private fun TvFocusStyle.focusedScale(): Float = when (this) {
    TvFocusStyle.Card, TvFocusStyle.Button, TvFocusStyle.NavItem -> 1.05f
    TvFocusStyle.Row -> 1.03f
    TvFocusStyle.Key -> 1.08f
    TvFocusStyle.Transport -> 1.05f
}

private fun TvFocusStyle.glowElevation() = when (this) {
    TvFocusStyle.Card -> 30.dp
    TvFocusStyle.Transport -> 34.dp
    TvFocusStyle.Row -> 18.dp
    TvFocusStyle.Button, TvFocusStyle.Key, TvFocusStyle.NavItem -> 22.dp
}

/**
 * The single focus treatment (mockup 4): scale 1.05 (rows 1.03, keys 1.08), a 2 dp white border,
 * an accent glow and elevation. Pressing OK shrinks to 0.98 for tactile feedback.
 *
 * It must come before `clickable`/`focusable` and before any `clip` or `background`, so those scale
 * with the node instead of clipping the glow. Scaling lives in [graphicsLayer]; layout never moves.
 * [onFocusChange] lets components that invert their fill (buttons, rail items) react to focus.
 */
@Composable
fun Modifier.tvFocus(
    style: TvFocusStyle,
    shape: Shape,
    onFocus: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    pressed: Boolean = false,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    val target = when {
        focused && pressed -> TvTokens.FocusPressedScale
        focused -> style.focusedScale()
        else -> 1f
    }
    val scale by animateFloatAsState(target, spring(dampingRatio = 0.75f, stiffness = 350f), label = "TvFocusScale")
    val border by animateDpAsState(if (focused) TvTokens.FocusBorder else 0.dp, tween(150), label = "TvFocusBorder")
    val glowTarget = when {
        !focused -> 0.dp
        pressed -> style.glowElevation() / 3
        else -> style.glowElevation()
    }
    val glow by animateDpAsState(glowTarget, tween(if (focused) 150 else 120), label = "TvFocusGlow")

    return this
        .onFocusChanged { state ->
            if (focused != state.isFocused) onFocusChange?.invoke(state.isFocused)
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
                    ambientColor = accent.copy(alpha = TvTokens.FocusGlowAlpha),
                    spotColor = accent.copy(alpha = TvTokens.FocusGlowAlpha),
                )
            } else {
                Modifier
            },
        )
        .border(border, if (focused) Color.White else Color.Transparent, shape)
}

/** Focus treatment plus one clickable focus target. No ripple: the focus treatment is the feedback. */
@Composable
fun Modifier.tvClickable(
    style: TvFocusStyle,
    shape: Shape,
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .tvFocus(style, shape, onFocus = onFocus, onFocusChange = onFocusChange, pressed = pressed)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Focus treatment plus one clickable focus target with a long-press action (D-pad menu). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvLongClickable(
    style: TvFocusStyle,
    shape: Shape,
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .tvFocus(style, shape, onFocus = onFocus, onFocusChange = onFocusChange, pressed = pressed)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/** Focus treatment plus a plain focus target, for nodes that handle their own keys. */
@Composable
fun Modifier.tvFocusable(
    style: TvFocusStyle,
    shape: Shape,
    onFocus: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
): Modifier = this
    .tvFocus(style, shape, onFocus = onFocus, onFocusChange = onFocusChange)
    .focusable()

/** Focus group that reports when D-pad focus enters any descendant (embedded phone screens). */
fun Modifier.tvFocusWithin(onFocus: () -> Unit): Modifier = this
    .onFocusChanged { if (it.hasFocus) onFocus() }
    .focusGroup()
