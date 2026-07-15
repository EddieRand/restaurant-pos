package com.restaurantpos.core.sync

import com.restaurantpos.core.model.KitchenTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Abstraction over the remote API used for pulling kitchen ticket status changes
 * made by other devices (e.g. cashier bumps an item, KDS screen needs to see it).
 *
 * Real implementation: HTTP GET /sync/pull?since=<watermark> with JWT auth.
 */
interface KitchenTicketPullPort {
    /** Returns kitchen tickets whose server `updatedAt` is strictly greater than [since]. */
    suspend fun pullKitchenTickets(since: Long): KitchenTicketPullResult?
}

/**
 * [serverTime] becomes the new watermark on success — always taken from the server's clock,
 * never the client's, to avoid drift between devices.
 */
data class KitchenTicketPullResult(
    val serverTime: Long,
    val tickets: List<KitchenTicket>,
)

/**
 * Pulls cross-device KDS ticket status changes (bump/recall) down to the local Room database.
 *
 * Polls periodically (every [pollIntervalMs]) while online, in addition to pulling once on
 * [start] and on every reconnect, since ticket status changes don't go through push notifications.
 */
class KitchenTicketSyncPuller(
    private val port: KitchenTicketPullPort,
    private val watermarkStore: SyncWatermarkStore,
    private val network: NetworkMonitor,
    private val applyTickets: suspend (List<KitchenTicket>) -> Unit,
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

    /** Fetch and apply changes since the last watermark. Best-effort: swallows network errors. */
    suspend fun pull() {
        val since = watermarkStore.getLastPullAt(SyncEntityType.KITCHEN_TICKET)
        val result = runCatching { port.pullKitchenTickets(since) }.getOrNull() ?: return
        if (result.tickets.isNotEmpty()) {
            applyTickets(result.tickets)
        }
        watermarkStore.setLastPullAt(SyncEntityType.KITCHEN_TICKET, result.serverTime)
    }
}
