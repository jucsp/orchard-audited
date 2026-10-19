// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Hands a validated `orchard://pair` link from the activity to the phone navigation. The raw URI
 * carries the one-time token, so only the parsed [PairingUri.QrInfo] is kept, never the string.
 */
object PairingLinkInbox {
    private val pendingLink = MutableStateFlow<PairingUri.QrInfo?>(null)
    val pending: StateFlow<PairingUri.QrInfo?> = pendingLink.asStateFlow()

    /** Returns false when [uri] is not a valid pairing link; nothing is stored then. */
    fun offer(uri: String): Boolean {
        val info = PairingUri.parse(uri) ?: return false
        pendingLink.value = info
        return true
    }

    fun take(): PairingUri.QrInfo? = pendingLink.getAndUpdate { null }
}
