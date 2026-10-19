// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.PlaybackSnapshot
import dev.sfg.orchard.mobile.model.RepeatMode
import dev.sfg.orchard.mobile.ui.components.LocalPlayerClock
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.focus.tvFocusable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

/** Callbacks shared by both player layouts, so the composables stay readable. */
class TvTransportActions(
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onToggleLike: () -> Unit,
    val onNightMode: () -> Unit,
    val onMore: () -> Unit,
    val onContentFocus: () -> Unit,
)

enum class TvTransportLayout {
    /** 1A/1B: like at the start, transport centred, night mode and "more" at the end. */
    Wide,

    /** 1C: play first, then the rest in one row (the info column is narrower). */
    Compact,
}

@Composable
fun TvTransportRow(
    playback: PlaybackSnapshot,
    liked: Boolean,
    actions: TvTransportActions,
    layout: TvTransportLayout,
    playFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val shuffleBtn = @Composable {
        TvRoundButton(Icons.Rounded.Shuffle, if (playback.shuffle) "Desactivar aleatorio" else "Aleatorio", 36.dp, actions.onShuffle, actions.onContentFocus, tint = if (playback.shuffle) accent else Color.White, glass = false)
    }
    val repeatBtn = @Composable {
        TvRoundButton(
            if (playback.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            if (playback.repeatMode == RepeatMode.OFF) "Repetir" else "Cambiar repetición",
            36.dp, actions.onRepeat, actions.onContentFocus,
            tint = if (playback.repeatMode != RepeatMode.OFF) accent else Color.White, glass = false,
        )
    }
    val prevBtn = @Composable { TvRoundButton(Icons.Rounded.SkipPrevious, "Anterior", if (layout == TvTransportLayout.Wide) 46.dp else 44.dp, actions.onPrevious, actions.onContentFocus) }
    val nextBtn = @Composable { TvRoundButton(Icons.Rounded.SkipNext, "Siguiente", if (layout == TvTransportLayout.Wide) 46.dp else 44.dp, actions.onNext, actions.onContentFocus) }
    val playBtn = @Composable { PlayButton(playback.isPlaying, if (layout == TvTransportLayout.Wide) 64.dp else 60.dp, actions, playFocus) }
    val likeBtn = @Composable {
        TvRoundButton(if (liked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Quitar de me gusta" else "Me gusta", if (layout == TvTransportLayout.Wide) 38.dp else 32.dp, actions.onToggleLike, actions.onContentFocus, tint = if (liked) CanopyColors.Favorite else Color.White)
    }
    val nightBtn = @Composable { TvRoundButton(Icons.Rounded.Bedtime, "Modo nocturno", if (layout == TvTransportLayout.Wide) 38.dp else 32.dp, actions.onNightMode, actions.onContentFocus) }
    val moreBtn = @Composable { TvRoundButton(Icons.Rounded.MoreHoriz, "Más opciones", if (layout == TvTransportLayout.Wide) 38.dp else 32.dp, actions.onMore, actions.onContentFocus) }

    when (layout) {
        TvTransportLayout.Wide -> Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            likeBtn()
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                shuffleBtn(); prevBtn(); playBtn(); nextBtn(); repeatBtn()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { nightBtn(); moreBtn() }
        }

        TvTransportLayout.Compact -> Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            playBtn(); prevBtn(); nextBtn(); shuffleBtn(); repeatBtn()
            Spacer(Modifier.weight(1f))
            nightBtn(); likeBtn(); moreBtn()
        }
    }
}

/** The transport hero: a white circle; focus adds the white ring, glow and 1.05 scale. */
@Composable
private fun PlayButton(isPlaying: Boolean, size: Dp, actions: TvTransportActions, playFocus: FocusRequester) {
    Box(
        Modifier
            .size(size)
            .focusRequester(playFocus)
            .tvClickable(TvFocusStyle.Transport, CircleShape, onFocus = actions.onContentFocus, onClick = actions.onToggle)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pausa" else "Reproducir",
            tint = Color(0xFF12101A),
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** Round icon button that inverts to white on focus (mockup 4, "Botón"). */
@Composable
fun TvRoundButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    glass: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .tvClickable(TvFocusStyle.Button, CircleShape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(CircleShape)
            .background(
                when {
                    focused -> CanopyColors.Text
                    glass -> Color.White.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (focused) CanopyColors.Chrome else tint, modifier = Modifier.size(size * 0.44f))
    }
}

/** Seek bar: focusable; ◀ ▶ seek 10 s. Only this node and the lyrics read the player clock. */
@Composable
fun TvProgressBar(
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = LocalPlayerClock.current
    val head by remember(clock) { derivedStateOf { clock.reported() } }
    val durationMs = playback.durationMs
    val fraction = (head.positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val barShape = RoundedCornerShape(3.dp)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onSeek((head.positionMs - 10_000).coerceAtLeast(0)); true }
                        Key.DirectionRight -> { onSeek((head.positionMs + 10_000).coerceAtMost(durationMs)); true }
                        else -> false
                    }
                }
                .tvFocusable(TvFocusStyle.Row, RoundedCornerShape(8.dp), onFocus = onContentFocus)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(barShape).background(Color.White.copy(alpha = 0.28f)))
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(barShape).background(Color.White))
            KnobAt(fraction)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(head.positionMs), style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.86f)))
            Text("-" + formatTime(durationMs - head.positionMs), style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.86f)))
        }
    }
}

/** Positions the 11 dp knob at [fraction] of the available width. */
@Composable
private fun KnobAt(fraction: Float) {
    Layout(
        content = { Box(Modifier.size(11.dp).clip(CircleShape).background(Color.White)) },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val knob = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val x = ((constraints.maxWidth * fraction) - knob.width / 2f).toInt().coerceIn(0, constraints.maxWidth - knob.width)
        layout(constraints.maxWidth, knob.height) { knob.place(x, 0) }
    }
}

internal fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1_000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Small "keycap" used in on-screen hints ("OK", "◀"). */
@Composable
fun TvKeyHint(key: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            key,
            style = TvTokens.tvMeta.copy(color = Color.White),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
                .offset(y = 0.dp),
        )
        Text(label, style = TvTokens.tvMeta.copy(color = Color.White.copy(alpha = 0.82f)))
    }
}
