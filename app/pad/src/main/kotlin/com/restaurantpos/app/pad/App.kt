package com.restaurantpos.app.pad

import android.app.Application
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.network.TerminalAuthService
import com.restaurantpos.core.sync.KitchenTicketSyncPuller
import com.restaurantpos.core.sync.MenuSyncPuller
import com.restaurantpos.core.sync.PadConfigSyncPuller
import com.restaurantpos.core.sync.PermissionSyncPuller
import com.restaurantpos.core.sync.RegionFormatSyncPuller
import com.restaurantpos.core.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var menuSyncPuller: MenuSyncPuller
    @Inject lateinit var kitchenTicketSyncPuller: KitchenTicketSyncPuller
    @Inject lateinit var padConfigSyncPuller: PadConfigSyncPuller
    @Inject lateinit var permissionSyncPuller: PermissionSyncPuller
    @Inject lateinit var terminalAuthService: TerminalAuthService
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var regionFormatSyncPuller: RegionFormatSyncPuller

    override fun onCreate() {
        super.onCreate()
        val appScope = MainScope()
        syncEngine.start(appScope)
        // WEB 后台货币/数字格式下发，保证各端金额显示一致
        regionFormatSyncPuller.start(appScope)
        menuSyncPuller.start(appScope)
        kitchenTicketSyncPuller.start(appScope)
        padConfigSyncPuller.start(appScope)
        permissionSyncPuller.start(appScope)
        appScope.launch {
            terminalAuthService.loginTerminal(configRepository.current().terminalId)
        }
    }
}
