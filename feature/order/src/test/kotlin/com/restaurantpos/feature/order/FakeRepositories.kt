package com.restaurantpos.feature.order

import com.restaurantpos.core.domain.repository.ComboRepository
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.hardware.KitchenTicketData
import com.restaurantpos.core.hardware.PrintResult
import com.restaurantpos.core.hardware.PrinterPort
import com.restaurantpos.core.hardware.ReceiptData
import com.restaurantpos.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory fakes used only in feature:order unit tests — not for production use. */

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
    override suspend fun applyRemote(tables: List<Table>) { tables.forEach { this.tables[it.id] = it } }
}

class FakeMenuItemRepository : MenuItemRepository {
    val itemsMap = mutableMapOf<String, MenuItem>()
    private val modifierGroups = mutableMapOf<String, List<ModifierGroup>>()
    override fun observeAll(): Flow<List<MenuItem>> = flowOf(itemsMap.values.toList())
    override fun observeAvailable(): Flow<List<MenuItem>> = flowOf(itemsMap.values.filter { !it.isSoldOut }.toList())
    override fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>> =
        observeAvailable()
    override suspend fun getById(id: String): MenuItem? = itemsMap[id]
    override suspend fun save(item: MenuItem) { itemsMap[item.id] = item }
    override suspend fun upsertAll(items: List<MenuItem>) { items.forEach { itemsMap[it.id] = it } }
    override suspend fun setSoldOut(id: String, soldOut: Boolean) {
        itemsMap[id]?.let { itemsMap[id] = it.copy(isSoldOut = soldOut) }
    }
    override suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean) {
        ids.forEach { id -> itemsMap[id]?.let { itemsMap[id] = it.copy(isSoldOut = soldOut) } }
    }
    override suspend fun getModifierGroups(menuItemId: String): List<ModifierGroup> =
        modifierGroups[menuItemId] ?: emptyList()
    override suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>) {
        modifierGroups[menuItemId] = groups
    }
}

class FakeComboRepository : ComboRepository {
    val combos = mutableMapOf<String, Combo>()
    override fun observeActive(): Flow<List<Combo>> = flowOf(combos.values.toList())
    override suspend fun getById(id: String): Combo? = combos[id]
    override suspend fun save(combo: Combo) { combos[combo.id] = combo }
}

class FakeKitchenTicketRepository : KitchenTicketRepository {
    val tickets = mutableMapOf<String, KitchenTicket>()
    override fun observeActive(): Flow<List<KitchenTicket>> =
        flowOf(tickets.values.filter { it.status != KitchenTicketStatus.DONE })
    override fun observeByStation(stationId: String): Flow<List<KitchenTicket>> =
        flowOf(tickets.values.filter { it.stationId == stationId })
    override suspend fun getById(id: String): KitchenTicket? = tickets[id]
    override suspend fun getByOrder(orderId: String): List<KitchenTicket> =
        tickets.values.filter { it.orderId == orderId }
    override suspend fun save(ticket: KitchenTicket) { tickets[ticket.id] = ticket }
    override suspend fun saveAll(tickets: List<KitchenTicket>) { tickets.forEach { this.tickets[it.id] = it } }
    override suspend fun updateStatus(id: String, status: KitchenTicketStatus, bumpedAt: Long?) {
        tickets[id]?.let { tickets[id] = it.copy(status = status) }
    }
    override suspend fun applyRemote(tickets: List<KitchenTicket>) { tickets.forEach { this.tickets[it.id] = it } }
}

class FakeRolePermissionRepository(
    private val allow: Boolean = true,
) : RolePermissionRepository {
    override suspend fun getAllRoles(): List<Role> = emptyList()
    override suspend fun getPermissionKeys(roleId: String): List<String> = emptyList()
    override suspend fun hasPermission(roleId: String, permissionKey: PermissionKey): Boolean = allow
    override suspend fun replacePermissions(roleId: String, permissionKeys: List<String>) {}
    override suspend fun getAllMappings(): List<RolePermission> = emptyList()
}

class FakeSessionRepository(user: User? = null) : SessionRepository {
    private val _user = MutableStateFlow(user)
    override val currentUser: Flow<User?> = _user
    override fun current(): User? = _user.value
    override fun login(user: User) { _user.value = user }
    override fun logout() { _user.value = null }
}

class FakeCustomerRepository : CustomerRepository {
    val customers = mutableMapOf<String, Customer>()
    override fun observeAll(): Flow<List<Customer>> = flowOf(customers.values.toList())
    override suspend fun search(query: String): List<Customer> =
        customers.values.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
    override suspend fun getById(id: String): Customer? = customers[id]
    override suspend fun getByPhone(phone: String): Customer? = customers.values.firstOrNull { it.phone == phone }
    override suspend fun save(customer: Customer) { customers[customer.id] = customer }
    override suspend fun addPoints(customerId: String, points: Long, orderId: String?, description: String) {}
    override suspend fun getTransactions(customerId: String): List<LoyaltyTransaction> = emptyList()
    override suspend fun applyRemote(customers: List<Customer>) { customers.forEach { this.customers[it.id] = it } }
}

/** No Android dependency (unlike MockPrinter, which logs via android.util.Log). */
class FakePrinterPort : PrinterPort {
    val printedReceipts = mutableListOf<ReceiptData>()
    val printedTickets = mutableListOf<KitchenTicketData>()
    var connected = true
    override suspend fun printReceipt(data: ReceiptData): PrintResult {
        printedReceipts.add(data)
        return PrintResult.Success
    }
    override suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult {
        printedTickets.add(data)
        return PrintResult.Success
    }
    override suspend fun isConnected(): Boolean = connected
}
