package com.restaurantpos.app.handheld

import android.app.Application
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.network.TerminalAuthService
import com.restaurantpos.core.sync.KitchenTicketSyncPuller
import com.restaurantpos.core.sync.MenuSyncPuller
import com.restaurantpos.core.sync.PermissionSyncPuller
import com.restaurantpos.core.sync.RegionFormatSyncPuller
import com.restaurantpos.core.sync.SyncEngine
import com.restaurantpos.core.sync.UserSyncPuller
import com.restaurantpos.core.sync.TableSyncPuller
import com.restaurantpos.core.sync.CustomerSyncPuller
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var menuSyncPuller: MenuSyncPuller
    @Inject lateinit var kitchenTicketSyncPuller: KitchenTicketSyncPuller
    @Inject lateinit var permissionSyncPuller: PermissionSyncPuller
    @Inject lateinit var userSyncPuller: UserSyncPuller
    @Inject lateinit var tableSyncPuller: TableSyncPuller
    @Inject lateinit var customerSyncPuller: CustomerSyncPuller
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
        permissionSyncPuller.start(appScope)
        appScope.launch {
            terminalAuthService.loginTerminal(configRepository.current().terminalId)
            // Start downsync pullers only after the terminal JWT is obtained, so the first
            // pull is authenticated — handheld has no on-device seeder (F-023/F-024).
            userSyncPuller.start(appScope)
            tableSyncPuller.start(appScope)
            customerSyncPuller.start(appScope)
        }
    }
}
