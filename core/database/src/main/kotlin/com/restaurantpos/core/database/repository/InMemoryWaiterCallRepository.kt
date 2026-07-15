package com.restaurantpos.core.database.repository

import com.restaurantpos.core.domain.repository.WaiterCallRepository
import com.restaurantpos.core.model.WaiterCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation — suitable for single-device operation.
 * Replace with a Room-backed + sync implementation when multi-device support is needed.
 */
@Singleton
class InMemoryWaiterCallRepository @Inject constructor() : WaiterCallRepository {

    private val _calls = MutableStateFlow<List<WaiterCall>>(emptyList())

    override fun observePending(): Flow<List<WaiterCall>> =
        _calls.map { list -> list.filter { it.acknowledgedAt == null }.sortedByDescending { it.createdAt } }

    override suspend fun call(call: WaiterCall) {
        _calls.update { it + call }
    }

    override suspend fun acknowledge(callId: String) {
        _calls.update { list ->
            list.map { if (it.id == callId) it.copy(acknowledgedAt = System.currentTimeMillis()) else it }
        }
    }

    override suspend fun acknowledgeAll(tableId: String) {
        _calls.update { list ->
            list.map { if (it.tableId == tableId && it.acknowledgedAt == null)
                it.copy(acknowledgedAt = System.currentTimeMillis()) else it }
        }
    }
}
