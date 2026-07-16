package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

/** Stable P0 contract for Growth Adviser. Platform trend and ad signals are demo-only. */
@Serializable
enum class AiGrowthDataMode { REAL, AI_GENERATED, DEMO_SIGNAL }

@Serializable
enum class AiGrowthProposalType { COUPON_CAMPAIGN }

@Serializable
data class AiGrowthEvidenceDto(
    val key: String,
    val label: String,
    val numericValue: Long? = null,
    val textValue: String? = null,
    val unit: String,
    val dataMode: AiGrowthDataMode,
    val source: String,
)

@Serializable
data class AiGrowthExpectedImpactDto(
    val title: String,
    val detail: String,
    val dataMode: AiGrowthDataMode,
)

@Serializable
data class AiGrowthEditableParamsDto(
    val fixedAmountMinorUnit: Long,
    val validDays: Int,
    val targetSegment: String,
)

@Serializable
data class AiGrowthBriefingResponse(
    val briefingId: String,
    val businessDate: String,
    val generatedAt: Long,
    val dataFingerprint: String,
    val headline: String,
    val summary: String,
    val evidence: List<AiGrowthEvidenceDto>,
    val suggestions: List<String>,
    val contentDraft: String? = null,
    val demoSignalNotice: String = "演示信号，不代表抖音官方数据",
)

@Serializable
data class AiGrowthProposalResponse(
    val proposalId: String,
    val type: AiGrowthProposalType,
    val dataMode: AiGrowthDataMode,
    val evidence: List<AiGrowthEvidenceDto>,
    val expectedImpact: AiGrowthExpectedImpactDto,
    val editableParams: AiGrowthEditableParamsDto,
    val expiresAt: Long,
    val requiresConfirmation: Boolean = true,
    val version: Int,
)

@Serializable
data class CreateAiGrowthProposalRequest(
    val fixedAmountMinorUnit: Long,
    val validDays: Int,
    val targetSegment: String,
)

@Serializable
data class ReviseAiGrowthProposalRequest(
    val fixedAmountMinorUnit: Long? = null,
    val validDays: Int? = null,
    val targetSegment: String? = null,
)

@Serializable
data class ExecuteAiGrowthProposalRequest(
    val confirmed: Boolean,
    val idempotencyKey: String,
)

@Serializable
data class ExecuteAiGrowthProposalResponse(
    val proposalId: String,
    val auditId: String,
    val couponId: String,
    val campaignId: String,
    val idempotentReplay: Boolean,
    val executedAt: Long,
)

@Serializable
data class AiGrowthErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

object AiGrowthPermissions {
    const val CAMPAIGN_MANAGE = "crm.campaign.manage"
}

object AiGrowthErrorCodes {
    const val PERMISSION_DENIED = "GROWTH_PERMISSION_DENIED"
    const val NOT_FOUND = "GROWTH_PROPOSAL_NOT_FOUND"
    const val EXPIRED = "GROWTH_PROPOSAL_EXPIRED"
    const val STALE = "GROWTH_PROPOSAL_STALE"
    const val INVALID_PARAMS = "GROWTH_INVALID_PARAMS"
    const val IDEMPOTENCY_CONFLICT = "GROWTH_IDEMPOTENCY_CONFLICT"
    const val ALREADY_EXECUTED = "GROWTH_ALREADY_EXECUTED"
}
