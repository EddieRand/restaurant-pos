package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.WaiterCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaiterCallDao {

    @Query("SELECT * FROM waiter_calls WHERE acknowledgedAt IS NULL ORDER BY createdAt DESC")
    fun observePending(): Flow<List<WaiterCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(call: WaiterCallEntity)

    @Query("UPDATE waiter_calls SET acknowledgedAt = :at WHERE id = :id")
    suspend fun acknowledge(id: String, at: Long)

    @Query("UPDATE waiter_calls SET acknowledgedAt = :at WHERE tableId = :tableId AND acknowledgedAt IS NULL")
    suspend fun acknowledgeAll(tableId: String, at: Long)

    @Query("DELETE FROM waiter_calls WHERE acknowledgedAt IS NOT NULL AND acknowledgedAt < :before")
    suspend fun purgeAcknowledgedBefore(before: Long)
}
