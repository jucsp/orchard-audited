// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sfg.orchard.connect.R
import dev.sfg.orchard.mobile.playback.auto.AutoInstallSource
import dev.sfg.orchard.mobile.ui.tv.isTvLayout

/**
 * Android Auto readiness under Connections -> Devices (SDD-android-auto §5.5). Only reads this
 * app's own installer; it never writes Android Auto settings (AUTO-INV-3). Hidden on TV.
 */
@Composable
internal fun AndroidAutoSettingsRow() {
    if (isTvLayout()) return
    val context = LocalContext.current
    val installer = remember(context) {
        AutoInstallSource(context.packageManager, context.packageName).installingPackage()
    }
    val fromPlay = AutoInstallSource.isPlayStore(installer)
    var expanded by rememberSaveable { mutableStateOf(!fromPlay) }

    SettingsRow(
        title = stringResource(R.string.auto_title),
        subtitle = stringResource(if (fromPlay) R.string.auto_ready else R.string.auto_unknown_sources),
        icon = Icons.Rounded.DirectionsCar,
        modifier = if (fromPlay) Modifier else Modifier.clickable { expanded = !expanded },
    ) {
        if (!fromPlay) {
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.auto_steps_title),
                tint = SettingsStyle.Chevron,
            )
        }
    }
    if (!fromPlay && expanded) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(
                stringResource(R.string.auto_installer, installer ?: stringResource(R.string.auto_installer_unknown)),
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyle.Description,
            )
            Spacer(Modifier.height(8.dp))
            for (step in AUTO_STEPS) {
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium, color = SettingsStyle.Title)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private val AUTO_STEPS = listOf(
    R.string.auto_step_developer,
    R.string.auto_step_unknown_sources,
    R.string.auto_step_reconnect,
    R.string.auto_step_launcher,
)
