package com.restaurantpos.feature.settings.di

import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.InMemoryConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideConfigRepository(): ConfigRepository = InMemoryConfigRepository()
}
