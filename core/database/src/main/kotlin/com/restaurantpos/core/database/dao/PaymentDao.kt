package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.PaymentEntity
import com.restaurantpos.core.model.PaymentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY createdAt")
    fun observeByOrder(orderId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY createdAt")
    suspend fun getByOrder(orderId: String): List<PaymentEntity>

    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    @Query("UPDATE payments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: PaymentStatus)
}
