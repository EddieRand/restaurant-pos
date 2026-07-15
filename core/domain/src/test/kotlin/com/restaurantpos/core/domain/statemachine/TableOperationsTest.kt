package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.usecase.CancelReservationUseCase
import com.restaurantpos.core.domain.usecase.CreateReservationUseCase
import com.restaurantpos.core.domain.usecase.MergeTablesUseCase
import com.restaurantpos.core.domain.usecase.SeatReservationUseCase
import com.restaurantpos.core.domain.usecase.SplitTableUseCase
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

// ── Fake repositories ─────────────────────────────────────────────────────────

private class FakeTableRepository : TableRepository {
    val tables = mutableMapOf<String, Table>()

    override fun observeAll(): Flow<List<Table>> = flowOf(tables.values.toList())
    override suspend fun getById(id: String): Table? = tables[id]
    override suspend fun save(table: Table) { tables[table.id] = table }
    override suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?) {
        tables[id] = tables[id]!!.copy(status = status, currentOrderId = orderId)
    }
    override suspend fun applyRemote(tables: List<Table>) {
        tables.forEach { this.tables[it.id] = it }
    }
}

private class FakeOrderRepository : OrderRepository {
    val orders = mutableMapOf<String, Order>()
    val items = mutableMapOf<String, OrderItem>()  // itemId -> OrderItem

