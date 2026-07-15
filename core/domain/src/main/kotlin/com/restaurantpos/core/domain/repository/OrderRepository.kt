package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeActive(): Flow<List<Order>>
    suspend fun getById(id: String): Order?
    suspend fun getActiveByTable(tableId: String): Order?
    suspend fun save(order: Order)
    suspend fun saveItems(items: List<OrderItem>)
    suspend fun getItemsByOrder(orderId: String): List<OrderItem>
    suspend fun updateStatus(id: String, status: OrderStatus)
    suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long)
    suspend fun setTip(id: String, tipMinorUnit: Long)
    suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order>
    suspend fun searchOrders(query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?): List<Order>
    fun observeReadyForPickup(): Flow<List<Order>>
    suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus)
    /** Returns how many pickup codes have been allocated to orders created since [sinceEpoch]. */
    suspend fun countPickupCodesSince(sinceEpoch: Long): Int
    /**
     * Applies orders/items pulled from the server (last-write-wins by updatedAt).
     * Must NOT re-enqueue to the sync outbox — these changes originated remotely.
     */
    suspend fun applyRemote(orders: List<Order>, items: List<OrderItem>)
    suspend fun setPickupCode(id: String, pickupCode: String)
}
