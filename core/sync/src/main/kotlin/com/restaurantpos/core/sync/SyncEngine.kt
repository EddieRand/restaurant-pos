package com.restaurantpos.core.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Core sync engine.
 *
 * Responsibilities:
 * 1. On [start]: reset IN_FLIGHT records (crash recovery), then flush outbox.
 * 2. Watches [NetworkMonitor.isOnline]: flushes outbox every time connectivity is restored.
 * 3. [flush]: iterates PENDING records, pushes each to [RemoteSyncPort], handles responses.
 * 4. Conflict resolution: server wins on [SyncResponse.Conflict] — stores server payload
 *    via [ConflictResolver] for the caller to apply locally.
 * 5. Max retries: records failing > [maxRetries] times are left as FAILED and not retried.
 */
class SyncEngine(
    private val outbox: SyncOutbox,
    private val remote: RemoteSyncPort,
    private val network: NetworkMonitor,
    private val conflictResolver: ConflictResolver = NoOpConflictResolver,
    val maxRetries: Int = 5,
    private val pollIntervalMs: Long = 5_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            outbox.resetInflight()
            flush()

            launch {
                network.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { flush() }
            }

            // F-013: 会话中产生的新记录也要及时推送 —— 周期冲账，
            // 否则 outbox 只在启动/断网恢复时清空，正常营业期间订单永远不上云。
            while (isActive) {
                delay(pollIntervalMs)
                flush()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** Synchronously flush all pending records. Returns number successfully delivered. */
    suspend fun flush(): Int {
        val pending = outbox.getPending()
        var delivered = 0
        for (record in pending) {
            if (record.retryCount >= maxRetries) {
                // Too many retries — permanently abandon so it no longer blocks the queue
                outbox.abandon(record.id)
                continue
            }
            when (val response = remote.push(record)) {
                is SyncResponse.Accepted -> {
                    outbox.markDone(record.id)
                    delivered++
                }
                is SyncResponse.Conflict -> {
                    conflictResolver.resolve(record, response.serverPayload)
                    outbox.markDone(record.id)
                    delivered++
                }
                is SyncResponse.NetworkError -> {
                    // Increment retry count, leave as PENDING — will retry on next flush
                    outbox.markFailed(record.id)
                    // Stop flush on network error — will retry when connectivity returns
                    break
                }
                is SyncResponse.PermanentError -> {
                    // Business error: abandon immediately, no retry
                    outbox.abandon(record.id)
                    // Don't break — skip this record and continue with others
                }
            }
        }
        return delivered
    }
}

/**
 * Called when the server returns a newer version of an entity.
 * The implementation should apply [serverPayload] to the local Room database.
 */
interface ConflictResolver {
    suspend fun resolve(local: SyncRecord, serverPayload: String)
}

/** No-op: discards server payload. Safe for unit tests; replace with real impl in production. */
object NoOpConflictResolver : ConflictResolver {
    override suspend fun resolve(local: SyncRecord, serverPayload: String) = Unit
}
