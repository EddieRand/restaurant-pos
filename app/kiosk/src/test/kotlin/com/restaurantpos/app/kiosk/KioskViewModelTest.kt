package com.restaurantpos.app.kiosk

import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class KioskViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOrderRepo : OrderRepository {
        val store = mutableMapOf<String, Order>()
        override fun observeActive(): Flow<List<Order>> = flowOf(store.values.toList())
        override suspend fun getById(id: String) = store[id]
        override suspend fun getActiveByTable(tableId: String): Order? = null
        override suspend fun save(order: Order) { store[order.id] = order }
        override suspend fun saveItems(items: List<OrderItem>) {}
        override suspend fun getItemsByOrder(orderId: String): List<OrderItem> = emptyList()
        override suspend fun updateStatus(id: String, status: OrderStatus) {}
        override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {}
        override suspend fun setTip(id: String, tipMinorUnit: Long) {}
        override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> = emptyList()
        override suspend fun searchOrders(query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?): List<Order> = emptyList()
        override fun observeReadyForPickup(): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {}
        override suspend fun countPickupCodesSince(sinceEpoch: Long): Int = 0
        override suspend fun setPickupCode(id: String, pickupCode: String) {
            store[id]?.let { store[id] = it.copy(pickupCode = pickupCode) }
        }
        override suspend fun applyRemote(orders: List<Order>, items: List<OrderItem>) {}
    }

    @Test
    fun `init creates a fresh TAKEAWAY draft order without a table`() = runTest {
        val repo = FakeOrderRepo()
        val vm = KioskViewModel(repo, InMemoryConfigRepository())

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.orderId)
        val order = repo.store[state.orderId]!!
        assertEquals(OrderType.TAKEAWAY, order.type)
        assertNull(order.tableId)
        assertEquals(OrderStatus.DRAFT, order.status)
    }

    @Test
    fun `onOrderPlaced exposes the pickup code assigned by PlaceOrderUseCase`() = runTest {
        val repo = FakeOrderRepo()
        val vm = KioskViewModel(repo, InMemoryConfigRepository())
        val orderId = vm.uiState.value.orderId!!
        // PlaceOrderUseCase 在下单时已写入取餐号，这里模拟其结果
        repo.store[orderId] = repo.store[orderId]!!.copy(pickupCode = "8")

        vm.onOrderPlaced(orderId)

        assertEquals(orderId, vm.uiState.value.confirmedOrderId)
        assertEquals("8", vm.uiState.value.pickupCode)
        assertNotNull(vm.uiState.value.autoReturnCountdown)
    }

    @Test
    fun `startNewOrder resets to a fresh draft`() = runTest {
        val repo = FakeOrderRepo()
        val vm = KioskViewModel(repo, InMemoryConfigRepository())
        val firstId = vm.uiState.value.orderId!!
        vm.onOrderPlaced(firstId)

        vm.startNewOrder()

        val state = vm.uiState.value
        assertNull(state.confirmedOrderId)
        assertNull(state.pickupCode)
        assertNotNull(state.orderId)
        assertEquals(2, repo.store.size)
    }
}
