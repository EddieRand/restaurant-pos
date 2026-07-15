package com.restaurantpos.core.hardware

import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.routing.KitchenRouter as DomainKitchenRouter
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Batch 4 hardware unit tests (JVM, no Android dependency).
 *
 * Uses FakePrinter (not MockPrinter which needs android.util.Log) and FakeOrderRepo.
 */
class HardwareBatch4Test {

    private lateinit var fakePrinter: FakePrinter
    private lateinit var orderRepo: FakeOrderRepo

    @Before
    fun setup() {
        fakePrinter = FakePrinter()
        orderRepo = FakeOrderRepo()
    }

    // ── KitchenRouter ─────────────────────────────────────────────────────────

    @Test
    fun `router routes known category to configured station`() {
        val router = KitchenRouter(routes = mapOf("cat-bar" to "bar", "cat-grill" to "grill"))
        assertEquals("bar", router.stationFor("cat-bar"))
        assertEquals("grill", router.stationFor("cat-grill"))
    }

    @Test
    fun `router falls back to defaultStation for unknown category`() {
        val router = KitchenRouter(routes = mapOf("cat-bar" to "bar"), defaultStationId = "main-kitchen")
        assertEquals("main-kitchen", router.stationFor("cat-unknown"))
    }

    @Test
    fun `router groupByStation splits items correctly`() {
        val router = KitchenRouter(routes = mapOf("cat-bar" to "bar"), defaultStationId = "kitchen")
        data class Item(val id: String, val categoryId: String)
        val items = listOf(
            Item("i1", "cat-bar"),
            Item("i2", "cat-food"),
            Item("i3", "cat-bar"),
        )
        val grouped = router.groupByStation(items) { it.categoryId }
        assertEquals(2, grouped["bar"]!!.size)
        assertEquals(1, grouped["kitchen"]!!.size)
    }

    // ── PrintReceiptUseCase ────────────────────────────────────────────────────

    @Test
    fun `print receipt - success path calls printer once`() = runBlocking {
        val order = makeClosedOrder("o1", total = 2000L)
        orderRepo.orders[order.id] = order
        val item = makeItem("o1", 2000L)
        orderRepo.items[item.id] = item

        val useCase = PrintReceiptUseCase(orderRepo, fakePrinter, DefaultRegionConfig)
        val result = useCase("o1")

        assertTrue(result is PrintReceiptUseCase.Result.Success)
        assertEquals(1, fakePrinter.printedReceipts.size)
        assertEquals("o1", fakePrinter.printedReceipts[0].orderId)
    }

    @Test
    fun `print receipt - order not found returns error`() = runBlocking {
        val useCase = PrintReceiptUseCase(orderRepo, fakePrinter, DefaultRegionConfig)
        val result = useCase("nonexistent")
        assertTrue(result is PrintReceiptUseCase.Result.Error)
        assertEquals(0, fakePrinter.printedReceipts.size)
    }

    @Test
    fun `print receipt - draft order is rejected`() = runBlocking {
        val order = makeOrder("o1", OrderStatus.DRAFT, total = 500L)
        orderRepo.orders[order.id] = order

        val result = PrintReceiptUseCase(orderRepo, fakePrinter, DefaultRegionConfig)("o1")
        assertTrue(result is PrintReceiptUseCase.Result.Error)
    }

    @Test
    fun `print receipt - tax and service charge lines included`() = runBlocking {
        val order = makeClosedOrder("o1", total = 1150L).copy(
            subtotalMinorUnit = 1000L, taxTotalMinorUnit = 100L, serviceChargeMinorUnit = 50L,
        )
        orderRepo.orders[order.id] = order

        val useCase = PrintReceiptUseCase(orderRepo, fakePrinter, DefaultRegionConfig)
        useCase("o1")

        val receipt = fakePrinter.printedReceipts[0]
        assertTrue(receipt.lines.any { it.label == "Tax" && it.amountMinorUnit == 100L })
        assertTrue(receipt.lines.any { it.label == "Service Charge" && it.amountMinorUnit == 50L })
        assertEquals(1150L, receipt.totalMinorUnit)
    }

    // ── PrintKitchenTicketUseCase ─────────────────────────────────────────────

    @Test
    fun `kitchen ticket - items routed to correct stations`() = runBlocking {
        val order = makeOrder("o1", OrderStatus.PLACED, total = 3000L)
        orderRepo.orders[order.id] = order

        val item1 = makeItem("o1", 1200L, categoryId = "cat-grill")   // → grill
        val item2 = makeItem("o1", 800L,  categoryId = "cat-bar")    // → bar
        val item3 = makeItem("o1", 1000L, categoryId = "cat-salad")  // → kitchen (default)
        listOf(item1, item2, item3).forEach { orderRepo.items[it.id] = it }

        val router = DomainKitchenRouter(
            routes = mapOf("cat-grill" to "grill", "cat-bar" to "bar"),
            defaultStationId = "kitchen",
        )
        val useCase = PrintKitchenTicketUseCase(orderRepo, fakePrinter, router)
        val result = useCase("o1")

        assertTrue(result is PrintKitchenTicketUseCase.Result.Success)
        val stationResults = (result as PrintKitchenTicketUseCase.Result.Success).stationResults
        assertEquals(3, stationResults.size)
        assertTrue(stationResults.all { it.result is PrintResult.Success })
        assertEquals(3, fakePrinter.printedTickets.size)
        val stations = fakePrinter.printedTickets.map { it.stationId }.toSet()
        assertEquals(setOf("grill", "bar", "kitchen"), stations)
    }

