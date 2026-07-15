package com.restaurantpos.core.sync

import com.restaurantpos.core.domain.repository.RolePermissionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls the server-authoritative role & permission matrix down to the local Room
 * database.
 *
 * The permission matrix is server-authoritative (admin edits via Web Admin); devices
 * never push permission changes up. On [start] and on every reconnect, fetches the
 * full set of role-permission mappings and replaces local data by role.
 *
 * Since the dataset is small (4 roles × 27 permissions = ~108 rows), we always
 * do a full replace — no incremental watermark needed.
 */
class PermissionSyncPuller(
    private val port: PermissionPullPort,
    private val rolePermissionRepository: RolePermissionRepository,
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
            // Poll periodically — admin permission edits must reach devices mid-session
            while (isActive) {
                delay(pollIntervalMs)
                pull()
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /**
     * Fetch and replace local role permissions.
     * Best-effort: swallows network errors so a temporary disconnect doesn't crash
     * the puller.
     */
    suspend fun pull() {
        val result = runCatching { port.pullPermissions() }.getOrNull() ?: return
        // Group pulled role-permissions by roleId and replace each role's
        // permissions atomically using the existing transaction-safe method.
        val grouped = result.rolePermissions.groupBy({ it.roleId }) { it.permissionKey }
        for ((roleId, permissionKeys) in grouped) {
            rolePermissionRepository.replacePermissions(roleId, permissionKeys)
        }
    }
}
