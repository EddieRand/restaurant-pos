package com.restaurantpos.feature.tables

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PickupDisplayViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOrderRepo : OrderRepository {
        val ordersFlow = MutableStateFlow<List<Order>>(emptyList())
        override fun observeActive(): Flow<List<Order>> = ordersFlow
        override suspend fun getById(id: String): Order? = null
        override suspend fun getActiveByTable(tableId: String): Order? = null
        override suspend fun save(order: Order) {}
        override suspend fun saveItems(items: List<OrderItem>) {}
        override suspend fun getItemsByOrder(orderId: String): List<OrderItem> = emptyList()
        override suspend fun updateStatus(id: String, status: OrderStatus) {}
        override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {}
        override suspend fun setTip(id: String, tipMinorUnit: Long) {}
        override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> = emptyList()
        override suspend fun searchOrders(query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?): List<Order> = emptyList()
        override fun observeReadyForPickup(): Flow<List<Order>> =
            ordersFlow.map { list -> list.filter { it.fulfillmentStatus == OrderFulfillmentStatus.READY_FOR_PICKUP } }
        override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {}
        override suspend fun countPickupCodesSince(sinceEpoch: Long): Int = 0
        override suspend fun setPickupCode(id: String, pickupCode: String) {}
        override suspend fun applyRemote(orders: List<Order>, items: List<OrderItem>) {}
    }

    private fun order(id: String, fulfillment: OrderFulfillmentStatus, updatedAt: Long, pickupCode: String? = null) =
        Order(
            id = id,
            sourceTerminalId = "kiosk-1",
            fulfillmentStatus = fulfillment,
            pickupCode = pickupCode,
            createdAt = 0L,
            updatedAt = updatedAt,
        )

    @Test
    fun `shows only READY_FOR_PICKUP orders sorted by updatedAt regardless of source`() = runTest {
        val repo = FakeOrderRepo()
        repo.ordersFlow.value = listOf(
            order("late-ready", OrderFulfillmentStatus.READY_FOR_PICKUP, updatedAt = 200L, pickupCode = "9"),
            order("not-ready", OrderFulfillmentStatus.NOT_READY, updatedAt = 50L),
            order("early-ready", OrderFulfillmentStatus.READY_FOR_PICKUP, updatedAt = 100L, pickupCode = "8"),
            order("picked-up", OrderFulfillmentStatus.PICKED_UP, updatedAt = 10L),
        )
        val vm = PickupDisplayViewModel(repo)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(listOf("early-ready", "late-ready"), state.readyOrders.map { it.id })
        assertEquals(listOf("8", "9"), state.readyOrders.map { it.pickupCode })
    }

    @Test
    fun `order disappears from the board once picked up`() = runTest {
        val repo = FakeOrderRepo()
        repo.ordersFlow.value = listOf(order("o1", OrderFulfillmentStatus.READY_FOR_PICKUP, 100L, "3"))
        val vm = PickupDisplayViewModel(repo)
        assertEquals(1, vm.uiState.first { !it.isLoading }.readyOrders.size)

        repo.ordersFlow.value = listOf(order("o1", OrderFulfillmentStatus.PICKED_UP, 200L, "3"))

        assertEquals(0, vm.uiState.first { it.readyOrders.isEmpty() }.readyOrders.size)
    }
}
