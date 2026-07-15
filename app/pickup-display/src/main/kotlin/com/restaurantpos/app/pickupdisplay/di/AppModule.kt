package com.restaurantpos.app.pickupdisplay.di

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.usecase.TransferTableUseCase
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

    // :feature:tables 的 TablesViewModel 也在 Hilt 图里，需要满足其依赖
    @Provides
    fun provideTransferTableUseCase(
        orderRepo: OrderRepository,
        tableRepo: TableRepository,
    ): TransferTableUseCase = TransferTableUseCase(orderRepo, tableRepo)
}
