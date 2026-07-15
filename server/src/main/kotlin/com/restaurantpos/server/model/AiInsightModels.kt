package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

@Serializable
data class AiOperatingInsightRequest(
    val fromMs: Long,
    val toMs: Long,
    val locale: String = "zh-CN",
)

@Serializable
data class AiInsightPeriodDto(
    val fromMs: Long,
    val toMs: Long,
)

@Serializable
data class AiInsightSnapshotDto(
    val orderCount: Int,
    val netRevenueMinorUnit: Long,
    val averageOrderValueMinorUnit: Long,
    val guestCount: Int,
)

@Serializable
data class AiInsightObservationDto(
    val severity: String,
    val title: String,
    val detail: String,
    val evidenceKeys: List<String> = emptyList(),
)

@Serializable
data class AiInsightActionDto(
    val priority: String,
    val title: String,
    val reason: String,
)

@Serializable
data class AiOperatingInsightResponse(
    val generatedAt: Long,
    val model: String,
    val period: AiInsightPeriodDto,
    val snapshot: AiInsightSnapshotDto,
    val headline: String,
    val summary: String,
    val observations: List<AiInsightObservationDto>,
    val actions: List<AiInsightActionDto>,
)

@Serializable
data class AiInsightErrorResponse(
    val code: String,
    val message: String,
)
