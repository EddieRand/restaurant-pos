package com.restaurantpos.feature.report.di

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.domain.usecase.DailyReportUseCase
import com.restaurantpos.core.domain.usecase.ShiftReportUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ReportModule {

    @Provides
    fun provideShiftReportUseCase(
        orderRepo: OrderRepository,
        paymentRepo: PaymentRepository,
        reportRepo: ReportRepository,
    ): ShiftReportUseCase = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)

    @Provides
    fun provideDailyReportUseCase(
        orderRepo: OrderRepository,
        paymentRepo: PaymentRepository,
        reportRepo: ReportRepository,
    ): DailyReportUseCase = DailyReportUseCase(orderRepo, paymentRepo, reportRepo)
}
