package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.TableDao
import com.restaurantpos.core.database.entity.TableEntity
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncOperation
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomTableRepository(
    private val tableDao: TableDao,
    private val syncWriter: SyncWriter,
) : TableRepository {

    override fun observeAll(): Flow<List<Table>> =
        tableDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Table? =
        tableDao.getById(id)?.toDomain()

    override suspend fun save(table: Table) {
        val now = System.currentTimeMillis()
        val stamped = table.copy(updatedAt = now)
        tableDao.upsert(TableEntity.fromDomain(stamped))
        enqueue(stamped)
    }

    override suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?) {
        val now = System.currentTimeMillis()
        tableDao.updateStatusAndOrder(id, status, orderId, now)
        // Push the full row so the server (and other devices) get the complete state.
        tableDao.getById(id)?.let { enqueue(it.toDomain()) }
    }

    /**
     * Applies tables pulled from the server. Last-write-wins by [Table.updatedAt]:
     * a remote row only overwrites the local one when it is at least as new, so a
     * device's own in-flight mutation is never clobbered by a stale pull.
     */
    override suspend fun applyRemote(tables: List<Table>) {
        val toUpsert = tables.filter { remote ->
            val local = tableDao.getById(remote.id)
            local == null || remote.updatedAt >= local.updatedAt
        }
        if (toUpsert.isNotEmpty()) {
            tableDao.upsertAll(toUpsert.map { TableEntity.fromDomain(it) })
        }
    }

    private suspend fun enqueue(table: Table) {
        // Field names mirror SyncPushProcessor.processTable — keep both sides in sync.
        val payload = JSONObject().apply {
            put("id", table.id)
            put("name", table.name)
            put("sectionId", table.sectionId)
            put("capacity", table.capacity)
            put("currentOrderId", table.currentOrderId)
            put("status", table.status.name)
            put("updatedAt", table.updatedAt)
        }.toString()
        syncWriter.enqueue(
            entityType = SyncEntityType.TABLE,
            entityId = table.id,
            operation = SyncOperation.UPDATE,
            payload = payload,
        )
    }
}