    override fun observeActive(): Flow<List<Order>> = flowOf(orders.values.toList())
    override suspend fun getById(id: String): Order? = orders[id]
    override suspend fun getActiveByTable(tableId: String): Order? =
        orders.values.firstOrNull { it.tableId == tableId }
    override suspend fun save(order: Order) { orders[order.id] = order }
    override suspend fun saveItems(newItems: List<OrderItem>) {
        // Remove old versions of these items (by id), then add new ones
        newItems.forEach { items[it.id] = it }
    }
    override suspend fun getItemsByOrder(orderId: String): List<OrderItem> =
        items.values.filter { it.orderId == orderId }
    override suspend fun updateStatus(id: String, status: OrderStatus) {
        orders[id] = orders[id]!!.copy(status = status)
    }
    override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {
        orders[id] = orders[id]!!.copy(subtotalMinorUnit = subtotal, taxTotalMinorUnit = taxTotal)
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

private class FakeReservationRepository : ReservationRepository {
    val reservations = mutableMapOf<String, Reservation>()

    override fun observeByDate(dateEpochMillis: Long): Flow<List<Reservation>> =
        flowOf(reservations.values.toList())
    override suspend fun getById(id: String): Reservation? = reservations[id]
    override suspend fun save(reservation: Reservation) { reservations[reservation.id] = reservation }
    override suspend fun updateStatus(id: String, status: ReservationStatus) {
        reservations[id] = reservations[id]!!.copy(status = status)
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun makeItem(
    orderId: String,
    priceMinorUnit: Long,
    qty: Int = 1,
): OrderItem = OrderItem(
    id = UUID.randomUUID().toString(),
    orderId = orderId,
    menuItemId = "item-1",
    menuItemNameSnapshot = mapOf("en" to "Burger"),
    quantity = qty,
    unitPriceMinorUnit = priceMinorUnit,
    taxRateId = null,
)

private fun makeOrder(
    tableId: String,
    subtotal: Long = 0L,
    tax: Long = 0L,
): Order {
    val id = UUID.randomUUID().toString()
    return Order(
        id = id,
        tableId = tableId,
        sourceTerminalId = "cashier-1",
        subtotalMinorUnit = subtotal,
        taxTotalMinorUnit = tax,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

private fun makeTable(
    orderId: String? = null,
    status: TableStatus = if (orderId != null) TableStatus.OCCUPIED else TableStatus.AVAILABLE,
): Table = Table(
    id = UUID.randomUUID().toString(),
    name = "T${(Math.random() * 100).toInt()}",
    sectionId = "Indoor",
    capacity = 4,
    currentOrderId = orderId,
    status = status,
)

// ── Tests ─────────────────────────────────────────────────────────────────────

class TableOperationsTest {

    private lateinit var tableRepo: FakeTableRepository
    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var reservationRepo: FakeReservationRepository

    @Before
    fun setUp() {
        tableRepo = FakeTableRepository()
        orderRepo = FakeOrderRepository()
        reservationRepo = FakeReservationRepository()
    }

    // ── Merge: secondary table becomes AVAILABLE ──────────────────────────────

    @Test
    fun `merge tables - secondary table becomes AVAILABLE`() = runBlocking {
        val primaryOrder = makeOrder("p1", subtotal = 1000L, tax = 100L)
        val secondaryOrder = makeOrder("s1", subtotal = 500L, tax = 50L)
        val primaryTable = makeTable(orderId = primaryOrder.id, status = TableStatus.OCCUPIED)
            .copy(id = "p1")
        val secondaryTable = makeTable(orderId = secondaryOrder.id, status = TableStatus.OCCUPIED)
            .copy(id = "s1")

        tableRepo.tables["p1"] = primaryTable
        tableRepo.tables["s1"] = secondaryTable
        orderRepo.orders[primaryOrder.id] = primaryOrder
        orderRepo.orders[secondaryOrder.id] = secondaryOrder

        // Add items so totals are driven by items
        val item1 = makeItem(primaryOrder.id, 1000L)
        val item2 = makeItem(secondaryOrder.id, 500L)
        orderRepo.items[item1.id] = item1
        orderRepo.items[item2.id] = item2

        val useCase = MergeTablesUseCase(tableRepo, orderRepo)
        useCase("p1", "s1")

        val updatedSecondary = tableRepo.tables["s1"]!!
        assertEquals(TableStatus.AVAILABLE, updatedSecondary.status)
        assertNull(updatedSecondary.currentOrderId)
    }

    // ── Merge: primary order amount = sum of both original orders ─────────────

    @Test
    fun `merge tables - primary order subtotal equals sum of both orders`() = runBlocking {
        val primaryOrder = makeOrder("p1")
        val secondaryOrder = makeOrder("s1")
        val primaryTable = makeTable(orderId = primaryOrder.id, status = TableStatus.OCCUPIED)
            .copy(id = "p1")
        val secondaryTable = makeTable(orderId = secondaryOrder.id, status = TableStatus.OCCUPIED)
            .copy(id = "s1")

        tableRepo.tables["p1"] = primaryTable
        tableRepo.tables["s1"] = secondaryTable
        orderRepo.orders[primaryOrder.id] = primaryOrder
        orderRepo.orders[secondaryOrder.id] = secondaryOrder

        val item1 = makeItem(primaryOrder.id, 800L)
        val item2 = makeItem(primaryOrder.id, 200L)
        val item3 = makeItem(secondaryOrder.id, 600L)
        val item4 = makeItem(secondaryOrder.id, 400L)
        listOf(item1, item2, item3, item4).forEach { orderRepo.items[it.id] = it }

        val useCase = MergeTablesUseCase(tableRepo, orderRepo)
        useCase("p1", "s1")

        val updatedPrimaryOrder = orderRepo.orders[primaryOrder.id]!!
        assertEquals(2000L, updatedPrimaryOrder.subtotalMinorUnit)
    }

    // ── Split: both tables' amounts sum to original ───────────────────────────

    @Test
    fun `split table - totals sum preserved`() = runBlocking {
        val sourceOrder = makeOrder("src")
        val sourceTable = makeTable(orderId = sourceOrder.id, status = TableStatus.OCCUPIED)
            .copy(id = "src")
        val targetTable = makeTable(status = TableStatus.AVAILABLE).copy(id = "tgt")

        tableRepo.tables["src"] = sourceTable
        tableRepo.tables["tgt"] = targetTable
        orderRepo.orders[sourceOrder.id] = sourceOrder

        val item1 = makeItem(sourceOrder.id, 1000L)
        val item2 = makeItem(sourceOrder.id, 600L)
        val item3 = makeItem(sourceOrder.id, 400L)
        listOf(item1, item2, item3).forEach { orderRepo.items[it.id] = it }

        val useCase = SplitTableUseCase(tableRepo, orderRepo)
        val newOrderId = useCase(sourceOrder.id, "tgt", listOf(item2.id, item3.id))

        val srcSubtotal = orderRepo.orders[sourceOrder.id]!!.subtotalMinorUnit
        val tgtSubtotal = orderRepo.orders[newOrderId]!!.subtotalMinorUnit
        assertEquals(2000L, srcSubtotal + tgtSubtotal)
    }

    // ── Reservation created: table becomes RESERVED ───────────────────────────

    @Test
    fun `create reservation - table becomes RESERVED`() = runBlocking {
        val table = makeTable(status = TableStatus.AVAILABLE).copy(id = "t1")
        tableRepo.tables["t1"] = table

        val useCase = CreateReservationUseCase(tableRepo, reservationRepo, DefaultRegionConfig)
        val scheduledAt = System.currentTimeMillis() + 3_600_000L
        useCase("t1", "Alice", 3, scheduledAt)

        val updatedTable = tableRepo.tables["t1"]!!
        assertEquals(TableStatus.RESERVED, updatedTable.status)
    }

    // ── SeatReservation: table becomes OCCUPIED, reservation SEATED ───────────

    @Test
    fun `seat reservation - table OCCUPIED and reservation SEATED`() = runBlocking {
        val table = makeTable(status = TableStatus.RESERVED).copy(id = "t1")
        tableRepo.tables["t1"] = table

        val reservation = Reservation(
            id = "r1",
            tableId = "t1",
            guestName = "Bob",
            guestCount = 2,
            scheduledAt = System.currentTimeMillis() + 1_800_000L,
            status = ReservationStatus.CONFIRMED,
        )
        reservationRepo.reservations["r1"] = reservation

        val useCase = SeatReservationUseCase(reservationRepo, tableRepo, orderRepo)
        useCase("r1")

        val updatedTable = tableRepo.tables["t1"]!!
        assertEquals(TableStatus.OCCUPIED, updatedTable.status)
        assertNotNull(updatedTable.currentOrderId)

        val updatedReservation = reservationRepo.reservations["r1"]!!
        assertEquals(ReservationStatus.SEATED, updatedReservation.status)
    }

    // ── CancelReservation ─────────────────────────────────────────────────────

    @Test
    fun `cancel reservation - table returns to AVAILABLE`() = runBlocking {
        val table = makeTable(status = TableStatus.RESERVED).copy(id = "t1")
        tableRepo.tables["t1"] = table

        val reservation = Reservation(
            id = "r1", tableId = "t1", guestName = "Bob", guestCount = 2,
            scheduledAt = System.currentTimeMillis() + 3_600_000L,
            status = ReservationStatus.CONFIRMED,
        )
        reservationRepo.reservations["r1"] = reservation

        val useCase = CancelReservationUseCase(reservationRepo, tableRepo)
        val result = useCase("r1")

        assertTrue(result is CancelReservationUseCase.Result.Success)
        assertEquals(ReservationStatus.CANCELLED, reservationRepo.reservations["r1"]!!.status)
        assertEquals(TableStatus.AVAILABLE, tableRepo.tables["t1"]!!.status)
    }

    @Test
    fun `cancel reservation - non-existent returns error`() = runBlocking {
        val useCase = CancelReservationUseCase(reservationRepo, tableRepo)
        val result = useCase("nonexistent")
        assertTrue(result is CancelReservationUseCase.Result.Error)
    }

    @Test
    fun `cancel reservation - already seated returns error`() = runBlocking {
        val reservation = Reservation(
            id = "r1", tableId = "t1", guestName = "Carol", guestCount = 1,
            scheduledAt = 0L, status = ReservationStatus.SEATED,
        )
        reservationRepo.reservations["r1"] = reservation

        val useCase = CancelReservationUseCase(reservationRepo, tableRepo)
        val result = useCase("r1")
        assertTrue(result is CancelReservationUseCase.Result.Error)
    }
}
