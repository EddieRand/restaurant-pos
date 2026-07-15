package com.restaurantpos.core.sync

import com.restaurantpos.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls server-authoritative staff users down to the local Room database so that
 * PIN-login terminals which do not run the on-device seeder (handheld, pad) can
 * authenticate. Pulls on [start], on every reconnect, and every [pollIntervalMs].
 *
 * Users are upserted (never deleted) — a deactivated user arrives with
 * `isActive = false` and is excluded from login by the auth query.
 */
class UserSyncPuller(
    private val port: UserPullPort,
    private val userRepository: UserRepository,
    private val network: NetworkMonitor,
    private val pollIntervalMs: Long = 30_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            pull()
            launch {
                network.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { pull() }
            }
            while (isActive) {
                delay(pollIntervalMs)
                pull()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** Fetch and upsert the full user set. Best-effort: swallows network errors. */
    suspend fun pull() {
        val result = runCatching { port.pullUsers() }.getOrNull() ?: return
        // Guard each save independently: a single bad row (e.g. a duplicate pinHash that
        // collides with the unique index) must not abort syncing the remaining users.
        result.users.forEach { user -> runCatching { userRepository.save(user) } }
    }
}
