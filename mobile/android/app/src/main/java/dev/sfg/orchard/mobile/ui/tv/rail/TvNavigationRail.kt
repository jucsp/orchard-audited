// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.tv.rail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.mobile.model.Track
import dev.sfg.orchard.mobile.ui.components.ArtworkTile
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
    RailDestination(TvRoutes.SETTINGS, "Ajustes", Icons.Rounded.Person),
)

private val ItemShape = RoundedCornerShape(10.dp)

/**
 * Collapsed icon rail (68 dp) that expands to 210 dp when an item receives focus. The expanded pane
 * overlays the content instead of resizing it (TV-INV-2); the content keeps its x = 92 dp origin.
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
    val accent = LocalAccent.current
    val width by animateDpAsState(
        targetValue = if (expanded) TvTokens.RailExpandedWidth else TvTokens.RailCollapsedWidth,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "TvRailWidth",
    )

    val railScrim = Brush.horizontalGradient(
        0f to CanopyColors.Chrome.copy(alpha = 0.92f),
        0.75f to CanopyColors.Chrome.copy(alpha = 0.75f),
        1f to Color.Transparent,
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(if (expanded) CanopyColors.GlassChrome.copy(alpha = 0.82f) else CanopyColors.Chrome.copy(alpha = 0.95f)),
    ) {
        // Left-to-right scrim fades the collapsed rail into the content; expanded uses the glass panel.
        if (!expanded) {
            Box(Modifier.fillMaxHeight().width(TvTokens.RailCollapsedWidth).background(railScrim))
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = TvTokens.OverscanVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            destinations.forEach { destination ->
                RailItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    expanded = expanded,
                    accent = accent,
                    onSelect = { onSelect(destination.route) },
                    onFocus = onRailFocus,
                )
            }

            Spacer(Modifier.weight(1f))

            if (nowPlayingTrack != null) {
                NowPlayingTile(
                    track = nowPlayingTrack,
                    expanded = expanded,
                    onClick = onOpenNowPlaying,
                    onFocus = onRailFocus,
                )
                Spacer(Modifier.height(TvTokens.OverscanVertical))
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: RailDestination,
    selected: Boolean,
    expanded: Boolean,
    accent: Color,
    onSelect: () -> Unit,
    onFocus: () -> Unit,
) {
    val pill by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(180),
        label = "RailItemPill_${destination.route}",
    )
    val itemColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(180),
        label = "RailItemColor_${destination.route}",
    )

    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .width(if (expanded) TvTokens.RailExpandedWidth - 20.dp else 48.dp)
            .height(40.dp)
            .tvClickable(TvFocusStyle.NavItem, ItemShape, onFocus = onFocus, onClick = onSelect)
            .clip(ItemShape)
            .background(pill),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Icon(destination.icon, destination.label, tint = itemColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                destination.label,
                color = itemColor,
                style = TvTokens.tvBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                destination.icon,
                destination.label,
                tint = itemColor,
                modifier = Modifier.align(Alignment.CenterVertically).size(22.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingTile(
    track: Track,
    expanded: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .width(if (expanded) TvTokens.RailExpandedWidth - 20.dp else 48.dp)
            .height(if (expanded) 48.dp else 40.dp)
            .tvClickable(TvFocusStyle.NavItem, ItemShape, onFocus = onFocus, onClick = onClick)
            .clip(ItemShape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        if (expanded) {
            ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(32.dp), 6)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Ahora suena",
                    style = TvTokens.tvMeta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.title,
                    style = TvTokens.tvBody.copy(color = CanopyColors.Text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayingBars(accent, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Box(Modifier.align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                ArtworkTile(track.artworkUrl, "Portada de ${track.title}", Modifier.size(28.dp), 6)
                PlayingBars(accent, Modifier.size(12.dp))
            }
        }
    }
}
