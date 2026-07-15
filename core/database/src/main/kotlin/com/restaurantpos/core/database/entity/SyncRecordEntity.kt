package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncRecord
import com.restaurantpos.core.sync.SyncStatus

@Entity(tableName = "sync_outbox")
data class SyncRecordEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val updatedAt: Long,
    val retryCount: Int = 0,
    val status: String = SyncStatus.PENDING.name,
) {
    fun toDomain() = SyncRecord(
        id = id,
        entityType = SyncEntityType.valueOf(entityType),
        entityId = entityId,
        operation = SyncOperation.valueOf(operation),
        payload = payload,
        updatedAt = updatedAt,
        retryCount = retryCount,
        status = SyncStatus.valueOf(status),
    )

    companion object {
        fun fromDomain(r: SyncRecord) = SyncRecordEntity(
            id = r.id,
            entityType = r.entityType.name,
            entityId = r.entityId,
            operation = r.operation.name,
            payload = r.payload,
            updatedAt = r.updatedAt,
            retryCount = r.retryCount,
            status = r.status.name,
        )
    }
}
