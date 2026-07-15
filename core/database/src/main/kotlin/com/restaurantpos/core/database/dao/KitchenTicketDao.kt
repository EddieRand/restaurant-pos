package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.KitchenTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenTicketDao {

    @Query("SELECT * FROM kitchen_tickets WHERE status NOT IN ('DONE') ORDER BY course ASC, createdAt ASC")
    fun observeActive(): Flow<List<KitchenTicketEntity>>

    @Query("SELECT * FROM kitchen_tickets WHERE stationId = :stationId AND status NOT IN ('DONE') ORDER BY course ASC, createdAt ASC")
    fun observeByStation(stationId: String): Flow<List<KitchenTicketEntity>>

    @Query("SELECT * FROM kitchen_tickets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KitchenTicketEntity?

    @Query("SELECT * FROM kitchen_tickets WHERE orderId = :orderId")
    suspend fun getByOrder(orderId: String): List<KitchenTicketEntity>

    @Upsert
    suspend fun upsert(ticket: KitchenTicketEntity)

    @Upsert
    suspend fun upsertAll(tickets: List<KitchenTicketEntity>)

    @Query("UPDATE kitchen_tickets SET status = :status, bumpedAt = :bumpedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, bumpedAt: Long?, updatedAt: Long)

    @Query("SELECT * FROM kitchen_tickets WHERE updatedAt > :since")
    suspend fun getUpdatedSince(since: Long): List<KitchenTicketEntity>
}
