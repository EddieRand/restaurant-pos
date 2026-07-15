package com.restaurantpos.core.sync

import com.restaurantpos.core.model.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls server-authoritative menu changes down to the local Room database.
 *
 * The server is authoritative for menu content (admin edits via Web Admin); devices
 * never push menu changes up. Pulls on [start], on every reconnect, and every
 * [pollIntervalMs] — admin edits must reach devices mid-session, not only at app start.
 */
class MenuSyncPuller(
    private val port: MenuPullPort,
    private val watermarkStore: SyncWatermarkStore,
    private val network: NetworkMonitor,
    private val applyItems: suspend (List<MenuItem>) -> Unit,
    private val pollIntervalMs: Long = 30_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            pull()
            launch {
                network.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { pull() }
            }
            while (isActive) {
                delay(pollIntervalMs)
                pull()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** Fetch and apply changes since the last watermark. Best-effort: swallows network errors. */
    suspend fun pull() {
        val since = watermarkStore.getLastPullAt(SyncEntityType.MENU_ITEM)
        val result = runCatching { port.pullMenuItems(since) }.getOrNull() ?: return
        if (result.items.isNotEmpty()) {
            applyItems(result.items)
        }
        watermarkStore.setLastPullAt(SyncEntityType.MENU_ITEM, result.serverTime)
    }
}
