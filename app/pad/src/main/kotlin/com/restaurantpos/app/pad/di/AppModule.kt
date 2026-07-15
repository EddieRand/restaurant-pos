package com.restaurantpos.app.pad.di

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.*
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.domain.usecase.*
import com.restaurantpos.core.hardware.MockPrinter
import com.restaurantpos.core.hardware.PrinterPort
import com.restaurantpos.core.hardware.PrintKitchenTicketUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideConfigRepository(): ConfigRepository = InMemoryConfigRepository()

    @Provides
    fun provideRegionConfig(configRepo: ConfigRepository): RegionConfig = configRepo.current()

    @Provides
    fun providePlaceOrderUseCase(
        orderRepo: OrderRepository,
        tableRepo: TableRepository,
        regionConfig: RegionConfig,
    ): PlaceOrderUseCase = PlaceOrderUseCase(orderRepo, tableRepo, regionConfig)

    @Provides
    fun provideApplyDiscountUseCase(orderRepo: OrderRepository): ApplyDiscountUseCase =
        ApplyDiscountUseCase(orderRepo)

    @Provides
    fun provideSettlePaymentUseCase(
        orderRepo: OrderRepository,
        paymentRepo: PaymentRepository,
        tableRepo: TableRepository,
    ): SettlePaymentUseCase = SettlePaymentUseCase(orderRepo, paymentRepo, tableRepo)

    @Provides
    @Singleton
    fun provideKitchenRouter(configRepo: ConfigRepository): KitchenRouter {
        val kdsCfg = configRepo.current().kdsConfig
        return KitchenRouter(
            routes = kdsCfg.categoryToStation,
            defaultStationId = kdsCfg.defaultStationId,
        )
    }

    @Provides
    fun provideFireKitchenTicketsUseCase(
        orderRepo: OrderRepository,
        ticketRepo: KitchenTicketRepository,
        router: KitchenRouter,
    ): FireKitchenTicketsUseCase = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router)

    @Provides
    @Singleton
    fun providePrinterPort(): PrinterPort = MockPrinter()

    @Provides
    fun provideSplitOrderUseCase(orderRepo: OrderRepository): SplitOrderUseCase =
        SplitOrderUseCase(orderRepo)

    @Provides
    fun providePrintKitchenTicketUseCase(
        orderRepo: OrderRepository,
        printer: PrinterPort,
        router: KitchenRouter,
    ): PrintKitchenTicketUseCase = PrintKitchenTicketUseCase(orderRepo, printer, router)

    @Provides
    fun provideCheckPermissionUseCase(repo: RolePermissionRepository): CheckPermissionUseCase =
        CheckPermissionUseCase(repo)

    @Provides
    fun provideAddComboUseCase(
        comboRepo: ComboRepository,
        menuItemRepo: MenuItemRepository,
        orderRepo: OrderRepository,
    ): AddComboUseCase = AddComboUseCase(comboRepo, menuItemRepo, orderRepo)
}
