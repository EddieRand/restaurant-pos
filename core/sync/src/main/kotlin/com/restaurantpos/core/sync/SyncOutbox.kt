package com.restaurantpos.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * Persistence interface for the sync outbox.
 * Implementation: Room DAO via [RoomSyncOutbox] in this module.
 * Tests use [InMemorySyncOutbox].
 */
interface SyncOutbox {
    /** Add or replace a record (upsert by [SyncRecord.id]). */
    suspend fun enqueue(record: SyncRecord)

    /** Return all records in a given status, oldest-first. */
    suspend fun getPending(): List<SyncRecord>

    /** Observe pending count for UI badge. */
    fun observePendingCount(): Flow<Int>

    /** Mark a record as delivered (status → DONE). */
    suspend fun markDone(id: String)

    /** Mark failed + increment retryCount; record returns to PENDING for retry on next flush. */
    suspend fun markFailed(id: String)

    /** Permanently abandon a record that has exceeded max retries (status → FAILED, no retry). */
    suspend fun abandon(id: String)

    /** Delete DONE records older than [beforeEpoch] to keep the table tidy. */
    suspend fun pruneCompleted(beforeEpoch: Long)

    /** Reset all IN_FLIGHT → PENDING (called on process restart to avoid stuck records). */
    suspend fun resetInflight()
}