    @Test
    fun `kitchen ticket - no pending items returns error`() = runBlocking {
        val order = makeOrder("o1", OrderStatus.PLACED, total = 1000L)
        orderRepo.orders[order.id] = order
        // item already sent (ORDERED status)
        val item = makeItem("o1", 1000L, status = OrderItemStatus.PLACED)
        orderRepo.items[item.id] = item

        val router = DomainKitchenRouter(emptyMap())
        val result = PrintKitchenTicketUseCase(orderRepo, fakePrinter, router)("o1")
        assertTrue(result is PrintKitchenTicketUseCase.Result.Error)
    }

    @Test
    fun `kitchen ticket - modifier notes included in line`() = runBlocking {
        val order = makeOrder("o1", OrderStatus.PLACED, total = 1500L)
        orderRepo.orders[order.id] = order
        val item = makeItem("o1", 1500L).copy(
            selectedModifiers = listOf(
                SelectedModifier(
                    groupId = "cook", modifierId = "well-done",
                    nameSnapshot = mapOf("en" to "Well Done"),
                    priceAdjustmentMinorUnit = 0L,
                )
            )
        )
        orderRepo.items[item.id] = item

        val router = DomainKitchenRouter(emptyMap())
        PrintKitchenTicketUseCase(orderRepo, fakePrinter, router)("o1")

        val ticket = fakePrinter.printedTickets[0]
        assertTrue(ticket.items[0].notes.contains("Well Done"))
    }

    // ── CashDrawer ─────────────────────────────────────────────────────────────

    @Test
    fun `mock cash drawer open increments count`() = runBlocking {
        val drawer = MockCashDrawerFake()
        assertEquals(0, drawer.openCount)
        drawer.open()
        drawer.open()
        assertEquals(2, drawer.openCount)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private var counter = 0
    private fun makeItem(
        orderId: String,
        price: Long,
        menuItemId: String = "mi-${counter}",
        status: OrderItemStatus = OrderItemStatus.PENDING,
        categoryId: String? = null,
    ) = OrderItem(
        id = "item-${counter++}", orderId = orderId, menuItemId = menuItemId,
        menuItemNameSnapshot = mapOf("en" to "Item $counter"),
        quantity = 1, unitPriceMinorUnit = price, taxRateId = null,
        status = status, categoryId = categoryId,
    )

    private fun makeOrder(id: String, status: OrderStatus, total: Long) = Order(
        id = id, sourceTerminalId = "t1",
        subtotalMinorUnit = total, taxTotalMinorUnit = 0L,
        serviceChargeMinorUnit = 0L, discountMinorUnit = 0L,
        status = status, createdAt = 0L, updatedAt = 0L,
    )

    private fun makeClosedOrder(id: String, total: Long) = makeOrder(id, OrderStatus.CLOSED, total)
}

// ── Test doubles (no android.util.Log dependency) ────────────────────────────

class FakePrinter : PrinterPort {
    val printedReceipts = mutableListOf<ReceiptData>()
    val printedTickets = mutableListOf<KitchenTicketData>()

    override suspend fun printReceipt(data: ReceiptData): PrintResult {
        printedReceipts.add(data); return PrintResult.Success
    }
    override suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult {
        printedTickets.add(data); return PrintResult.Success
    }
    override suspend fun isConnected(): Boolean = true
}

class MockCashDrawerFake : CashDrawerPort {
    var openCount = 0; private set
    override suspend fun open(): DrawerResult { openCount++; return DrawerResult.Success }
    override suspend fun isConnected(): Boolean = true
}

class FakeOrderRepo : OrderRepository {
    val orders = mutableMapOf<String, Order>()
    val items = mutableMapOf<String, OrderItem>()

    override fun observeActive(): Flow<List<Order>> = flowOf(orders.values.toList())
    override suspend fun getById(id: String): Order? = orders[id]
    override suspend fun getActiveByTable(tableId: String): Order? =
        orders.values.firstOrNull { it.tableId == tableId }
    override suspend fun save(order: Order) { orders[order.id] = order }
    override suspend fun saveItems(items: List<OrderItem>) { items.forEach { this.items[it.id] = it } }
    override suspend fun getItemsByOrder(orderId: String): List<OrderItem> =
        items.values.filter { it.orderId == orderId }
    override suspend fun updateStatus(id: String, status: OrderStatus) {
        orders[id]?.let { orders[id] = it.copy(status = status) }
    }
    override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {
        orders[id]?.let { orders[id] = it.copy(subtotalMinorUnit = subtotal, taxTotalMinorUnit = taxTotal) }
    }
    override suspend fun setTip(id: String, tipMinorUnit: Long) {
        orders[id]?.let { orders[id] = it.copy(tipMinorUnit = tipMinorUnit) }
    }
    override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> =
        orders.values.filter { it.status == OrderStatus.CLOSED && it.updatedAt in fromEpoch..toEpoch }
    override suspend fun searchOrders(query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?): List<Order> =
        emptyList()

    override fun observeReadyForPickup(): Flow<List<Order>> = flowOf(emptyList())
    override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {
        orders[id]?.let { orders[id] = it.copy(fulfillmentStatus = status) }
    }
    override suspend fun countPickupCodesSince(sinceEpoch: Long): Int = 0
    override suspend fun applyRemote(newOrders: List<Order>, newItems: List<OrderItem>) {}
    override suspend fun setPickupCode(id: String, pickupCode: String) {
        orders[id]?.let { orders[id] = it.copy(pickupCode = pickupCode) }
    }
}
