package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory fake used only in unit tests — not for production use. */
class FakeOrderRepository : OrderRepository {
    val orders = mutableMapOf<String, Order>()
    val items = mutableMapOf<String, OrderItem>()  // itemId -> item

    override fun observeActive(): Flow<List<Order>> = flowOf(orders.values.toList())
    override suspend fun getById(id: String): Order? = orders[id]
    override suspend fun getActiveByTable(tableId: String): Order? =
        orders.values.firstOrNull { it.tableId == tableId }
    override suspend fun save(order: Order) { orders[order.id] = order }
    override suspend fun saveItems(items: List<OrderItem>) {
        items.forEach { this.items[it.id] = it }
    }
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

    override fun observeReadyForPickup(): Flow<List<Order>> =
        flowOf(orders.values.filter { it.fulfillmentStatus == OrderFulfillmentStatus.READY_FOR_PICKUP })

    override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {
        orders[id]?.let { orders[id] = it.copy(fulfillmentStatus = status) }
    }

    var pickupCodesAllocated: Int? = null
    override suspend fun applyRemote(newOrders: List<Order>, newItems: List<OrderItem>) {
        newOrders.forEach { remote ->
            val local = orders[remote.id]
            if (local == null || remote.updatedAt >= local.updatedAt) orders[remote.id] = remote
        }
        newItems.forEach { items[it.id] = it }
    }
    override suspend fun countPickupCodesSince(sinceEpoch: Long): Int =
        pickupCodesAllocated ?: orders.values.count { it.createdAt >= sinceEpoch && it.pickupCode != null }

    override suspend fun setPickupCode(id: String, pickupCode: String) {
        orders[id]?.let { orders[id] = it.copy(pickupCode = pickupCode) }
    }

    var closedRange: List<Order> = emptyList()
    override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> =
        closedRange.filter { it.updatedAt in fromEpoch..toEpoch }

    override suspend fun searchOrders(
        query: String,
        fromEpoch: Long?,
        toEpoch: Long?,
        status: OrderStatus?,
    ): List<Order> = orders.values
        .filter { status == null || it.status == status }
        .filter { fromEpoch == null || it.createdAt >= fromEpoch }
        .filter { toEpoch == null || it.createdAt <= toEpoch }
        .filter { query.isBlank() || it.tableId?.contains(query, ignoreCase = true) == true }
        .sortedByDescending { it.createdAt }
}

class FakePaymentRepository : PaymentRepository {
    val payments = mutableMapOf<String, Payment>()

    override fun observeByOrder(orderId: String): Flow<List<Payment>> =
        flowOf(payments.values.filter { it.orderId == orderId })
    override suspend fun getByOrder(orderId: String): List<Payment> =
        payments.values.filter { it.orderId == orderId }
    override suspend fun getById(id: String): Payment? = payments[id]
    override suspend fun save(payment: Payment) { payments[payment.id] = payment }
    override suspend fun updateStatus(id: String, status: PaymentStatus) {
        payments[id]?.let { payments[id] = it.copy(status = status) }
    }
}

class FakeTableRepository : TableRepository {
    val tables = mutableMapOf<String, Table>()

    override fun observeAll(): Flow<List<Table>> = flowOf(tables.values.toList())
    override suspend fun getById(id: String): Table? = tables[id]
    override suspend fun save(table: Table) { tables[table.id] = table }
    override suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?) {
        tables[id]?.let { tables[id] = it.copy(status = status, currentOrderId = orderId) }
    }
    override suspend fun applyRemote(tables: List<Table>) {
        tables.forEach { this.tables[it.id] = it }
    }
}
