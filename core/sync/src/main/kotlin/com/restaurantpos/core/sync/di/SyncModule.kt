package com.restaurantpos.core.sync.di

import android.content.Context
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.sync.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    // SyncOutbox is provided by DatabaseModule (RoomSyncOutbox) — not declared here.

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        AndroidNetworkMonitor(context)

    @Provides
    @Singleton
    fun provideSyncWatermarkStore(@ApplicationContext context: Context): SyncWatermarkStore =
        SharedPrefsSyncWatermarkStore(context)

    // MenuPullPort is provided by the app layer (core:network NetworkModule, HttpMenuPullPort).

    // PadConfigPullPort is provided by the app layer (core:network NetworkModule, HttpPadConfigPullPort).
    // ConfigRepository is provided by the app/feature layer (e.g. InMemoryConfigRepository).
    @Provides
    @Singleton
    fun providePadConfigSyncPuller(
        port: PadConfigPullPort,
        configRepository: ConfigRepository,
        network: NetworkMonitor,
    ): PadConfigSyncPuller = PadConfigSyncPuller(port, configRepository, network)

    // RegionFormatPullPort is provided by the app layer (core:network NetworkModule, HttpRegionFormatPullPort).
    // Pulls the merchant's currency/number-format settings so every terminal renders money identically.
    @Provides
    @Singleton
    fun provideRegionFormatSyncPuller(
        port: RegionFormatPullPort,
        configRepository: ConfigRepository,
        network: NetworkMonitor,
    ): RegionFormatSyncPuller = RegionFormatSyncPuller(port, configRepository, network)

    // PermissionPullPort is provided by the app layer (core:network NetworkModule, HttpPermissionPullPort).
    // RolePermissionRepository is provided by DatabaseModule.

    @Provides
    @Singleton
    fun providePermissionSyncPuller(
        port: PermissionPullPort,
        repo: RolePermissionRepository,
        network: NetworkMonitor,
    ): PermissionSyncPuller = PermissionSyncPuller(port, repo, network)

    // UserPullPort is provided by the app layer (core:network NetworkModule, HttpUserPullPort).
    // UserRepository is provided by DatabaseModule.
    @Provides
    @Singleton
    fun provideUserSyncPuller(
        port: UserPullPort,
        repo: UserRepository,
        network: NetworkMonitor,
    ): UserSyncPuller = UserSyncPuller(port, repo, network)

    // TablePullPort / CustomerPullPort provided by core:network NetworkModule.
    @Provides
    @Singleton
    fun provideTableSyncPuller(
        port: TablePullPort,
        watermarkStore: SyncWatermarkStore,
        network: NetworkMonitor,
        tableRepository: TableRepository,
    ): TableSyncPuller = TableSyncPuller(
        port = port,
        watermarkStore = watermarkStore,
        network = network,
        applyTables = { tables -> tableRepository.applyRemote(tables) },
    )

    @Provides
    @Singleton
    fun provideCustomerSyncPuller(
        port: CustomerPullPort,
        watermarkStore: SyncWatermarkStore,
        network: NetworkMonitor,
        customerRepository: CustomerRepository,
    ): CustomerSyncPuller = CustomerSyncPuller(
        port = port,
        watermarkStore = watermarkStore,
        network = network,
        applyCustomers = { customers -> customerRepository.applyRemote(customers) },
    )

    // RemoteSyncPort is provided by the app layer:
    // - core:network NetworkModule for production (HttpRemoteSyncPort)
    // - individual app modules may provide a stub for offline/test use

    @Provides
    @Singleton
    fun provideSyncWriter(outbox: SyncOutbox): SyncWriter = SyncWriter(outbox)

    @Provides
    @Singleton
    fun provideCdsPhaseBroadcaster(syncWriter: SyncWriter): CdsPhaseBroadcaster =
        CdsPhaseBroadcaster(syncWriter)

    @Provides
    @Singleton
    fun provideSyncEngine(
        outbox: SyncOutbox,
        remote: RemoteSyncPort,
        network: NetworkMonitor,
        conflictResolver: ConflictResolver,
    ): SyncEngine = SyncEngine(outbox, remote, network, conflictResolver)
}
