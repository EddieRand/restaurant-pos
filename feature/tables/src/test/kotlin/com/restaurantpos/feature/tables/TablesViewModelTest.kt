package com.restaurantpos.feature.tables

import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.domain.usecase.CancelReservationUseCase
import com.restaurantpos.core.domain.usecase.CreateReservationUseCase
import com.restaurantpos.core.domain.usecase.MergeTablesUseCase
import com.restaurantpos.core.domain.usecase.SeatReservationUseCase
import com.restaurantpos.core.domain.usecase.SplitTableUseCase
import com.restaurantpos.core.domain.usecase.TransferTableUseCase
import com.restaurantpos.core.model.*
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.sync.InMemorySyncOutbox
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TablesViewModelTest {

    private lateinit var tableRepo: FakeTableRepository
    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var reservationRepo: FakeReservationRepository
    private lateinit var configRepo: InMemoryConfigRepository
    private lateinit var sessionRepo: FakeSessionRepository
    private lateinit var outbox: InMemorySyncOutbox
    private lateinit var cdsPhaseBroadcaster: CdsPhaseBroadcaster

    private val testUser = User(id = "op-1", displayName = "Server", roleId = "waiter", pinHash = "x", createdAt = 0L)

    private fun availableTable(id: String, capacity: Int = 4) = Table(
        id = id, name = id.uppercase(), sectionId = "main", capacity = capacity,
        status = TableStatus.AVAILABLE, updatedAt = 0L,
    )

    private fun buildViewModel(): TablesViewModel {
        val regionConfig = configRepo.current()
        return TablesViewModel(
            tableRepo = tableRepo,
            orderRepo = orderRepo,
            reservationRepo = reservationRepo,
            mergeTablesUseCase = MergeTablesUseCase(tableRepo, orderRepo),
            splitTableUseCase = SplitTableUseCase(tableRepo, orderRepo),
            transferTableUseCase = TransferTableUseCase(orderRepo, tableRepo),
            createReservationUseCase = CreateReservationUseCase(tableRepo, reservationRepo, regionConfig),
            cancelReservationUseCase = CancelReservationUseCase(reservationRepo, tableRepo),
            seatReservationUseCase = SeatReservationUseCase(reservationRepo, tableRepo, orderRepo),
            configRepo = configRepo,
            sessionRepo = sessionRepo,
            cdsPhaseBroadcaster = cdsPhaseBroadcaster,
        )
    }

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        tableRepo = FakeTableRepository()
        orderRepo = FakeOrderRepository()
        reservationRepo = FakeReservationRepository()
        configRepo = InMemoryConfigRepository()
        sessionRepo = FakeSessionRepository(testUser)
        outbox = InMemorySyncOutbox()
        cdsPhaseBroadcaster = CdsPhaseBroadcaster(SyncWriter(outbox), scope = CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Seat table ───────────────────────────────────────────────────────────

    @Test
    fun `seating an available table creates a DINE_IN order and marks it OCCUPIED`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1")
        val vm = buildViewModel()
        var createdOrderId: String? = null

        vm.seatTable("t1", guestCount = 3) { createdOrderId = it }

        assertNotNull(createdOrderId)
        val order = orderRepo.orders[createdOrderId]
        assertNotNull(order)
        assertEquals(OrderType.DINE_IN, order!!.type)
        assertEquals(3, order.guestCount)
        assertEquals(TableStatus.OCCUPIED, tableRepo.tables["t1"]!!.status)
        assertEquals(createdOrderId, tableRepo.tables["t1"]!!.currentOrderId)
    }

    @Test
    fun `seating a table broadcasts ORDER phase to the customer display`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1")
        val vm = buildViewModel()

        vm.seatTable("t1") { }

        val cdsRecord = outbox.getPending().firstOrNull { it.entityType == SyncEntityType.CDS_STATE }
        assertNotNull("expected a CDS_STATE broadcast", cdsRecord)
        assertTrue(cdsRecord!!.payload.contains("\"ORDER\""))
    }

    @Test
    fun `seating a table that is not AVAILABLE is a no-op`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.OCCUPIED)
        val vm = buildViewModel()
        var called = false

        vm.seatTable("t1") { called = true }

        assertEquals(false, called)
        assertEquals(0, orderRepo.orders.size)
    }

    // ── Merge tables ─────────────────────────────────────────────────────────

    @Test
    fun `merging two occupied tables frees the secondary table and links its order`() = runTest {
        val primaryOrderId = "order-p"
        val secondaryOrderId = "order-s"
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.OCCUPIED, currentOrderId = primaryOrderId)
        tableRepo.tables["t2"] = availableTable("t2").copy(status = TableStatus.OCCUPIED, currentOrderId = secondaryOrderId)
        orderRepo.orders[primaryOrderId] = Order(id = primaryOrderId, tableId = "t1", sourceTerminalId = "pos-1", createdAt = 0L, updatedAt = 0L)
        orderRepo.orders[secondaryOrderId] = Order(id = secondaryOrderId, tableId = "t2", sourceTerminalId = "pos-1", createdAt = 0L, updatedAt = 0L)
        val vm = buildViewModel()
        var error: String? = null

        vm.mergeTables("t1", "t2", onError = { error = it })

        assertNull(error)
        assertEquals(TableStatus.AVAILABLE, tableRepo.tables["t2"]!!.status)
        assertNull(tableRepo.tables["t2"]!!.currentOrderId)
        assertTrue(orderRepo.orders[primaryOrderId]!!.mergedTableIds.contains("t2"))
    }

    @Test
    fun `merging surfaces an error when the primary table doesn't exist`() = runTest {
        tableRepo.tables["t2"] = availableTable("t2").copy(status = TableStatus.OCCUPIED, currentOrderId = "order-s")
        val vm = buildViewModel()
        var error: String? = null

        vm.mergeTables("missing", "t2", onError = { error = it })

        assertNotNull(error)
    }

    // ── Split table ──────────────────────────────────────────────────────────

    @Test
    fun `splitting selected items onto an available table creates a new order`() = runTest {
        val sourceOrderId = "order-src"
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.OCCUPIED, currentOrderId = sourceOrderId)
        tableRepo.tables["t2"] = availableTable("t2")
        orderRepo.orders[sourceOrderId] = Order(id = sourceOrderId, tableId = "t1", sourceTerminalId = "pos-1", createdAt = 0L, updatedAt = 0L)
        val item = OrderItem(
            id = "item-1", orderId = sourceOrderId, menuItemId = "mi-1",
            menuItemNameSnapshot = mapOf("en" to "Burger"), quantity = 1, unitPriceMinorUnit = 1000L, taxRateId = null,
        )
        orderRepo.items[item.id] = item
        val vm = buildViewModel()
        var newOrderId: String? = null

        vm.splitTable(sourceOrderId, "t2", listOf(item.id), onOrderCreated = { newOrderId = it })

        assertNotNull(newOrderId)
        assertEquals(TableStatus.OCCUPIED, tableRepo.tables["t2"]!!.status)
        assertEquals(newOrderId, tableRepo.tables["t2"]!!.currentOrderId)
        assertEquals(newOrderId, orderRepo.items[item.id]!!.orderId)
    }

    // ── Table lifecycle ──────────────────────────────────────────────────────

    @Test
    fun `clearing a dirty table returns it to AVAILABLE`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.DIRTY, currentOrderId = "order-old")
        val vm = buildViewModel()

        vm.clearTable("t1")

        assertEquals(TableStatus.AVAILABLE, tableRepo.tables["t1"]!!.status)
        assertNull(tableRepo.tables["t1"]!!.currentOrderId)
    }

    @Test
    fun `addTable creates a new AVAILABLE table`() = runTest {
        val vm = buildViewModel()

        vm.addTable("New Table", "patio", 6)

        val created = tableRepo.tables.values.firstOrNull { it.name == "New Table" }
        assertNotNull(created)
        assertEquals("patio", created!!.sectionId)
        assertEquals(6, created.capacity)
        assertEquals(TableStatus.AVAILABLE, created.status)
    }

    // ── Waitlist (in-memory) ─────────────────────────────────────────────────

    @Test
    fun `adding to the waitlist appends an entry, removing it clears it`() = runTest {
        val vm = buildViewModel()

        vm.addToWaitlist("Smith Party", 4)
        assertEquals(1, vm.waitlist.value.size)
        assertEquals("Smith Party", vm.waitlist.value.first().guestName)

        val entryId = vm.waitlist.value.first().id
        vm.removeFromWaitlist(entryId)
        assertTrue(vm.waitlist.value.isEmpty())
    }

    // ── Reservations ─────────────────────────────────────────────────────────

    @Test
    fun `creating a reservation on an available table marks it RESERVED`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1")
        val vm = buildViewModel()
        val future = System.currentTimeMillis() + 3_600_000L
        var error: String? = null

        vm.createReservation("t1", "Jane Doe", 2, future, onError = { error = it })

        assertNull(error)
        assertEquals(TableStatus.RESERVED, tableRepo.tables["t1"]!!.status)
        assertEquals(1, reservationRepo.reservations.size)
    }

    @Test
    fun `cancelling a confirmed reservation frees the table`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.RESERVED)
        val reservation = Reservation(id = "r1", tableId = "t1", guestName = "Jane", guestCount = 2, scheduledAt = 0L)
        reservationRepo.reservations[reservation.id] = reservation
        val vm = buildViewModel()
        var error: String? = null

        vm.cancelReservation("r1", onError = { error = it })

        assertNull(error)
        assertEquals(ReservationStatus.CANCELLED, reservationRepo.reservations["r1"]!!.status)
        assertEquals(TableStatus.AVAILABLE, tableRepo.tables["t1"]!!.status)
    }

    @Test
    fun `marking no-show only applies to CONFIRMED reservations`() = runTest {
        val reservation = Reservation(id = "r1", tableId = "t1", guestName = "Jane", guestCount = 2, scheduledAt = 0L, status = ReservationStatus.CONFIRMED)
        reservationRepo.reservations[reservation.id] = reservation
        val vm = buildViewModel()

        vm.markNoShow("r1")

        assertEquals(ReservationStatus.NO_SHOW, reservationRepo.reservations["r1"]!!.status)
    }

    @Test
    fun `seating a reservation creates an order and clears the table back to OCCUPIED`() = runTest {
        tableRepo.tables["t1"] = availableTable("t1").copy(status = TableStatus.RESERVED)
        val reservation = Reservation(id = "r1", tableId = "t1", guestName = "Jane", guestCount = 2, scheduledAt = 0L)
        reservationRepo.reservations[reservation.id] = reservation
        val vm = buildViewModel()
        var newOrderId: String? = null

        vm.seatReservation("r1", onOrderCreated = { newOrderId = it })

        assertNotNull(newOrderId)
        assertEquals(ReservationStatus.SEATED, reservationRepo.reservations["r1"]!!.status)
        assertEquals(TableStatus.OCCUPIED, tableRepo.tables["t1"]!!.status)
    }

    // ── Table selection / detail panel ──────────────────────────────────────

    @Test
    fun `selecting a table without a current order shows an empty detail panel`() = runTest {
        val table = availableTable("t1")
        tableRepo.tables["t1"] = table
        val vm = buildViewModel()

        vm.selectTable(table)

        assertEquals("t1", vm.detailState.value.selectedTableId)
        assertNull(vm.detailState.value.order)
    }

    @Test
    fun `selecting an occupied table loads its order and items into the detail panel`() = runTest {
        val orderId = "order-1"
        val table = availableTable("t1").copy(status = TableStatus.OCCUPIED, currentOrderId = orderId)
        tableRepo.tables["t1"] = table
        orderRepo.orders[orderId] = Order(id = orderId, tableId = "t1", sourceTerminalId = "pos-1", createdAt = 0L, updatedAt = 0L)
        val vm = buildViewModel()

        vm.selectTable(table)

        assertEquals(orderId, vm.detailState.value.order?.id)
    }
}
