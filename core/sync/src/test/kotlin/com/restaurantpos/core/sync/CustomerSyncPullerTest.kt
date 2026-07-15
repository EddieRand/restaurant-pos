package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Customer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** F-024 customer pull-sync unit tests (JVM). */
class CustomerSyncPullerTest {

    private lateinit var port: FakeCustomerPullPort
    private lateinit var watermark: InMemorySyncWatermarkStore
    private lateinit var applied: MutableList<List<Customer>>
    private lateinit var puller: CustomerSyncPuller

    @Before
    fun setup() {
        port = FakeCustomerPullPort()
        watermark = InMemorySyncWatermarkStore()
        applied = mutableListOf()
        puller = CustomerSyncPuller(
            port = port,
            watermarkStore = watermark,
            network = FakeNetworkMonitor(initialOnline = true),
            applyCustomers = { applied.add(it) },
        )
    }

    private fun customer(id: String, updatedAt: Long = 1L) =
        Customer(id = id, name = id, phone = "p$id", registeredAt = 0L, updatedAt = updatedAt)

    @Test
    fun `first pull queries from watermark zero and applies customers`() = runBlocking {
        port.result = CustomerPullResult(serverTime = 1_000L, customers = listOf(customer("c1")))
        puller.pull()
        assertEquals(0L, port.lastSince)
        assertEquals(listOf("c1"), applied.first().map { it.id })
    }

    @Test
    fun `watermark advances and empty result applies nothing`() = runBlocking {
        port.result = CustomerPullResult(serverTime = 4_000L, customers = emptyList())
        puller.pull()
        assertEquals(4_000L, watermark.getLastPullAt(SyncEntityType.CUSTOMER))
        assertTrue(applied.isEmpty())
    }

    @Test
    fun `failed pull leaves watermark untouched`() = runBlocking {
        port.result = null
        puller.pull()
        assertEquals(0L, watermark.getLastPullAt(SyncEntityType.CUSTOMER))
    }
}

class FakeCustomerPullPort : CustomerPullPort {
    var result: CustomerPullResult? = null
    var lastSince: Long = -1L
        private set

    override suspend fun pullCustomers(since: Long): CustomerPullResult? {
        lastSince = since
        return result
    }
}
