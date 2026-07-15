package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SplitOrderTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var useCase: SplitOrderUseCase

    private val now = 1_000_000L

    private fun item(id: String, orderId: String, price: Long, qty: Int) = OrderItem(
        id = id,
        orderId = orderId,
        menuItemId = "menu-$id",
        menuItemNameSnapshot = mapOf("en" to id),
        quantity = qty,
        unitPriceMinorUnit = price,
        taxRateId = null,
        status = OrderItemStatus.PLACED,
    )

    @Before
    fun setUp() {
        orderRepo = FakeOrderRepository()
        useCase = SplitOrderUseCase(orderRepo)
    }

    @Test
    fun `split conserves total amount across both orders`() = runTest {
        val sourceOrder = Order(
            id = "order-1",
            sourceTerminalId = "t1",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
            subtotalMinorUnit = 3700L,
        )
        orderRepo.save(sourceOrder)
        orderRepo.saveItems(listOf(
            item("i1", "order-1", 1200L, 2),  // 2400
            item("i2", "order-1", 500L, 1),   // 500
            item("i3", "order-1", 800L, 1),   // 800
        ))

        val result = useCase(SplitOrderUseCase.Params(
            sourceOrderId = "order-1",
            itemIdsToSplit = setOf("i3"),
            newOrderId = "order-2",
            now = now,
            terminalId = "t1",
        ))

        assertTrue(result is SplitOrderUseCase.Result.Success)
        val success = result as SplitOrderUseCase.Result.Success

        // Amount conservation: 2400 + 500 + 800 = 3700
        val totalAfterSplit = success.originalOrder.subtotalMinorUnit + success.newOrder.subtotalMinorUnit
        assertEquals(3700L, totalAfterSplit)

        // New order has only the split item
        assertEquals(800L, success.newOrder.subtotalMinorUnit)
        assertEquals("order-1", success.newOrder.splitFromOrderId)

        // Original order retains remaining items
        assertEquals(2900L, success.originalOrder.subtotalMinorUnit)
    }

    @Test
    fun `split assigns items to new order in repository`() = runTest {
        val sourceOrder = Order(
            id = "order-A",
            sourceTerminalId = "t1",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
            subtotalMinorUnit = 2000L,
        )
        orderRepo.save(sourceOrder)
        orderRepo.saveItems(listOf(
            item("ia", "order-A", 1200L, 1),
            item("ib", "order-A", 800L, 1),
        ))

        useCase(SplitOrderUseCase.Params(
            sourceOrderId = "order-A",
            itemIdsToSplit = setOf("ib"),
            newOrderId = "order-B",
            now = now,
        ))

        val itemsInNew = orderRepo.getItemsByOrder("order-B")
        val itemsInOld = orderRepo.getItemsByOrder("order-A")

        assertEquals(1, itemsInNew.size)
        assertEquals("ib", itemsInNew.first().id)
        assertEquals(1, itemsInOld.size)
        assertEquals("ia", itemsInOld.first().id)
    }

    @Test
    fun `split of all items returns error`() = runTest {
        val sourceOrder = Order(
            id = "order-X",
            sourceTerminalId = "t1",
            status = OrderStatus.PLACED,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(sourceOrder)
        orderRepo.saveItems(listOf(item("ix", "order-X", 500L, 1)))

        val result = useCase(SplitOrderUseCase.Params(
            sourceOrderId = "order-X",
            itemIdsToSplit = setOf("ix"),
            newOrderId = "order-Y",
        ))

        assertTrue(result is SplitOrderUseCase.Result.Error)
    }

    @Test
    fun `split on DRAFT order returns error`() = runTest {
        val sourceOrder = Order(
            id = "order-D",
            sourceTerminalId = "t1",
            status = OrderStatus.DRAFT,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(sourceOrder)
        orderRepo.saveItems(listOf(
            item("id1", "order-D", 500L, 1),
            item("id2", "order-D", 600L, 1),
        ))

        val result = useCase(SplitOrderUseCase.Params(
            sourceOrderId = "order-D",
            itemIdsToSplit = setOf("id1"),
            newOrderId = "order-E",
        ))

        assertTrue(result is SplitOrderUseCase.Result.Error)
    }
}
