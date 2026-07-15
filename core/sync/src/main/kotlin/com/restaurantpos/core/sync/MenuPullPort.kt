package com.restaurantpos.core.sync

import com.restaurantpos.core.model.MenuItem

/**
 * Abstraction over the remote API used for pulling server-authoritative menu changes.
 *
 * Real implementation: HTTP GET /sync/pull?since=<watermark> with JWT auth.
 * Test implementation: a fake in test sources.
 */
interface MenuPullPort {
    /** Returns menu items whose server `updatedAt` is strictly greater than [since]. */
    suspend fun pullMenuItems(since: Long): MenuPullResult?
}

/**
 * [serverTime] becomes the new watermark on success — always taken from the server's clock,
 * never the client's, to avoid drift between devices.
 */
data class MenuPullResult(
    val serverTime: Long,
    val items: List<MenuItem>,
)
