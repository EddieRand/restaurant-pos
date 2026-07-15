package com.restaurantpos.core.database.sync

import com.restaurantpos.core.database.dao.MenuItemDao
import com.restaurantpos.core.database.dao.OrderDao
import com.restaurantpos.core.database.dao.OrderItemDao
import com.restaurantpos.core.database.dao.PaymentDao
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderItemStatus
import com.restaurantpos.core.model.PaymentStatus
import com.restaurantpos.core.sync.ConflictResolver
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncRecord
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the server's authoritative payload to the local Room database.
 *
 * Strategy: parse the minimal JSON the server stored (same format the client pushed),
 * then apply targeted DAO updates. Unknown fields are ignored — only fields present
 * in the payload are written, preserving any local-only state not included in the sync.
 *
 * Entity coverage:
 *  - ORDER      → status, tip, discount, service charge, updatedAt
 *  - ORDER_ITEM → status, updatedAt
 *  - MENU_ITEM  → isSoldOut, price, updatedAt
 *  - PAYMENT    → status
 *  - Others     → no-op (TABLE, RESERVATION handled by their own sync flows)
 */
@Singleton
class RoomConflictResolver @Inject constructor(
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val menuItemDao: MenuItemDao,
    private val paymentDao: PaymentDao,
) : ConflictResolver {

    override suspend fun resolve(local: SyncRecord, serverPayload: String) {
        if (serverPayload.isBlank()) return
        runCatching {
            val json = JSONObject(serverPayload)
            when (local.entityType) {
                SyncEntityType.ORDER      -> resolveOrder(json)
                SyncEntityType.ORDER_ITEM -> resolveOrderItem(json)
                SyncEntityType.MENU_ITEM  -> resolveMenuItem(json)
                SyncEntityType.PAYMENT    -> resolvePayment(json)
                else                      -> Unit
            }
        }
        // Errors are swallowed: a resolver failure must never crash the sync engine.
        // The outbox record will be marked DONE regardless (server is authoritative).
    }

    private suspend fun resolveOrder(json: JSONObject) {
        val id = json.optString("id").ifBlank { return }
        val now = json.optLong("updatedAt", System.currentTimeMillis())

        val existing = orderDao.getById(id) ?: return

        val statusStr = json.optString("status")
        val updatedStatus: OrderStatus? = statusStr.takeIf { it.isNotBlank() }
            ?.runCatching { OrderStatus.valueOf(this) }?.getOrNull()

        // Build the merged entity: server-provided fields override local
        val merged = existing.copy(
            status          = updatedStatus ?: existing.status,
            tipMinorUnit    = if (json.has("tipMinorUnit")) json.getLong("tipMinorUnit") else existing.tipMinorUnit,
            discountMinorUnit = if (json.has("discountMinorUnit")) json.getLong("discountMinorUnit") else existing.discountMinorUnit,
            serviceChargeMinorUnit = if (json.has("serviceChargeMinorUnit")) json.getLong("serviceChargeMinorUnit") else existing.serviceChargeMinorUnit,
            subtotalMinorUnit = if (json.has("subtotalMinorUnit")) json.getLong("subtotalMinorUnit") else existing.subtotalMinorUnit,
            taxTotalMinorUnit = if (json.has("taxTotalMinorUnit")) json.getLong("taxTotalMinorUnit") else existing.taxTotalMinorUnit,
            pickupCode      = if (json.has("pickupCode")) json.optString("pickupCode").ifBlank { null } else existing.pickupCode,
            fulfillmentStatus = json.optString("fulfillmentStatus").takeIf { it.isNotBlank() }
                ?.runCatching { OrderFulfillmentStatus.valueOf(this) }?.getOrNull() ?: existing.fulfillmentStatus,
            updatedAt       = now,
        )
        orderDao.upsert(merged)
    }

    private suspend fun resolveOrderItem(json: JSONObject) {
        val id = json.optString("id").ifBlank { return }
        val existing = orderItemDao.getById(id) ?: return

        val statusStr = json.optString("status")
        val updatedStatus: OrderItemStatus? = statusStr.takeIf { it.isNotBlank() }
            ?.runCatching { OrderItemStatus.valueOf(this) }?.getOrNull()

        orderItemDao.upsert(existing.copy(
            status = updatedStatus ?: existing.status,
        ))
    }

    private suspend fun resolveMenuItem(json: JSONObject) {
        val id = json.optString("id").ifBlank { return }
        val existing = menuItemDao.getById(id) ?: return

        menuItemDao.upsert(existing.copy(
            isSoldOut = if (json.has("isSoldOut")) json.getBoolean("isSoldOut") else existing.isSoldOut,
            priceMinorUnit = if (json.has("priceMinorUnit")) json.getLong("priceMinorUnit") else existing.priceMinorUnit,
        ))
    }

    private suspend fun resolvePayment(json: JSONObject) {
        val id = json.optString("id").ifBlank { return }
        val existing = paymentDao.getById(id) ?: return

        val statusStr = json.optString("status")
        val updatedStatus: PaymentStatus? = statusStr.takeIf { it.isNotBlank() }
            ?.runCatching { PaymentStatus.valueOf(this) }?.getOrNull()

        paymentDao.upsert(existing.copy(
            status = updatedStatus ?: existing.status,
        ))
    }
}
