package com.restaurantpos.core.config

import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    val config: Flow<RegionConfig>
    fun current(): RegionConfig
    suspend fun update(config: RegionConfig)
}
