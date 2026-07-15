package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Customer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Abstraction over the remote API for pulling the customer directory.
 * Real impl: HTTP GET /sync/customers?since=<watermark> with JWT.
 */
interface CustomerPullPort {
    /** Returns customers whose server `updatedAt` is strictly greater than [since]. */
    suspend fun pullCustomers(since: Long): CustomerPullResult?
}

data class CustomerPullResult(
    val serverTime: Long,
    val customers: List<Customer>,
)

/**
 * Pulls customer-directory changes down to the local Room database. Watermark-based,
 * applied via [applyCustomers] with last-write-wins by updatedAt (F-024).
 */
class CustomerSyncPuller(
    private val port: CustomerPullPort,
    private val watermarkStore: SyncWatermarkStore,
    private val network: NetworkMonitor,
    private val applyCustomers: suspend (List<Customer>) -> Unit,
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

    suspend fun pull() {
        val since = watermarkStore.getLastPullAt(SyncEntityType.CUSTOMER)
        val result = runCatching { port.pullCustomers(since) }.getOrNull() ?: return
        if (result.customers.isNotEmpty()) applyCustomers(result.customers)
        watermarkStore.setLastPullAt(SyncEntityType.CUSTOMER, result.serverTime)
    }
}
