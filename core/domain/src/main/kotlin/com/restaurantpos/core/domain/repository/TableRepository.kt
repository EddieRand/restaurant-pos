package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus
import kotlinx.coroutines.flow.Flow

interface TableRepository {
    fun observeAll(): Flow<List<Table>>
    suspend fun getById(id: String): Table?
    suspend fun save(table: Table)
    suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?)

    /** Applies tables pulled from the server (last-write-wins by updatedAt); must not re-enqueue outbox. */
    suspend fun applyRemote(tables: List<Table>)
}
