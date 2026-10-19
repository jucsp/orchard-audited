// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.rail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
import dev.sfg.orchard.mobile.ui.components.OrchardMark
import dev.sfg.orchard.mobile.ui.motion.PlayingBars
import dev.sfg.orchard.mobile.ui.theme.CanopyColors
import dev.sfg.orchard.mobile.ui.theme.LocalAccent
import dev.sfg.orchard.mobile.ui.tv.TvRoutes
import dev.sfg.orchard.mobile.ui.tv.focus.TvFocusStyle
import dev.sfg.orchard.mobile.ui.tv.focus.tvClickable
import dev.sfg.orchard.mobile.ui.tv.theme.TvTokens

private data class RailDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    RailDestination(TvRoutes.HOME, "Inicio", Icons.Rounded.Home),
    RailDestination(TvRoutes.SEARCH, "Buscar", Icons.Rounded.Search),
    RailDestination(TvRoutes.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
    RailDestination(TvRoutes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)

/**
 * Floating glass capsule (mockups 2A/2B). Collapsed it is 44 dp wide and vertically centred; when
 * an item takes focus it grows to a 180 dp panel. The caller pushes the content right by
 * [TvTokens.RailPush] while it is expanded, so the rail never covers titles or the mini player.
 * The caller places it at x = [TvTokens.RailStart] so its icons stay inside the safe area.
 */
@Composable
fun TvNavigationRail(
    currentRoute: String?,
    expanded: Boolean,
    nowPlayingTrack: Track?,
    onSelect: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onRailFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) TvTokens.RailExpandedWidth else TvTokens.RailCollapsedWidth,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "TvRailWidth",
    )
    val shape = RoundedCornerShape(if (expanded) 18.dp else 22.dp)

    Column(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxHeight().padding(vertical = TvTokens.OverscanVertical) else Modifier)
            .width(width)
            .clip(shape)
            .background(if (expanded) Color(0xFF0E0C16).copy(alpha = 0.78f) else TvTokens.GlassSurface)
            .border(1.dp, TvTokens.GlassBorder, shape)
            .padding(vertical = if (expanded) 16.dp else 7.dp, horizontal = if (expanded) 11.dp else 6.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (expanded) {
            Row(Modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                OrchardMark(Modifier.size(22.dp), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Orchard", color = CanopyColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
        }

        destinations.forEach { destination ->
            RailItem(
                destination = destination,
                selected = currentRoute == destination.route,
                expanded = expanded,
                onSelect = { onSelect(destination.route) },
                onFocus = onRailFocus,
            )
        }

        if (expanded) Spacer(Modifier.weight(1f))

        if (nowPlayingTrack != null) {
            if (!expanded) {
                Box(Modifier.padding(vertical = 2.dp).width(20.dp).height(1.dp).background(Color.White.copy(alpha = 0.14f)))
            }
            NowPlayingTile(nowPlayingTrack, expanded, onOpenNowPlaying, onRailFocus)
        }
    }
}

@Composable
private fun RailItem(
    destination: RailDestination,
    selected: Boolean,
    expanded: Boolean,
    onSelect: () -> Unit,
    onFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = if (expanded) RoundedCornerShape(50) else CircleShape
    // Focused inverts to a white pill; the current route keeps an accent tint (mockup 4).
    val background = when {
        focused -> CanopyColors.Text
        selected -> accent.copy(alpha = 0.38f)
        else -> Color.Transparent
    }
    val content = when {
        focused -> CanopyColors.Chrome
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }
    Row(
        Modifier
            .then(if (expanded) Modifier.fillMaxWidth().height(38.dp) else Modifier.size(32.dp))
            .tvClickable(
                TvFocusStyle.NavItem,
                shape,
                onFocus = onFocus,
                onFocusChange = { focused = it },
                onClick = onSelect,
            )
            .clip(shape)
            .background(background)
            .padding(horizontal = if (expanded) 13.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(destination.icon, contentDescription = destination.label, tint = content, modifier = Modifier.size(16.dp))
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(
                destination.label,
                color = content,
                fontSize = 13.sp,
                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NowPlayingTile(track: Track, expanded: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(if (expanded) 12.dp else 8.dp)
    if (!expanded) {
        Box(
            Modifier
                .size(32.dp)
                .tvClickable(TvFocusStyle.Card, shape, onFocus = onFocus, onClick = onClick)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkTile(track.artworkUrl, "Ahora suena: ${track.title}", Modifier.fillMaxSize(), 8)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            PlayingBars(accent, Modifier.size(14.dp))
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(TvFocusStyle.Card, shape, onFocus = onFocus, onClick = onClick)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(36.dp), 7)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("AHORA SUENA", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(track.title, style = TvTokens.tvCardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = TvTokens.tvMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
