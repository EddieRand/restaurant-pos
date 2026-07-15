package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.OrderDao
import com.restaurantpos.core.database.dao.OrderItemDao
import com.restaurantpos.core.database.entity.OrderEntity
import com.restaurantpos.core.database.entity.OrderItemEntity
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomOrderRepository(
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val syncWriter: SyncWriter,
) : OrderRepository {

    override fun observeActive(): Flow<List<Order>> =
        orderDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Order? =
        orderDao.getById(id)?.toDomain()

    override suspend fun getActiveByTable(tableId: String): Order? =
        orderDao.getActiveByTable(tableId)?.toDomain()

    override suspend fun save(order: Order) {
        orderDao.upsert(OrderEntity.fromDomain(order))
        val payload = JSONObject().apply {
            put("id", order.id)
            put("type", order.type.name)
            put("tableId", order.tableId)
            put("sourceTerminalId", order.sourceTerminalId)
            put("guestCount", order.guestCount)
            put("status", order.status.name)
            put("subtotalMinorUnit", order.subtotalMinorUnit)
            put("taxTotalMinorUnit", order.taxTotalMinorUnit)
            put("serviceChargeMinorUnit", order.serviceChargeMinorUnit)
            put("tipMinorUnit", order.tipMinorUnit)
            put("discountMinorUnit", order.discountMinorUnit)
            put("orderNotes", order.orderNotes)
            put("createdAt", order.createdAt)
            put("updatedAt", order.updatedAt)
            put("pickupCode", order.pickupCode)
            put("fulfillmentStatus", order.fulfillmentStatus.name)
        }.toString()
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = order.id,
            operation = SyncOperation.UPDATE,
            payload = payload,
        )
    }

    override suspend fun saveItems(items: List<OrderItem>) {
        orderItemDao.upsertAll(items.map { OrderItemEntity.fromDomain(it) })
        items.forEach { item ->
            // Field names mirror SyncPushProcessor.processOrderItem — keep both sides in sync
            val payload = JSONObject().apply {
                put("id", item.id)
                put("orderId", item.orderId)
                put("menuItemId", item.menuItemId)
                put("menuItemNameSnapshot", JSONObject(item.menuItemNameSnapshot).toString())
                put("quantity", item.quantity)
                put("unitPriceMinorUnit", item.unitPriceMinorUnit)
                put("taxRateId", item.taxRateId)
                put("course", item.course)
                put("status", item.status.name)
                put("notes", item.notes)
                put("categoryId", item.categoryId)
                put("allergenSnapshot", item.allergenSnapshot.joinToString("|") { it.name })
                put("comboId", item.comboId)
            }.toString()
            syncWriter.enqueue(
                entityType = SyncEntityType.ORDER_ITEM,
                entityId = item.id,
                operation = SyncOperation.UPDATE,
                payload = payload,
            )
        }
    }

    override suspend fun getItemsByOrder(orderId: String): List<OrderItem> =
        orderItemDao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun updateStatus(id: String, status: OrderStatus) {
        val now = System.currentTimeMillis()
        orderDao.updateStatus(id, status, updatedAt = now)
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","status":"$status","updatedAt":$now}""",
        )
    }

    override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {
        // Regression: this previously only wrote to local Room and never enqueued a sync
        // write — unlike save()/updateStatus()/setTip(). The in-app cart total was always
        // correct (Room is authoritative locally), but the server's `orders` row — and
        // anything reading from it, like the Customer Display's live total — never learned
        // about cart changes and stayed stuck at 0. Found via real-device CDS regression
        // testing.
        val now = System.currentTimeMillis()
        orderDao.updateTotals(id, subtotal, taxTotal, updatedAt = now)
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","subtotalMinorUnit":$subtotal,"taxTotalMinorUnit":$taxTotal,"updatedAt":$now}""",
        )
    }

    override suspend fun setTip(id: String, tipMinorUnit: Long) {
        val now = System.currentTimeMillis()
        orderDao.setTip(id, tipMinorUnit, updatedAt = now)
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","tipMinorUnit":$tipMinorUnit,"updatedAt":$now}""",
        )
    }

    override fun observeReadyForPickup(): Flow<List<Order>> =
        orderDao.observeReadyForPickup().map { list -> list.map { it.toDomain() } }

    override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {
        val now = System.currentTimeMillis()
        orderDao.updateFulfillmentStatus(id, status, updatedAt = now)
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","fulfillmentStatus":"${status.name}","updatedAt":$now}""",
        )
    }

    override suspend fun countPickupCodesSince(sinceEpoch: Long): Int =
        orderDao.countPickupCodesSince(sinceEpoch)

    override suspend fun applyRemote(orders: List<Order>, items: List<OrderItem>) {
        val ordersToApply = orders.filter { remote ->
            val local = orderDao.getById(remote.id)
            local == null || remote.updatedAt >= local.updatedAt
        }
        ordersToApply.forEach { orderDao.upsert(OrderEntity.fromDomain(it)) }
        // Items ride along with their order: apply only those whose order was accepted.
        val acceptedOrderIds = ordersToApply.map { it.id }.toSet()
        val itemsToApply = items.filter { it.orderId in acceptedOrderIds }
        if (itemsToApply.isNotEmpty()) {
            orderItemDao.upsertAll(itemsToApply.map(OrderItemEntity::fromDomain))
        }
    }

    override suspend fun setPickupCode(id: String, pickupCode: String) {
        val now = System.currentTimeMillis()
        orderDao.setPickupCode(id, pickupCode, updatedAt = now)
        syncWriter.enqueue(
            entityType = SyncEntityType.ORDER,
            entityId = id,
            operation = SyncOperation.UPDATE,
            payload = """{"id":"$id","pickupCode":"$pickupCode","updatedAt":$now}""",
        )
    }

    override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> =
        orderDao.getClosedInRange(fromEpoch, toEpoch).map { it.toDomain() }

    override suspend fun searchOrders(
        query: String,
        fromEpoch: Long?,
        toEpoch: Long?,
        status: OrderStatus?,
    ): List<Order> {
        val tableId = query.trim().ifBlank { null }
        return orderDao.searchOrders(
            status = status?.name,
            tableId = tableId,
            fromEpoch = fromEpoch,
            toEpoch = toEpoch,
        ).map { it.toDomain() }
    }
}
