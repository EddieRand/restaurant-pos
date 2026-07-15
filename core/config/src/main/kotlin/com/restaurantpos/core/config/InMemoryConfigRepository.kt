package com.restaurantpos.core.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryConfigRepository(
    initial: RegionConfig = DefaultRegionConfig,
) : ConfigRepository {

    private val _config = MutableStateFlow(initial)

    override val config: Flow<RegionConfig> = _config.asStateFlow()

    override fun current(): RegionConfig = _config.value

    override suspend fun update(config: RegionConfig) {
        _config.value = config
    }
}
