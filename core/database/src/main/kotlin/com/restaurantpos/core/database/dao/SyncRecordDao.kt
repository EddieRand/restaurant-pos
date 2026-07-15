package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.SyncRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRecordDao {

    @Upsert
    suspend fun upsert(record: SyncRecordEntity)

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun getPending(): List<SyncRecordEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING' OR status = 'IN_FLIGHT'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE sync_outbox SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: String)

    // Increment retryCount and return to PENDING so the engine will try again on next flush.
    @Query("UPDATE sync_outbox SET status = 'PENDING', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailed(id: String)

    // Permanently abandon a record that has exceeded maxRetries.
    @Query("UPDATE sync_outbox SET status = 'FAILED' WHERE id = :id")
    suspend fun abandon(id: String)

    @Query("UPDATE sync_outbox SET status = 'IN_FLIGHT' WHERE id = :id")
    suspend fun markInFlight(id: String)

    @Query("UPDATE sync_outbox SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInflight()

    @Query("DELETE FROM sync_outbox WHERE status = 'DONE' AND updatedAt < :beforeEpoch")
    suspend fun pruneCompleted(beforeEpoch: Long)

    @Query("SELECT * FROM sync_outbox WHERE id = :id")
    suspend fun getById(id: String): SyncRecordEntity?
}
