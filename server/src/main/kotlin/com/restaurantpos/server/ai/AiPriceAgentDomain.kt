package com.restaurantpos.server.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiPriceIntent(
    val targetName: String,
    val operation: String,
    /** Decimal major-unit string, for example "5.00". Never a floating-point value. */
    val amountMajorUnit: String? = null,
    /** Decimal percentage string, for example "10" or "2.5". */
    val percentage: String? = null,
)

fun interface AiPriceIntentClient {
    suspend fun parse(
        instruction: String,
        locale: String,
        currencyCode: String,
        minorUnitDigits: Int,
    ): AiPriceIntent
}

class AiAgentException(
    val code: String,
    override val message: String,
    val retryable: Boolean = false,
) : RuntimeException(message)
