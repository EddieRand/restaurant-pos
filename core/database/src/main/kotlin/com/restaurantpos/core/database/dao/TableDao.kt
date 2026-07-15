package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.TableEntity
import com.restaurantpos.core.model.TableStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {
    @Query("SELECT * FROM tables ORDER BY sectionId, name")
    fun observeAll(): Flow<List<TableEntity>>

    @Query("SELECT * FROM tables WHERE id = :id")
    suspend fun getById(id: String): TableEntity?

    @Upsert
    suspend fun upsert(table: TableEntity)

    @Upsert
    suspend fun upsertAll(tables: List<TableEntity>)

    @Query("UPDATE tables SET status = :status, currentOrderId = :orderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?, updatedAt: Long)
}
