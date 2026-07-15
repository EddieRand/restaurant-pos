package com.restaurantpos.core.hardware

import android.util.Log

class MockCashDrawer : CashDrawerPort {
    var openCount = 0
        private set

    override suspend fun open(): DrawerResult {
        openCount++
        Log.d("MockCashDrawer", "Cash drawer opened (count=$openCount)")
        return DrawerResult.Success
    }

    override suspend fun isConnected(): Boolean = true

    fun reset() { openCount = 0 }
}
