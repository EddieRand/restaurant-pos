package com.restaurantpos.core.sync

/** Persists "last successfully pulled" cursors per entity type, surviving process death. */
interface SyncWatermarkStore {
    suspend fun getLastPullAt(entityType: SyncEntityType): Long
    suspend fun setLastPullAt(entityType: SyncEntityType, timestamp: Long)
}
