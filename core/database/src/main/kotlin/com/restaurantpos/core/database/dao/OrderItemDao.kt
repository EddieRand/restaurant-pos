package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.OrderItemEntity
import com.restaurantpos.core.model.OrderItemStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderItemDao {
    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY course, id")
    fun observeByOrder(orderId: String): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY course, id")
    suspend fun getByOrder(orderId: String): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE id = :id")
    suspend fun getById(id: String): OrderItemEntity?

    @Upsert
    suspend fun upsert(item: OrderItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<OrderItemEntity>)

    @Query("UPDATE order_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: OrderItemStatus)

    @Query("DELETE FROM order_items WHERE id = :id")
    suspend fun delete(id: String)
}
