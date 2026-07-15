package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Table
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Abstraction over the remote API for pulling cross-device table changes (status,
 * current order, layout). Real impl: HTTP GET /sync/tables?since=<watermark> with JWT.
 */
interface TablePullPort {
    /** Returns tables whose server `updatedAt` is strictly greater than [since]. */
    suspend fun pullTables(since: Long): TablePullResult?
}

data class TablePullResult(
    val serverTime: Long,
    val tables: List<Table>,
)

/**
 * Pulls cross-device table changes down to the local Room database. Watermark-based
 * (tables carry updatedAt); applied via [applyTables] using last-write-wins so a
 * device's own in-flight seating is never clobbered by a stale pull (F-024).
 */
class TableSyncPuller(
    private val port: TablePullPort,
    private val watermarkStore: SyncWatermarkStore,
    private val network: NetworkMonitor,
    private val applyTables: suspend (List<Table>) -> Unit,
    private val pollIntervalMs: Long = 5_000L,
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

    suspend fun pull() {
        val since = watermarkStore.getLastPullAt(SyncEntityType.TABLE)
        val result = runCatching { port.pullTables(since) }.getOrNull() ?: return
        if (result.tables.isNotEmpty()) applyTables(result.tables)
        watermarkStore.setLastPullAt(SyncEntityType.TABLE, result.serverTime)
    }
}
