package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TransferTableTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var tableRepo: FakeTableRepository
    private lateinit var useCase: TransferTableUseCase

    private val now = 1_000_000L

    @Before
    fun setUp() {
        orderRepo = FakeOrderRepository()
        tableRepo = FakeTableRepository()
        useCase = TransferTableUseCase(orderRepo, tableRepo)
    }

    @Test
    fun `transfer sets original table AVAILABLE and target table OCCUPIED`() = runTest {
        val order = Order(
            id = "o1",
            sourceTerminalId = "t1",
            tableId = "table-A",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(order)

        tableRepo.save(Table(id = "table-A", name = "A1", sectionId = "s1", capacity = 4,
            status = TableStatus.ORDERED, currentOrderId = "o1"))
        tableRepo.save(Table(id = "table-B", name = "B1", sectionId = "s1", capacity = 4,
            status = TableStatus.AVAILABLE))

        val result = useCase(TransferTableUseCase.Params(
            orderId = "o1",
            targetTableId = "table-B",
            now = now,
        ))

        assertTrue(result is TransferTableUseCase.Result.Success)

        val tableA = tableRepo.getById("table-A")!!
        val tableB = tableRepo.getById("table-B")!!

        assertEquals(TableStatus.AVAILABLE, tableA.status)
        assertNull(tableA.currentOrderId)

        assertEquals(TableStatus.OCCUPIED, tableB.status)
        assertEquals("o1", tableB.currentOrderId)
    }

    @Test
    fun `transfer updates order tableId to target`() = runTest {
        val order = Order(
            id = "o2",
            sourceTerminalId = "t1",
            tableId = "table-C",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(order)

        tableRepo.save(Table(id = "table-C", name = "C1", sectionId = "s1", capacity = 2,
            status = TableStatus.ORDERED, currentOrderId = "o2"))
        tableRepo.save(Table(id = "table-D", name = "D1", sectionId = "s1", capacity = 2,
            status = TableStatus.AVAILABLE))

        useCase(TransferTableUseCase.Params(orderId = "o2", targetTableId = "table-D"))

        val updatedOrder = orderRepo.getById("o2")!!
        assertEquals("table-D", updatedOrder.tableId)
    }

    @Test
    fun `transfer to non-available table returns error`() = runTest {
        val order = Order(
            id = "o3",
            sourceTerminalId = "t1",
            tableId = "table-E",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(order)

        tableRepo.save(Table(id = "table-E", name = "E1", sectionId = "s1", capacity = 2,
            status = TableStatus.ORDERED, currentOrderId = "o3"))
        tableRepo.save(Table(id = "table-F", name = "F1", sectionId = "s1", capacity = 2,
            status = TableStatus.OCCUPIED, currentOrderId = "other-order"))

        val result = useCase(TransferTableUseCase.Params(orderId = "o3", targetTableId = "table-F"))

        assertTrue(result is TransferTableUseCase.Result.Error)
    }

    @Test
    fun `transfer of order not found returns error`() = runTest {
        tableRepo.save(Table(id = "table-G", name = "G1", sectionId = "s1", capacity = 2,
            status = TableStatus.AVAILABLE))

        val result = useCase(TransferTableUseCase.Params(orderId = "no-such-order", targetTableId = "table-G"))

        assertTrue(result is TransferTableUseCase.Result.Error)
    }
}
