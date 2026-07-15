package com.restaurantpos.core.sync

import com.restaurantpos.core.model.User

/**
 * Abstraction over the remote API used for pulling server-authoritative staff
 * users down to terminals that authenticate by PIN (handheld, pad, cashier).
 *
 * Real implementation: HTTP GET /sync/users with JWT auth.
 * Test implementation: a fake in test sources.
 *
 * Like the permission matrix, the user set is small (a single store's staff), so we
 * always pull the full set and upsert locally on every sync — no incremental
 * watermark is needed. Without this, non-seeding terminals start with an empty
 * users table and can never log in (see F-023).
 */
interface UserPullPort {
    /** Returns the full set of staff users (including pinHash for local PIN auth). */
    suspend fun pullUsers(): UserPullResult?
}

/**
 * [serverTime] is the server clock at the moment the data was read.
 */
data class UserPullResult(
    val serverTime: Long,
    val users: List<User>,
)
