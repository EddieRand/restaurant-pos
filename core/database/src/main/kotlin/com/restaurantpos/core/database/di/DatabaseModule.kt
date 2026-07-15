package com.restaurantpos.core.database.di

import android.content.Context
import androidx.room.Room
import com.restaurantpos.core.database.PosDatabase
import com.restaurantpos.core.database.migration.MIGRATION_1_2
import com.restaurantpos.core.database.migration.MIGRATION_2_3
import com.restaurantpos.core.database.migration.MIGRATION_3_4
import com.restaurantpos.core.database.migration.MIGRATION_4_5
import com.restaurantpos.core.database.migration.MIGRATION_5_6
import com.restaurantpos.core.database.migration.MIGRATION_6_7
import com.restaurantpos.core.database.migration.MIGRATION_7_8
import com.restaurantpos.core.database.migration.MIGRATION_8_9
import com.restaurantpos.core.database.migration.MIGRATION_9_10
import com.restaurantpos.core.database.migration.MIGRATION_10_11
import com.restaurantpos.core.database.migration.MIGRATION_11_12
import com.restaurantpos.core.database.migration.MIGRATION_12_13
import com.restaurantpos.core.database.migration.MIGRATION_13_14
import com.restaurantpos.core.database.migration.MIGRATION_14_15
import com.restaurantpos.core.database.migration.MIGRATION_15_16
import com.restaurantpos.core.database.migration.MIGRATION_16_17
import com.restaurantpos.core.database.migration.MIGRATION_17_18
import com.restaurantpos.core.database.migration.MIGRATION_18_19
import com.restaurantpos.core.database.migration.MIGRATION_19_20
import com.restaurantpos.core.database.migration.MIGRATION_20_21
import com.restaurantpos.core.database.repository.*
import com.restaurantpos.core.database.sync.RoomConflictResolver
import com.restaurantpos.core.database.sync.RoomSyncOutbox
import com.restaurantpos.core.domain.repository.*
import com.restaurantpos.core.sync.ConflictResolver
import com.restaurantpos.core.sync.KitchenTicketPullPort
import com.restaurantpos.core.sync.OrderPullPort
import com.restaurantpos.core.sync.OrderSyncPuller
import com.restaurantpos.core.sync.KitchenTicketSyncPuller
import com.restaurantpos.core.sync.MenuPullPort
import com.restaurantpos.core.sync.MenuSyncPuller
import com.restaurantpos.core.sync.NetworkMonitor
import com.restaurantpos.core.sync.SyncOutbox
import com.restaurantpos.core.sync.SyncWatermarkStore
import com.restaurantpos.core.sync.SyncWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "pos-database")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
            .build()

    @Provides fun provideMenuItemDao(db: PosDatabase) = db.menuItemDao()
    @Provides fun provideOrderDao(db: PosDatabase) = db.orderDao()
    @Provides fun provideOrderItemDao(db: PosDatabase) = db.orderItemDao()
    @Provides fun provideTableDao(db: PosDatabase) = db.tableDao()
    @Provides fun providePaymentDao(db: PosDatabase) = db.paymentDao()
    @Provides fun provideSyncRecordDao(db: PosDatabase) = db.syncRecordDao()
    @Provides fun provideReservationDao(db: PosDatabase) = db.reservationDao()
    @Provides fun provideKitchenTicketDao(db: PosDatabase) = db.kitchenTicketDao()
    @Provides fun provideUserDao(db: PosDatabase) = db.userDao()
    @Provides fun provideModifierGroupDao(db: PosDatabase) = db.modifierGroupDao()
    @Provides fun provideCouponDao(db: PosDatabase) = db.couponDao()
    @Provides fun provideComboDao(db: PosDatabase) = db.comboDao()
    @Provides fun provideWaiterCallDao(db: PosDatabase) = db.waiterCallDao()
    @Provides fun provideMenuProfileDao(db: PosDatabase) = db.menuProfileDao()
    @Provides fun provideCustomerDao(db: PosDatabase) = db.customerDao()
    @Provides fun provideRoleDao(db: PosDatabase) = db.roleDao()
    @Provides fun provideRolePermissionDao(db: PosDatabase) = db.rolePermissionDao()

    @Provides
    @Singleton
    fun provideRolePermissionRepository(db: PosDatabase): RolePermissionRepository =
        RoomRolePermissionRepository(db.roleDao(), db.rolePermissionDao())

    @Provides
    @Singleton
    fun provideSyncOutbox(db: PosDatabase): SyncOutbox = RoomSyncOutbox(db.syncRecordDao())

    @Provides
    @Singleton
    fun provideOrderRepository(db: PosDatabase, syncWriter: SyncWriter): OrderRepository =
        RoomOrderRepository(db.orderDao(), db.orderItemDao(), syncWriter)

    @Provides
    @Singleton
    fun provideTableRepository(db: PosDatabase, syncWriter: SyncWriter): TableRepository =
        RoomTableRepository(db.tableDao(), syncWriter)

    @Provides
    @Singleton
    fun providePaymentRepository(db: PosDatabase, syncWriter: SyncWriter): PaymentRepository =
        RoomPaymentRepository(db.paymentDao(), syncWriter)

    @Provides
    @Singleton
    fun provideMenuItemRepository(db: PosDatabase): MenuItemRepository =
        RoomMenuItemRepository(db.menuItemDao(), db.menuProfileDao(), db.modifierGroupDao())

    @Provides
    @Singleton
    fun provideMenuSyncPuller(
        menuPullPort: MenuPullPort,
        watermarkStore: SyncWatermarkStore,
        network: NetworkMonitor,
        menuItemRepository: MenuItemRepository,
    ): MenuSyncPuller = MenuSyncPuller(
        port = menuPullPort,
        watermarkStore = watermarkStore,
        network = network,
        applyItems = { items -> menuItemRepository.upsertAll(items) },
    )

    @Provides
    @Singleton
    fun provideKitchenTicketSyncPuller(
        port: KitchenTicketPullPort,
        watermarkStore: SyncWatermarkStore,
        network: NetworkMonitor,
        kitchenTicketRepository: KitchenTicketRepository,
    ): KitchenTicketSyncPuller = KitchenTicketSyncPuller(
        port = port,
        watermarkStore = watermarkStore,
        network = network,
        applyTickets = { tickets -> kitchenTicketRepository.applyRemote(tickets) },
    )

    @Provides
    @Singleton
    fun provideOrderSyncPuller(
        port: OrderPullPort,
        watermarkStore: SyncWatermarkStore,
        network: NetworkMonitor,
        orderRepository: OrderRepository,
    ): OrderSyncPuller = OrderSyncPuller(
        port = port,
        watermarkStore = watermarkStore,
        network = network,
        applyOrders = { orders, items -> orderRepository.applyRemote(orders, items) },
    )

    @Provides
    @Singleton
    fun provideMenuProfileRepository(db: PosDatabase): MenuProfileRepository =
        RoomMenuProfileRepository(db.menuProfileDao())

    @Provides
    @Singleton
    fun provideKitchenTicketRepository(db: PosDatabase, syncWriter: SyncWriter): KitchenTicketRepository =
        RoomKitchenTicketRepository(db.kitchenTicketDao(), syncWriter)

    @Provides
    @Singleton
    fun provideReservationRepository(db: PosDatabase, syncWriter: SyncWriter): ReservationRepository =
        RoomReservationRepository(db.reservationDao(), syncWriter)

    @Provides
    @Singleton
    fun provideUserRepository(db: PosDatabase): UserRepository =
        RoomUserRepository(db.userDao())

    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository = InMemorySessionRepository()

    @Provides
    @Singleton
    fun provideCouponRepository(db: PosDatabase): CouponRepository =
        RoomCouponRepository(db.couponDao())

    @Provides
    @Singleton
    fun provideComboRepository(db: PosDatabase): ComboRepository =
        RoomComboRepository(db.comboDao())

    @Provides
    @Singleton
    fun provideWaiterCallRepository(db: PosDatabase): WaiterCallRepository =
        RoomWaiterCallRepository(db.waiterCallDao())

    @Provides
    @Singleton
    fun provideCustomerRepository(db: PosDatabase, syncWriter: SyncWriter): CustomerRepository =
        RoomCustomerRepository(db.customerDao(), syncWriter)

    @Provides fun provideDailySnapshotDao(db: PosDatabase) = db.dailySnapshotDao()

    @Provides
    @Singleton
    fun provideReportRepository(db: PosDatabase, syncWriter: SyncWriter): ReportRepository =
        RoomReportRepository(db.dailySnapshotDao(), syncWriter)

    @Provides
    @Singleton
    fun provideConflictResolver(db: PosDatabase): ConflictResolver = RoomConflictResolver(
        orderDao = db.orderDao(),
        orderItemDao = db.orderItemDao(),
        menuItemDao = db.menuItemDao(),
        paymentDao = db.paymentDao(),
    )
}
