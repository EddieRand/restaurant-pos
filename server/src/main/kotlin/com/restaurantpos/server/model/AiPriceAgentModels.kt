package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

/** Public HTTP contract for the controlled AI menu-price write plane. */
@Serializable
data class AiPriceProposalRequest(
    val instruction: String,
    val locale: String = "zh-CN",
)

@Serializable
data class AiPriceProposalWarningDto(
    val code: String,
    val message: String,
)

@Serializable
data class AiPriceChangeDto(
    val itemId: String,
    val itemName: String,
    val oldPriceMinorUnit: Long,
    val newPriceMinorUnit: Long,
    val deltaMinorUnit: Long,
    /** Percentage change in basis points; null when the old price is zero. */
    val deltaPercentBasisPoints: Long? = null,
)

@Serializable
data class AiPriceProposalResponse(
    val proposalId: String,
    val status: String,
    val tool: String,
    val createdAt: Long,
    val expiresAt: Long,
    val requiresConfirmation: Boolean,
    val currencyCode: String,
    val minorUnitDigits: Int,
    val changes: List<AiPriceChangeDto>,
    val warnings: List<AiPriceProposalWarningDto> = emptyList(),
)

@Serializable
data class ExecuteAiPriceProposalRequest(
    val confirmed: Boolean,
    val idempotencyKey: String,
)

@Serializable
data class ExecuteAiPriceProposalResponse(
    val proposalId: String,
    val status: String,
    val executedAt: Long,
    val auditId: String,
    val idempotentReplay: Boolean,
)

@Serializable
data class AiAgentErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)
