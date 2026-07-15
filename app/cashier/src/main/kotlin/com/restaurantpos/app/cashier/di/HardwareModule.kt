package com.restaurantpos.app.cashier.di

import android.content.Context
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.hardware.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    /**
     * Uses SunmiPrinter on real SUNMI hardware (detected by manufacturer string),
     * falls back to MockPrinter on emulators / non-SUNMI devices.
     */
    @Provides
    @Singleton
    fun providePrinterPort(@ApplicationContext context: Context): PrinterPort {
        val isSunmi = android.os.Build.MANUFACTURER.equals("sunmi", ignoreCase = true)
        return if (isSunmi) SunmiPrinter(context) else MockPrinter()
    }

    @Provides
    @Singleton
    fun provideCashDrawerPort(): CashDrawerPort = MockCashDrawer()

    @Provides
    fun providePrintReceiptUseCase(
        orderRepo: OrderRepository,
        printer: PrinterPort,
        regionConfig: RegionConfig,
    ): PrintReceiptUseCase = PrintReceiptUseCase(orderRepo, printer, regionConfig)

    @Provides
    fun providePrintKitchenTicketUseCase(
        orderRepo: OrderRepository,
        printer: PrinterPort,
        router: KitchenRouter,
    ): PrintKitchenTicketUseCase = PrintKitchenTicketUseCase(orderRepo, printer, router)
}
