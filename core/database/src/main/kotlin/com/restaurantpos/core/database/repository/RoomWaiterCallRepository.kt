package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.WaiterCallDao
import com.restaurantpos.core.database.entity.WaiterCallEntity
import com.restaurantpos.core.domain.repository.WaiterCallRepository
import com.restaurantpos.core.model.WaiterCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWaiterCallRepository(
    private val dao: WaiterCallDao,
) : WaiterCallRepository {

    override fun observePending(): Flow<List<WaiterCall>> =
        dao.observePending().map { it.map(WaiterCallEntity::toDomain) }

    override suspend fun call(call: WaiterCall) =
        dao.upsert(WaiterCallEntity.fromDomain(call))

    override suspend fun acknowledge(callId: String) =
        dao.acknowledge(callId, System.currentTimeMillis())

    override suspend fun acknowledgeAll(tableId: String) =
        dao.acknowledgeAll(tableId, System.currentTimeMillis())
}
