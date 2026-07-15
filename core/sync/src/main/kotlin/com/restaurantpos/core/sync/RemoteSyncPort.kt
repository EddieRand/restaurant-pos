package com.restaurantpos.core.sync

/**
 * Abstraction over the remote API used for syncing local changes.
 *
 * Real implementation: HTTP POST /sync/batch with JWT auth.
 * Test implementation: [FakeRemoteSyncPort] in test sources.
 *
 * Conflict resolution contract:
 * - Server accepts the payload if server's updatedAt ≤ record.updatedAt (last-write-wins).
 * - Server returns [SyncResponse.Conflict] if its version is newer; caller must merge.
 * - Authority fields (e.g. finalPrice, auditLog) are always set by server; client must not overwrite them.
 */
interface RemoteSyncPort {
    suspend fun push(record: SyncRecord): SyncResponse
}

sealed class SyncResponse {
    /** Record accepted. */
    object Accepted : SyncResponse()

    /**
     * Server has a newer version.
     * [serverPayload] is the authoritative JSON the client should merge/overwrite locally.
     */
    data class Conflict(val serverPayload: String) : SyncResponse()

    /** Transient network error — safe to retry. */
    data class NetworkError(val cause: Throwable?) : SyncResponse()

    /** Permanent business error — do not retry (e.g. entity deleted on server). */
    data class PermanentError(val reason: String) : SyncResponse()
}
