package com.restaurantpos.app.kds.di

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.routing.KitchenRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KdsAppModule {

    @Provides
    @Singleton
    fun provideConfigRepository(): ConfigRepository = InMemoryConfigRepository()

    @Provides
    fun provideRegionConfig(repo: ConfigRepository): RegionConfig = repo.current()

    @Provides
    @Singleton
    fun provideKitchenRouter(configRepo: ConfigRepository): KitchenRouter {
        val kdsCfg = configRepo.current().kdsConfig
        return KitchenRouter(
            routes = kdsCfg.categoryToStation,
            defaultStationId = kdsCfg.defaultStationId,
        )
    }
}
