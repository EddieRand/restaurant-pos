package com.restaurantpos.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * Observes network reachability.
 * Android implementation uses ConnectivityManager callbacks.
 * [MockNetworkMonitor] is used in tests.
 */
interface NetworkMonitor {
    /** Emits true when network is available, false when offline. */
    val isOnline: Flow<Boolean>
}
