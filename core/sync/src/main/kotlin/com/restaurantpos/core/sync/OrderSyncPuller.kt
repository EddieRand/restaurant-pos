package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Abstraction over the remote API used for pulling order changes made by other
 * devices (e.g. kiosk places an order the cashier must settle, KDS marks an
 * order READY_FOR_PICKUP that the pickup display must show).
 *
 * Real implementation: HTTP GET /sync/pull?since=<watermark> with JWT auth.
 */
interface OrderPullPort {
    /** Returns orders whose server `updatedAt` is strictly greater than [since], plus their items. */
    suspend fun pullOrders(since: Long): OrderPullResult?
}

/**
 * [serverTime] becomes the new watermark on success — always taken from the server's clock,
 * never the client's, to avoid drift between devices.
 */
data class OrderPullResult(
    val serverTime: Long,
    val orders: List<Order>,
    val orderItems: List<OrderItem>,
)

/**
 * Pulls cross-device order changes down to the local Room database.
 *
 * Polls periodically (every [pollIntervalMs]) while online, in addition to pulling once on
 * [start] and on every reconnect.
 */
class OrderSyncPuller(
    private val port: OrderPullPort,
    private val watermarkStore: SyncWatermarkStore,
    private val network: NetworkMonitor,
    private val applyOrders: suspend (List<Order>, List<OrderItem>) -> Unit,
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
        val since = watermarkStore.getLastPullAt(SyncEntityType.ORDER)
        val result = runCatching { port.pullOrders(since) }.getOrNull() ?: return
        if (result.orders.isNotEmpty() || result.orderItems.isNotEmpty()) {
            applyOrders(result.orders, result.orderItems)
        }
        watermarkStore.setLastPullAt(SyncEntityType.ORDER, result.serverTime)
    }
}
