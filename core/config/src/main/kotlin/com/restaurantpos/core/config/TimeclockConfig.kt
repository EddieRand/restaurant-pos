package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

@Serializable
data class TimeclockConfig(
    val enabled: Boolean = false,
    val requireClockInForOrders: Boolean = false,
)
