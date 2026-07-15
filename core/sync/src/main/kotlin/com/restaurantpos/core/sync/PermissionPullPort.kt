package com.restaurantpos.core.sync

import com.restaurantpos.core.model.Role
import com.restaurantpos.core.model.RolePermission

/**
 * Abstraction over the remote API used for pulling server-authoritative role &
 * permission changes.
 *
 * Real implementation: HTTP GET /sync/permissions with JWT auth.
 * Test implementation: a fake in test sources.
 *
 * Unlike menu items, the permission matrix is small (4 roles × 27 permissions =
 * 108 rows), so we always pull the full set and replace local data on every sync.
 * No incremental watermark is needed.
 */
interface PermissionPullPort {
    /** Returns the full set of roles and role-permission mappings. */
    suspend fun pullPermissions(): PermissionPullResult?
}

/**
 * [serverTime] is the server clock at the moment the data was read; stored as
 * the new watermark so the next pull (if we later add incremental sync) knows
 * where to start.
 */
data class PermissionPullResult(
    val serverTime: Long,
    val roles: List<Role>,
    val rolePermissions: List<RolePermission>,
)
