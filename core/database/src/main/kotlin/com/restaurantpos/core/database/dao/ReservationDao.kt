package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.ReservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {

    @Query("SELECT * FROM reservations WHERE scheduledAt BETWEEN :fromEpoch AND :toEpoch ORDER BY scheduledAt ASC")
    fun observeByDate(fromEpoch: Long, toEpoch: Long): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReservationEntity?

    @Upsert
    suspend fun upsert(reservation: ReservationEntity)

    @Query("UPDATE reservations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
