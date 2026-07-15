package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.OrderEntity
import com.restaurantpos.core.model.OrderFulfillmentStatus
import com.restaurantpos.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE tableId = :tableId AND status NOT IN ('CLOSED','VOIDED') LIMIT 1")
    suspend fun getActiveByTable(tableId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status NOT IN ('CLOSED','VOIDED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<OrderEntity>>

    @Upsert
    suspend fun upsert(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: OrderStatus, updatedAt: Long)

    @Query("UPDATE orders SET subtotalMinorUnit = :subtotal, taxTotalMinorUnit = :taxTotal, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long, updatedAt: Long)

    @Query("UPDATE orders SET tipMinorUnit = :tip, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTip(id: String, tip: Long, updatedAt: Long)

    @Query("UPDATE orders SET fulfillmentStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM orders WHERE createdAt >= :sinceEpoch AND pickupCode IS NOT NULL")
    suspend fun countPickupCodesSince(sinceEpoch: Long): Int

    @Query("UPDATE orders SET pickupCode = :pickupCode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPickupCode(id: String, pickupCode: String, updatedAt: Long)

    @Query("SELECT * FROM orders WHERE fulfillmentStatus = 'READY_FOR_PICKUP' ORDER BY updatedAt ASC")
    fun observeReadyForPickup(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'CLOSED' AND updatedAt BETWEEN :fromEpoch AND :toEpoch")
    suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<OrderEntity>

    @Query("""
        SELECT * FROM orders
        WHERE (:status IS NULL OR status = :status)
          AND (:tableId IS NULL OR tableId = :tableId)
          AND (:fromEpoch IS NULL OR createdAt >= :fromEpoch)
          AND (:toEpoch IS NULL OR createdAt <= :toEpoch)
        ORDER BY createdAt DESC
        LIMIT 100
    """)
    suspend fun searchOrders(
        status: String?,
        tableId: String?,
        fromEpoch: Long?,
        toEpoch: Long?,
    ): List<OrderEntity>
}
