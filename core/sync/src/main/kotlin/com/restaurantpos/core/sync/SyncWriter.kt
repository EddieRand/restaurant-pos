package com.restaurantpos.core.sync

import java.util.UUID

/**
 * Helper used by repositories to enqueue an outbox record after every local write.
 *
 * Usage (in a repository):
 * ```kotlin
 * override suspend fun save(order: Order) {
 *     orderDao.upsert(OrderEntity.fromDomain(order))
 *     syncWriter.enqueue(SyncEntityType.ORDER, order.id, SyncOperation.UPDATE, order.toJson())
 * }
 * ```
 */
class SyncWriter(private val outbox: SyncOutbox) {
    suspend fun enqueue(
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncOperation,
        payload: String,
        now: Long = System.currentTimeMillis(),
    ) {
        outbox.enqueue(
            SyncRecord(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payload = payload,
                updatedAt = now,
            )
        )
    }
}
