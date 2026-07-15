package com.restaurantpos.server.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiTopItemMetric(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val revenueMinorUnit: Long,
)

@Serializable
data class AiPeakHourMetric(
    val hour: Int,
    val orderCount: Int,
)

@Serializable
data class AiInsightMetrics(
    val fromMs: Long,
    val toMs: Long,
    val orderCount: Int,
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val averageOrderValueMinorUnit: Long,
    val guestCount: Int,
    val totalDiscountMinorUnit: Long,
    val totalRefundMinorUnit: Long,
    val paymentMethodBreakdown: Map<String, Long>,
    val topItems: List<AiTopItemMetric>,
    val peakHours: List<AiPeakHourMetric>,
    val previousPeriodOrderCount: Int,
    val previousPeriodNetRevenueMinorUnit: Long,
    val currencyCode: String = "CNY",
    val minorUnitDigits: Int = 2,
    val orderCountChangeBasisPoints: Long? = null,
    val netRevenueChangeBasisPoints: Long? = null,
)

@Serializable
data class AiGeneratedInsight(
    val headline: String,
    val summary: String,
    val observations: List<AiGeneratedObservation>,
    val actions: List<AiGeneratedAction>,
)

@Serializable
data class AiGeneratedObservation(
    val severity: String,
    val title: String,
    val detail: String,
    val evidenceKeys: List<String> = emptyList(),
)

@Serializable
data class AiGeneratedAction(
    val priority: String,
    val title: String,
    val reason: String,
)

fun interface AiInsightClient {
    suspend fun generate(metrics: AiInsightMetrics, locale: String): AiGeneratedInsight
}

fun interface AiInsightDataSource {
    fun load(fromMs: Long, toMs: Long, locale: String): AiInsightMetrics
}

class AiProviderException(
    val code: String,
    message: String,
    val retryable: Boolean,
) : RuntimeException(message)

class AiNotConfiguredException : RuntimeException("DeepSeek API is not configured")
