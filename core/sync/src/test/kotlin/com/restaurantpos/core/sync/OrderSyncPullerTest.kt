package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Order pull-sync unit tests (F-004) — same contract as MenuSyncPullerTest:
 * watermark from server clock, failure leaves watermark untouched, empty pull
 * advances watermark without applying.
 */
class OrderSyncPullerTest {

    private lateinit var port: FakeOrderPullPort
    private lateinit var watermark: InMemorySyncWatermarkStore
    private lateinit var applied: MutableList<Pair<List<Order>, List<OrderItem>>>
    private lateinit var puller: OrderSyncPuller

    @Before
    fun setup() {
        port = FakeOrderPullPort()
        watermark = InMemorySyncWatermarkStore()
        applied = mutableListOf()
        puller = OrderSyncPuller(
            port = port,
            watermarkStore = watermark,
            network = FakeNetworkMonitor(initialOnline = true),
            applyOrders = { orders, items -> applied.add(orders to items) },
        )
    }

    @Test
    fun `first pull queries from watermark zero and applies orders with items`() = runBlocking {
        port.result = OrderPullResult(
            serverTime = 1_000L,
            orders = listOf(order("o1")),
            orderItems = listOf(item("i1", "o1")),
        )

        puller.pull()

        assertEquals(0L, port.lastSinceQueried)
        assertEquals(1, applied.size)
        assertEquals(listOf("o1"), applied.first().first.map { it.id })
        assertEquals(listOf("i1"), applied.first().second.map { it.id })
    }

    @Test
    fun `watermark advances to server time after successful pull`() = runBlocking {
        port.result = OrderPullResult(serverTime = 5_000L, orders = listOf(order("o1")), orderItems = emptyList())

        puller.pull()

        assertEquals(5_000L, watermark.getLastPullAt(SyncEntityType.ORDER))
    }

    @Test
    fun `subsequent pull uses the stored watermark as cursor`() = runBlocking {
        port.result = OrderPullResult(serverTime = 5_000L, orders = listOf(order("o1")), orderItems = emptyList())
        puller.pull()

        port.result = OrderPullResult(serverTime = 9_000L, orders = emptyList(), orderItems = emptyList())
        puller.pull()

        assertEquals(5_000L, port.lastSinceQueried)
        assertEquals(9_000L, watermark.getLastPullAt(SyncEntityType.ORDER))
    }

    @Test
    fun `empty result still advances watermark without invoking apply`() = runBlocking {
        port.result = OrderPullResult(serverTime = 3_000L, orders = emptyList(), orderItems = emptyList())

        puller.pull()

        assertEquals(0, applied.size)
        assertEquals(3_000L, watermark.getLastPullAt(SyncEntityType.ORDER))
    }

    @Test
    fun `failed pull leaves watermark untouched`() = runBlocking {
        port.result = null // simulates network/parse failure

        puller.pull()

        assertEquals(0, applied.size)
        assertEquals(0L, watermark.getLastPullAt(SyncEntityType.ORDER))
    }

    @Test
    fun `ready-for-pickup state arrives via pull`() = runBlocking {
        port.result = OrderPullResult(
            serverTime = 2_000L,
            orders = listOf(order("o9").copy(pickupCode = "42", fulfillmentStatus = OrderFulfillmentStatus.READY_FOR_PICKUP)),
            orderItems = emptyList(),
        )

        puller.pull()

        val pulled = applied.single().first.single()
        assertEquals("42", pulled.pickupCode)
        assertEquals(OrderFulfillmentStatus.READY_FOR_PICKUP, pulled.fulfillmentStatus)
    }

    private fun order(id: String) = Order(
        id = id,
        sourceTerminalId = "kiosk-1",
        createdAt = 0L,
        updatedAt = 100L,
    )

    private fun item(id: String, orderId: String) = OrderItem(
        id = id,
        orderId = orderId,
        menuItemId = "mi-1",
        menuItemNameSnapshot = mapOf("en" to "Burger"),
        quantity = 1,
        unitPriceMinorUnit = 1000L,
        taxRateId = null,
    )
}

class FakeOrderPullPort : OrderPullPort {
    var result: OrderPullResult? = null
    var lastSinceQueried: Long = -1L
        private set

    override suspend fun pullOrders(since: Long): OrderPullResult? {
        lastSinceQueried = since
        return result
    }
}
