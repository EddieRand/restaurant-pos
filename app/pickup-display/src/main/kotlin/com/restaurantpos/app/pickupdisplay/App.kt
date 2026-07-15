package com.restaurantpos.app.pickupdisplay

import android.app.Application
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.network.TerminalAuthService
import com.restaurantpos.core.sync.OrderSyncPuller
import com.restaurantpos.core.sync.RegionFormatSyncPuller
import com.restaurantpos.core.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var orderSyncPuller: OrderSyncPuller
    @Inject lateinit var terminalAuthService: TerminalAuthService
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var regionFormatSyncPuller: RegionFormatSyncPuller

    override fun onCreate() {
        super.onCreate()
        val appScope = MainScope()
        syncEngine.start(appScope)
        // WEB 后台货币/数字格式下发，保证各端金额显示一致
        regionFormatSyncPuller.start(appScope)
        // 叫号屏自身不产生订单，靠订单下行同步获知 READY_FOR_PICKUP
        orderSyncPuller.start(appScope)
        appScope.launch {
            terminalAuthService.loginTerminal(configRepository.current().terminalId)
        }
    }
}
