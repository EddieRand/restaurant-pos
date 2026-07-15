package com.restaurantpos.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.restaurantpos.core.database.entity.OrderEntity
import com.restaurantpos.core.database.entity.OrderItemEntity
import com.restaurantpos.core.database.entity.TableEntity
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class OrderDaoTest {

    private lateinit var db: PosDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PosDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    private fun makeOrder(id: String, status: OrderStatus = OrderStatus.DRAFT, tableId: String? = null) =
        OrderEntity(
            id = id, type = OrderType.DINE_IN, tableId = tableId, guestCount = 2,
            sourceTerminalId = "terminal-1", subtotalMinorUnit = 0L, taxTotalMinorUnit = 0L,
            serviceChargeMinorUnit = 0L, discountMinorUnit = 0L, status = status,
            createdAt = 1000L, updatedAt = 1000L,
        )

    @Test
    fun upsertAndGetByIdRoundTrip() = runTest {
        val order = makeOrder("order-1")
        db.orderDao().upsert(order)
        val fetched = db.orderDao().getById("order-1")
        assertEquals(order, fetched)
    }

    @Test
    fun updateStatusChangesStatus() = runTest {
        db.orderDao().upsert(makeOrder("order-2"))
        db.orderDao().updateStatus("order-2", OrderStatus.PLACED, updatedAt = 2000L)
        val updated = db.orderDao().getById("order-2")
        assertEquals(OrderStatus.PLACED, updated?.status)
        assertEquals(2000L, updated?.updatedAt)
    }

    @Test
    fun getActiveByTableReturnsOnlyNonClosedOrder() = runTest {
        db.orderDao().upsert(makeOrder("closed", OrderStatus.CLOSED, tableId = "t-1"))
        db.orderDao().upsert(makeOrder("active", OrderStatus.PLACED, tableId = "t-1"))
        val active = db.orderDao().getActiveByTable("t-1")
        assertEquals("active", active?.id)
    }

    @Test
    fun countPickupCodesSinceCountsOnlyCodesAfterCutoff() = runTest {
        db.orderDao().upsert(makeOrder("old").copy(pickupCode = "5", createdAt = 500L))
        db.orderDao().upsert(makeOrder("today1").copy(pickupCode = "1", createdAt = 1500L))
        db.orderDao().upsert(makeOrder("today2").copy(pickupCode = "2", createdAt = 1600L))
        db.orderDao().upsert(makeOrder("no-code").copy(createdAt = 1700L))

        assertEquals(2, db.orderDao().countPickupCodesSince(1000L))
        assertEquals(0, db.orderDao().countPickupCodesSince(2000L))
    }

    @Test
    fun setPickupCodeWritesCodeAndBumpsUpdatedAt() = runTest {
        db.orderDao().upsert(makeOrder("o-pc"))
        db.orderDao().setPickupCode("o-pc", "42", updatedAt = 3000L)
        val updated = db.orderDao().getById("o-pc")
        assertEquals("42", updated?.pickupCode)
        assertEquals(3000L, updated?.updatedAt)
    }

    @Test
    fun observeReadyForPickupReturnsOnlyReadySortedByUpdatedAt() = runTest {
        db.orderDao().upsert(makeOrder("not-ready"))
        db.orderDao().upsert(
            makeOrder("ready-late").copy(fulfillmentStatus = OrderFulfillmentStatus.READY_FOR_PICKUP, updatedAt = 5000L))
        db.orderDao().upsert(
            makeOrder("ready-early").copy(fulfillmentStatus = OrderFulfillmentStatus.READY_FOR_PICKUP, updatedAt = 4000L))
        db.orderDao().upsert(
            makeOrder("picked-up").copy(fulfillmentStatus = OrderFulfillmentStatus.PICKED_UP))

        val ready = db.orderDao().observeReadyForPickup().first()
        assertEquals(listOf("ready-early", "ready-late"), ready.map { it.id })
    }

    @Test
    fun observeActiveExcludesClosedAndVoided() = runTest {
        db.orderDao().upsert(makeOrder("draft", OrderStatus.DRAFT))
        db.orderDao().upsert(makeOrder("closed", OrderStatus.CLOSED))
        db.orderDao().upsert(makeOrder("voided", OrderStatus.VOIDED))
        val active = db.orderDao().observeActive().first()
        assertEquals(1, active.size)
        assertEquals("draft", active[0].id)
    }
}

@RunWith(AndroidJUnit4::class)
@SmallTest
class TableDaoTest {

    private lateinit var db: PosDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PosDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    private fun makeTable(id: String, status: TableStatus = TableStatus.AVAILABLE) =
        TableEntity(id = id, name = "T$id", sectionId = "main", capacity = 4, currentOrderId = null, status = status)

    @Test
    fun upsertAndObserveAll() = runTest {
        db.tableDao().upsertAll(listOf(makeTable("1"), makeTable("2")))
        val tables = db.tableDao().observeAll().first()
        assertEquals(2, tables.size)
    }

    @Test
    fun updateStatusAndOrderLinksOrderToTable() = runTest {
        db.tableDao().upsert(makeTable("t-1"))
        db.tableDao().updateStatusAndOrder("t-1", TableStatus.OCCUPIED, "order-99", 1000L)
        val table = db.tableDao().getById("t-1")
        assertEquals(TableStatus.OCCUPIED, table?.status)
        assertEquals("order-99", table?.currentOrderId)
        assertEquals(1000L, table?.updatedAt)
    }

    @Test
    fun clearingTableRemovesOrderId() = runTest {
        db.tableDao().upsert(makeTable("t-2"))
        db.tableDao().updateStatusAndOrder("t-2", TableStatus.OCCUPIED, "order-1", 1000L)
        db.tableDao().updateStatusAndOrder("t-2", TableStatus.AVAILABLE, null, 2000L)
        val table = db.tableDao().getById("t-2")
        assertEquals(TableStatus.AVAILABLE, table?.status)
        assertNull(table?.currentOrderId)
    }
}
