package com.restaurantpos.feature.auth.di

import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.domain.usecase.CheckPermissionUseCase
import com.restaurantpos.core.domain.usecase.LoginWithPinUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    fun provideLoginWithPinUseCase(userRepo: UserRepository): LoginWithPinUseCase =
        LoginWithPinUseCase(userRepo)

    @Provides
    fun provideCheckPermissionUseCase(repo: RolePermissionRepository): CheckPermissionUseCase =
        CheckPermissionUseCase(repo)
}
