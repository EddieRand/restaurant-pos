package com.restaurantpos.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory implementation used in unit tests and as the stub before Room impl is wired.
 * Thread-safe via synchronized access to [store].
 */
class InMemorySyncOutbox : SyncOutbox {

    private val store = MutableStateFlow<Map<String, SyncRecord>>(emptyMap())

    override suspend fun enqueue(record: SyncRecord) {
        store.value = store.value + (record.id to record)
    }

    override suspend fun getPending(): List<SyncRecord> =
        store.value.values
            .filter { it.status == SyncStatus.PENDING }
            .sortedBy { it.updatedAt }

    override fun observePendingCount(): Flow<Int> =
        store.map { m -> m.values.count { it.status == SyncStatus.PENDING } }

    override suspend fun markDone(id: String) = updateStatus(id, SyncStatus.DONE)

    override suspend fun markFailed(id: String) {
        val existing = store.value[id] ?: return
        store.value = store.value + (id to existing.copy(
            status = SyncStatus.PENDING,
            retryCount = existing.retryCount + 1,
        ))
    }

    override suspend fun abandon(id: String) = updateStatus(id, SyncStatus.FAILED)

    override suspend fun pruneCompleted(beforeEpoch: Long) {
        store.value = store.value.filterValues {
            !(it.status == SyncStatus.DONE && it.updatedAt < beforeEpoch)
        }
    }

    override suspend fun resetInflight() {
        store.value = store.value.mapValues {
            if (it.value.status == SyncStatus.IN_FLIGHT) it.value.copy(status = SyncStatus.PENDING)
            else it.value
        }
    }

    private fun updateStatus(id: String, status: SyncStatus) {
        val existing = store.value[id] ?: return
        store.value = store.value + (id to existing.copy(status = status))
    }

    /** Test helper: count by status. */
    fun countByStatus(status: SyncStatus): Int =
        store.value.values.count { it.status == status }
}
