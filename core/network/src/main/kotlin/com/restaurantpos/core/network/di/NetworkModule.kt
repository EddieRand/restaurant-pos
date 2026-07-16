package com.restaurantpos.core.network.di

import com.restaurantpos.core.domain.repository.GiftCardRepository
import com.restaurantpos.core.domain.repository.GroupBuyingVoucherRepository
import com.restaurantpos.core.domain.repository.TimeclockRepository
import com.restaurantpos.core.network.BuildConfig
import com.restaurantpos.core.network.HttpGiftCardApi
import com.restaurantpos.core.network.HttpGroupBuyingVoucherApi
import com.restaurantpos.core.network.HttpTimeclockApi
import com.restaurantpos.core.network.HttpKitchenTicketPullPort
import com.restaurantpos.core.network.HttpMenuPullPort
import com.restaurantpos.core.network.HttpOrderPullPort
import com.restaurantpos.core.network.HttpUserPullPort
import com.restaurantpos.core.network.HttpTablePullPort
import com.restaurantpos.core.network.HttpCustomerPullPort
import com.restaurantpos.core.network.HttpPadConfigPullPort
import com.restaurantpos.core.network.HttpRegionFormatPullPort
import com.restaurantpos.core.network.HttpPermissionPullPort
import com.restaurantpos.core.network.HttpRemoteSyncPort
import com.restaurantpos.core.network.SharedPrefsTokenStore
import com.restaurantpos.core.network.TerminalAuthService
import com.restaurantpos.core.network.TokenStore
import com.restaurantpos.core.sync.KitchenTicketPullPort
import com.restaurantpos.core.sync.MenuPullPort
import com.restaurantpos.core.sync.OrderPullPort
import com.restaurantpos.core.sync.UserPullPort
import com.restaurantpos.core.sync.TablePullPort
import com.restaurantpos.core.sync.CustomerPullPort
import com.restaurantpos.core.sync.PadConfigPullPort
import com.restaurantpos.core.sync.RegionFormatPullPort
import com.restaurantpos.core.sync.PermissionPullPort
import com.restaurantpos.core.sync.RemoteSyncPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: SharedPrefsTokenStore): TokenStore

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }

        @Provides
        @Singleton
        fun provideRemoteSyncPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): RemoteSyncPort = HttpRemoteSyncPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = {
                // Use stored JWT; fall back to empty string (server will return 401, outbox retries)
                tokenStore.getToken() ?: ""
            },
        )

        @Provides
        @Singleton
        fun provideMenuPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): MenuPullPort = HttpMenuPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideKitchenTicketPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): KitchenTicketPullPort = HttpKitchenTicketPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun providePadConfigPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): PadConfigPullPort = HttpPadConfigPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideRegionFormatPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): RegionFormatPullPort = HttpRegionFormatPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun providePermissionPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): PermissionPullPort = HttpPermissionPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideGiftCardRepository(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): GiftCardRepository = HttpGiftCardApi(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideGroupBuyingVoucherRepository(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): GroupBuyingVoucherRepository = HttpGroupBuyingVoucherApi(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideOrderPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): OrderPullPort = HttpOrderPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideUserPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): UserPullPort = HttpUserPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideTablePullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): TablePullPort = HttpTablePullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideCustomerPullPort(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): CustomerPullPort = HttpCustomerPullPort(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideTimeclockRepository(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): TimeclockRepository = HttpTimeclockApi(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            authToken = { tokenStore.getToken() ?: "" },
        )

        @Provides
        @Singleton
        fun provideTerminalAuthService(
            client: OkHttpClient,
            tokenStore: TokenStore,
        ): TerminalAuthService = TerminalAuthService(
            baseUrl = BuildConfig.SERVER_BASE_URL,
            client = client,
            tokenStore = tokenStore,
        )
    }
}
