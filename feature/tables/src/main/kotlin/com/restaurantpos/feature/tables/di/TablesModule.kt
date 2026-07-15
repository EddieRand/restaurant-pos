package com.restaurantpos.feature.tables.di

import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.usecase.CancelReservationUseCase
import com.restaurantpos.core.domain.usecase.CreateReservationUseCase
import com.restaurantpos.core.domain.usecase.MergeTablesUseCase
import com.restaurantpos.core.domain.usecase.SeatReservationUseCase
import com.restaurantpos.core.domain.usecase.SplitTableUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// ReservationRepository is provided by DatabaseModule (RoomReservationRepository)

@Module
@InstallIn(SingletonComponent::class)
object TablesModule {

    @Provides
    fun provideMergeTablesUseCase(
        tableRepo: TableRepository,
        orderRepo: OrderRepository,
    ): MergeTablesUseCase = MergeTablesUseCase(tableRepo, orderRepo)

    @Provides
    fun provideSplitTableUseCase(
        tableRepo: TableRepository,
        orderRepo: OrderRepository,
    ): SplitTableUseCase = SplitTableUseCase(tableRepo, orderRepo)

    @Provides
    fun provideCancelReservationUseCase(
        tableRepo: TableRepository,
        reservationRepo: ReservationRepository,
    ): CancelReservationUseCase = CancelReservationUseCase(reservationRepo, tableRepo)

    @Provides
    fun provideCreateReservationUseCase(
        tableRepo: TableRepository,
        reservationRepo: ReservationRepository,
        regionConfig: RegionConfig,
    ): CreateReservationUseCase = CreateReservationUseCase(tableRepo, reservationRepo, regionConfig)

    @Provides
    fun provideSeatReservationUseCase(
        reservationRepo: ReservationRepository,
        tableRepo: TableRepository,
        orderRepo: OrderRepository,
    ): SeatReservationUseCase = SeatReservationUseCase(reservationRepo, tableRepo, orderRepo)
}
