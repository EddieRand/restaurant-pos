package com.restaurantpos.feature.kds.di

import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.usecase.BumpTicketUseCase
import com.restaurantpos.core.domain.usecase.RecallTicketUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object KdsModule {

    @Provides
    fun provideBumpTicketUseCase(repo: KitchenTicketRepository, orderRepo: OrderRepository): BumpTicketUseCase =
        BumpTicketUseCase(repo, orderRepo)

    @Provides
    fun provideRecallTicketUseCase(repo: KitchenTicketRepository): RecallTicketUseCase =
        RecallTicketUseCase(repo)
}
