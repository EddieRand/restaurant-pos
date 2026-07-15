package com.restaurantpos.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.restaurantpos.core.database.entity.OrderEntity
import com.restaurantpos.core.database.repository.RoomOrderRepository
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import com.restaurantpos.core.sync.InMemorySyncOutbox
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class RoomOrderRepositoryTest {

    private lateinit var db: PosDatabase
    private lateinit var outbox: InMemorySyncOutbox
    private lateinit var repo: RoomOrderRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PosDatabase::class.java,
        ).allowMainThreadQueries().build()
        outbox = InMemorySyncOutbox()
        repo = RoomOrderRepository(db.orderDao(), db.orderItemDao(), SyncWriter(outbox))
    }

    @After
    fun teardown() = db.close()

    @Test
    fun updateTotalsPersistsLocallyAndEnqueuesASyncWrite() = runTest {
        // Regression: updateTotals() used to only write to local Room, never enqueueing a
        // sync write — unlike save()/updateStatus()/setTip(). The server's `orders` row (and
        // anything reading it, like the live Customer Display total) never learned about cart
        // changes and stayed stuck at 0, even though the in-app cart total was always correct.
        // Found via real-device CDS regression testing.
        db.orderDao().upsert(
            OrderEntity(
                id = "order-1", type = OrderType.DINE_IN, tableId = null, guestCount = 1,
                sourceTerminalId = "terminal-1", subtotalMinorUnit = 0L, taxTotalMinorUnit = 0L,
                serviceChargeMinorUnit = 0L, discountMinorUnit = 0L, status = OrderStatus.DRAFT,
                createdAt = 1000L, updatedAt = 1000L,
            )
        )

        repo.updateTotals("order-1", subtotal = 1050L, taxTotal = 50L)

        val updated = db.orderDao().getById("order-1")
        assertEquals(1050L, updated?.subtotalMinorUnit)
        assertEquals(50L, updated?.taxTotalMinorUnit)

        val pending = outbox.getPending().filter { it.entityType == SyncEntityType.ORDER && it.entityId == "order-1" }
        assertTrue("expected updateTotals to enqueue an ORDER sync write", pending.isNotEmpty())
        assertTrue(pending.last().payload.contains("\"subtotalMinorUnit\":1050"))
        assertTrue(pending.last().payload.contains("\"taxTotalMinorUnit\":50"))
    }
}
