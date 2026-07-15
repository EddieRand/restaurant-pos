package com.restaurantpos.core.database.sync

import com.restaurantpos.core.database.dao.SyncRecordDao
import com.restaurantpos.core.database.entity.SyncRecordEntity
import com.restaurantpos.core.sync.SyncOutbox
import com.restaurantpos.core.sync.SyncRecord
import com.restaurantpos.core.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [SyncOutbox]. Survives process death — records are durably persisted
 * in the `sync_outbox` table and replayed on next app launch.
 */
class RoomSyncOutbox(private val dao: SyncRecordDao) : SyncOutbox {

    override suspend fun enqueue(record: SyncRecord) =
        dao.upsert(SyncRecordEntity.fromDomain(record))

    override suspend fun getPending(): List<SyncRecord> =
        dao.getPending().map { it.toDomain() }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun markDone(id: String) = dao.markDone(id)

    override suspend fun markFailed(id: String) = dao.markFailed(id)

    override suspend fun abandon(id: String) = dao.abandon(id)

    override suspend fun pruneCompleted(beforeEpoch: Long) = dao.pruneCompleted(beforeEpoch)

    override suspend fun resetInflight() = dao.resetInflight()
}
