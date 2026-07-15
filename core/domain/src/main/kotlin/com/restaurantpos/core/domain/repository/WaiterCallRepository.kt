package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.WaiterCall
import kotlinx.coroutines.flow.Flow

interface WaiterCallRepository {
    /** All unacknowledged waiter calls, newest first. */
    fun observePending(): Flow<List<WaiterCall>>
    suspend fun call(call: WaiterCall)
    suspend fun acknowledge(callId: String)
    suspend fun acknowledgeAll(tableId: String)
}
