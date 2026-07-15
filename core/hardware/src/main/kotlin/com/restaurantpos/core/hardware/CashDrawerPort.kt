package com.restaurantpos.core.hardware

/**
 * Hardware abstraction for cash drawer.
 * SUNMI implementation: send ESC/POS kick-drawer command via serial/USB.
 * MockCashDrawer is used for all non-hardware paths.
 */
interface CashDrawerPort {
    suspend fun open(): DrawerResult
    suspend fun isConnected(): Boolean
}

sealed class DrawerResult {
    object Success : DrawerResult()
    data class Failure(val reason: String) : DrawerResult()
}
