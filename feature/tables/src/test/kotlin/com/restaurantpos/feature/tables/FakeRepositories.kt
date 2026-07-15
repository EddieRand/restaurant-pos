package com.restaurantpos.feature.tables

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory fakes used only in feature:tables unit tests — not for production use. */

class FakeTableRepository : TableRepository {
    val tables = mutableMapOf<String, Table>()
    override fun observeAll(): Flow<List<Table>> = flowOf(tables.values.toList())
    override suspend fun getById(id: String): Table? = tables[id]
    override suspend fun save(table: Table) { tables[table.id] = table }
    override suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?) {
        tables[id]?.let { tables[id] = it.copy(status = status, currentOrderId = orderId) }
    }
    override suspend fun applyRemote(tables: List<Table>) { tables.forEach { this.tables[it.id] = it } }
}

class FakeOrderRepository : OrderRepository {
    val orders = mutableMapOf<String, Order>()
    val items = mutableMapOf<String, OrderItem>()
    override fun observeActive(): Flow<List<Order>> = flowOf(orders.values.toList())
    override suspend fun getById(id: String): Order? = orders[id]
    override suspend fun getActiveByTable(tableId: String): Order? =
        orders.values.firstOrNull { it.tableId == tableId }
    override suspend fun save(order: Order) { orders[order.id] = order }
    override suspend fun saveItems(items: List<OrderItem>) { items.forEach { this.items[it.id] = it } }
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
    override suspend fun applyRemote(newOrders: List<Order>, newItems: List<OrderItem>) {
        newOrders.forEach { remote ->
            val local = orders[remote.id]
            if (local == null || remote.updatedAt >= local.updatedAt) orders[remote.id] = remote
        }
        newItems.forEach { items[it.id] = it }
    }
    override suspend fun countPickupCodesSince(sinceEpoch: Long): Int =
        orders.values.count { it.createdAt >= sinceEpoch && it.pickupCode != null }
    override suspend fun setPickupCode(id: String, pickupCode: String) {
        orders[id]?.let { orders[id] = it.copy(pickupCode = pickupCode) }
    }
    override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> =
        orders.values.filter { it.updatedAt in fromEpoch..toEpoch }
    override suspend fun searchOrders(
        query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?,
    ): List<Order> = orders.values
        .filter { status == null || it.status == status }
        .filter { fromEpoch == null || it.createdAt >= fromEpoch }
        .filter { toEpoch == null || it.createdAt <= toEpoch }
        .filter { query.isBlank() || it.tableId?.contains(query, ignoreCase = true) == true }
        .sortedByDescending { it.createdAt }
}

class FakeReservationRepository : ReservationRepository {
    val reservations = mutableMapOf<String, Reservation>()
    override fun observeByDate(dateEpochMillis: Long): Flow<List<Reservation>> =
        flowOf(reservations.values.toList())
    override suspend fun getById(id: String): Reservation? = reservations[id]
    override suspend fun save(reservation: Reservation) { reservations[reservation.id] = reservation }
    override suspend fun updateStatus(id: String, status: ReservationStatus) {
        reservations[id]?.let { reservations[id] = it.copy(status = status) }
    }
}

class FakeSessionRepository(user: User? = null) : SessionRepository {
    private val _user = MutableStateFlow(user)
    override val currentUser: Flow<User?> = _user
    override fun current(): User? = _user.value
    override fun login(user: User) { _user.value = user }
    override fun logout() { _user.value = null }
}
