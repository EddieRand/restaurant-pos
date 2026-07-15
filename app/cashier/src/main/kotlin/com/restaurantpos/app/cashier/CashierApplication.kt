package com.restaurantpos.app.cashier

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.restaurantpos.app.cashier.worker.DailySnapshotWorker
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.database.DataSeeder
import com.restaurantpos.core.network.TerminalAuthService
import com.restaurantpos.core.sync.KitchenTicketSyncPuller
import com.restaurantpos.core.sync.MenuSyncPuller
import com.restaurantpos.core.sync.OrderSyncPuller
import com.restaurantpos.core.sync.PermissionSyncPuller
import com.restaurantpos.core.sync.RegionFormatSyncPuller
import com.restaurantpos.core.sync.TableSyncPuller
import com.restaurantpos.core.sync.CustomerSyncPuller
import com.restaurantpos.core.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CashierApplication : Application() {

    @Inject lateinit var dataSeeder: DataSeeder
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var menuSyncPuller: MenuSyncPuller
    @Inject lateinit var kitchenTicketSyncPuller: KitchenTicketSyncPuller
    @Inject lateinit var orderSyncPuller: OrderSyncPuller
    @Inject lateinit var permissionSyncPuller: PermissionSyncPuller
    @Inject lateinit var tableSyncPuller: TableSyncPuller
    @Inject lateinit var customerSyncPuller: CustomerSyncPuller
    @Inject lateinit var regionFormatSyncPuller: RegionFormatSyncPuller
    @Inject lateinit var terminalAuthService: TerminalAuthService
    @Inject lateinit var configRepository: ConfigRepository

    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        dataSeeder.seedIfEmpty()
        syncEngine.start(appScope)
        menuSyncPuller.start(appScope)
        kitchenTicketSyncPuller.start(appScope)
        // 拉取其他终端（Kiosk/手持/扫码）创建的订单，收银台才能看到并结账
        orderSyncPuller.start(appScope)
        permissionSyncPuller.start(appScope)
        // 跨终端桌台状态（手持开台→收银台可见）与客户目录下行同步（F-024）
        tableSyncPuller.start(appScope)
        customerSyncPuller.start(appScope)
        // WEB 后台货币/数字格式（符号、小数位、分隔符）下发，保证各端金额显示一致
        regionFormatSyncPuller.start(appScope)
        // Exchange terminalId for server JWT so sync push requests are authenticated.
        // Fire-and-forget: if server is unreachable the outbox will retry when online.
        appScope.launch {
            val terminalId = configRepository.current().terminalId
            terminalAuthService.loginTerminal(terminalId)
        }

        // Schedule daily snapshot ETL: runs every 24h (ideal at ~03:00 for yesterday's data)
        scheduleDailySnapshotWork()
    }

    private fun scheduleDailySnapshotWork() {
        val dailySnapshotWork = PeriodicWorkRequestBuilder<DailySnapshotWorker>(
            24, TimeUnit.HOURS,
        )
            .setInitialDelay(3, TimeUnit.HOURS) // First run after 3 hours, then every 24h
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_snapshot_etl",
            ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already scheduled
            dailySnapshotWork,
        )
    }
}
