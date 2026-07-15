package com.restaurantpos.app.cashier.di

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.config.TimeclockConfig
import com.restaurantpos.core.domain.repository.ComboRepository
import com.restaurantpos.core.domain.repository.CouponRepository
import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideRegionConfig(configRepo: ConfigRepository): RegionConfig = configRepo.current()

    @Provides
    fun provideTimeclockConfig(regionConfig: RegionConfig): TimeclockConfig = regionConfig.timeclockConfig

    @Provides
    fun providePlaceOrderUseCase(
        orderRepo: OrderRepository,
        tableRepo: TableRepository,
        regionConfig: RegionConfig,
    ): PlaceOrderUseCase = PlaceOrderUseCase(orderRepo, tableRepo, regionConfig)

    @Provides
    fun provideSplitOrderUseCase(orderRepo: OrderRepository): SplitOrderUseCase =
        SplitOrderUseCase(orderRepo)

    @Provides
    fun provideTransferTableUseCase(
        orderRepo: OrderRepository,
        tableRepo: TableRepository,
    ): TransferTableUseCase = TransferTableUseCase(orderRepo, tableRepo)

    @Provides
    fun provideSettlePaymentUseCase(
        orderRepo: OrderRepository,
        paymentRepo: PaymentRepository,
        tableRepo: TableRepository,
    ): SettlePaymentUseCase = SettlePaymentUseCase(orderRepo, paymentRepo, tableRepo)

    @Provides
    fun provideApplyDiscountUseCase(orderRepo: OrderRepository): ApplyDiscountUseCase =
        ApplyDiscountUseCase(orderRepo)

    @Provides
    fun provideApplyServiceChargeUseCase(orderRepo: OrderRepository): ApplyServiceChargeUseCase =
        ApplyServiceChargeUseCase(orderRepo)

    @Provides
    fun provideSplitBillUseCase(orderRepo: OrderRepository): SplitBillUseCase =
        SplitBillUseCase(orderRepo)

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
    fun provideBumpTicketUseCase(repo: KitchenTicketRepository, orderRepo: OrderRepository): BumpTicketUseCase =
        BumpTicketUseCase(repo, orderRepo)

    @Provides
    fun provideRecallTicketUseCase(repo: KitchenTicketRepository): RecallTicketUseCase =
        RecallTicketUseCase(repo)

    @Provides
    fun provideRefundUseCase(
        orderRepo: OrderRepository,
        paymentRepo: PaymentRepository,
    ): RefundUseCase = RefundUseCase(orderRepo, paymentRepo)

    @Provides
    fun provideApplyCouponUseCase(
        couponRepo: CouponRepository,
        orderRepo: OrderRepository,
    ): ApplyCouponUseCase = ApplyCouponUseCase(couponRepo, orderRepo)

    @Provides
    fun provideAddComboUseCase(
        comboRepo: ComboRepository,
        menuItemRepo: MenuItemRepository,
        orderRepo: OrderRepository,
    ): AddComboUseCase = AddComboUseCase(comboRepo, menuItemRepo, orderRepo)

}
