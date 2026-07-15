package com.restaurantpos.core.sync

import com.restaurantpos.core.model.CdsPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcasts the current Customer Display phase to the server via the sync outbox so the
 * customer-facing display can follow the cashier through checkout. Outbound only — there is
 * no local CDS_STATE table; the server stores the latest phase per terminal (last-write-wins
 * by updatedAt) and the web CDS polls it.
 *
 * Fire-and-forget: enqueueing is cheap and may be called from any context (including a
 * ViewModel's onCleared, where the viewModelScope is already gone), so it runs on its own
 * application-lifetime scope.
 */
class CdsPhaseBroadcaster(
    private val syncWriter: SyncWriter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /**
     * @param terminalId identifies which display this state belongs to (the cashier's terminal)
     * @param orderId    the order being shown, or null for the idle [CdsPhase.WELCOME] state
     */
    fun broadcast(terminalId: String, phase: CdsPhase, orderId: String?) {
        val now = System.currentTimeMillis()
        // Minimal hand-built JSON (values are enums / UUID-like ids) — mirrors
        // SyncPushProcessor.processCdsState on the server.
        val orderField = if (orderId != null) "\"$orderId\"" else "null"
        val payload = """{"id":"$terminalId","terminalId":"$terminalId","orderId":$orderField,""" +
            """"phase":"${phase.name}","updatedAt":$now}"""
        scope.launch {
            runCatching {
                syncWriter.enqueue(
                    entityType = SyncEntityType.CDS_STATE,
                    entityId = terminalId,
                    operation = SyncOperation.UPDATE,
                    payload = payload,
                )
            }
        }
    }
}
