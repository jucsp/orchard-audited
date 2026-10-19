// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.prefs.TvPlayerPanel
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private val TabShape = RoundedCornerShape(50)

/** Panel tabs (1A/1B): Lyrics, Queue and "Ocultar ›", which switches to the 1C layout. */
@Composable
fun TvPanelTabs(
    active: TvPlayerPanel,
    queueCount: Int,
    onSelect: (TvPlayerPanel) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabPill("Letras", Icons.Rounded.Mic, active == TvPlayerPanel.Lyrics, onContentFocus) { onSelect(TvPlayerPanel.Lyrics) }
        TabPill("Cola · $queueCount", Icons.AutoMirrored.Rounded.QueueMusic, active == TvPlayerPanel.Queue, onContentFocus) { onSelect(TvPlayerPanel.Queue) }
        Spacer(Modifier.weight(1f))
        TabPill("Ocultar", Icons.AutoMirrored.Rounded.KeyboardArrowRight, false, onContentFocus, iconAfter = true) { onSelect(TvPlayerPanel.Hidden) }
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onContentFocus: () -> Unit,
    iconAfter: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val filled = selected || focused
    val content = if (filled) Color(0xFF12101A) else Color.White
    Row(
        Modifier
            .tvClickable(TvFocusStyle.Button, TabShape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick)
            .clip(TabShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!iconAfter) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Text(label, color = content, fontSize = 11.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold)
        if (iconAfter) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
    }
}

/**
 * Icon-only edge button for the 1C layout. The label appears next to it only while it has focus,
 * so the icons stay discoverable without text on screen. [badge] shows the queue length.
 */
@Composable
fun TvEdgeIconButton(
    icon: ImageVector,
    label: String,
    badge: Int?,
    onContentFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (focused) {
            Text(
                label,
                style = TvTokens.tvBody.copy(fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0A0812).copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .tvClickable(TvFocusStyle.Button, CircleShape, onFocus = onContentFocus, onFocusChange = { focused = it }, onClick = onClick)
                    .clip(CircleShape)
                    .background(if (focused) CanopyColors.Text else Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = if (focused) CanopyColors.Chrome else Color.White, modifier = Modifier.size(18.dp))
            }
            if (badge != null && badge > 0) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = Color(0xFF12101A),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-3).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}
