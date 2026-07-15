package com.restaurantpos.core.sync

import com.restaurantpos.core.config.PadConfig

/**
 * Abstraction over the remote API used for pulling the merchant's tableside-PAD
 * configuration set up via Web Admin (AYCE rules, idle screen, supported locales, ...).
 *
 * Real implementation: HTTP GET /admin/settings/padConfig with JWT auth.
 * Test implementation: a fake in test sources.
 */
interface PadConfigPullPort {
    /** Returns the merchant's current PAD configuration, or null if unset / unreachable. */
    suspend fun pullPadConfig(): PadConfig?
}
