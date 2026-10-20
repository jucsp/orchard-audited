// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 SFG545

package dev.sfg.orchard.mobile.app

import dev.sfg.orchard.mobile.OrchardGraph
import dev.sfg.orchard.mobile.auth.YouTubeAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Switches between saved YouTube accounts without carrying one account's library into another. */
internal class AccountSwitcher(
    private val graph: OrchardGraph,
    private val scope: CoroutineScope,
    private val onAccountChanged: () -> Unit,
    private val showWarning: (String) -> Unit,
) {
    val saved: StateFlow<List<YouTubeAccount>> = graph.auth.accounts

    fun switchTo(accountId: String) = change("Could not switch YouTube account.") {
        graph.auth.switchAccount(accountId)
    }

    fun remove(accountId: String) = change("Could not remove the YouTube account.") {
        graph.auth.removeAccount(accountId)
    }

    private fun change(failure: String, action: suspend () -> Unit) {
        scope.launch {
            val previous = activeAccountId()
            runCatching { action() }
                .onSuccess {
                    if (activeAccountId() == previous) return@onSuccess
                    graph.library.clearAccountCollections()
                    if (graph.auth.session() != null) graph.library.refreshSignedInLibrary()
                    onAccountChanged()
                }
                .onFailure { showWarning(it.message ?: failure) }
        }
    }

    private fun activeAccountId(): String? = graph.auth.accounts.value.firstOrNull { it.isActive }?.id
}
