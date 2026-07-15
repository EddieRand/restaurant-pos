package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

/** Stable HTTP contract for the unified, controlled AI workspace. */
@Serializable
enum class AiWorkspaceExpert {
    AUTO,
    OPERATIONS,
    PRODUCT_HELP,
    MENU,
}

@Serializable
enum class AiWorkspaceStepKind {
    ANALYSIS,
    HOW_TO,
    ACTION,
}

@Serializable
enum class AiWorkspaceStepStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    AWAITING_CONFIRMATION,
    FAILED,
    SKIPPED,
    EXECUTED,
}

@Serializable
data class AiWorkspaceContextDto(
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val currentRoute: String = "/",
)

@Serializable
data class CreateAiWorkspaceSessionRequest(
    val expert: AiWorkspaceExpert = AiWorkspaceExpert.AUTO,
    val locale: String = "zh-CN",
)

@Serializable
data class AiWorkspaceMessageRequest(
    val sessionId: String,
    val expert: AiWorkspaceExpert = AiWorkspaceExpert.AUTO,
    val message: String,
    val locale: String = "zh-CN",
    val context: AiWorkspaceContextDto = AiWorkspaceContextDto(),
)

@Serializable
data class AiWorkspaceMessageAcceptedResponse(
    val sessionId: String,
    val messageId: String,
    val runId: String,
    val status: String = "QUEUED",
)

@Serializable
data class AiWorkspacePeriodDto(
    val fromMs: Long,
    val toMs: Long,
)

@Serializable
data class AiWorkspaceEvidenceDto(
    val key: String,
    val label: String,
    val numericValue: Long,
    /** MINOR_UNIT, COUNT, or BASIS_POINTS. */
    val unit: String,
    val dimensionValue: String? = null,
)

@Serializable
data class AiWorkspaceQueryResultDto(
    val answer: String,
    val period: AiWorkspacePeriodDto,
    val evidence: List<AiWorkspaceEvidenceDto>,
    val sourceTool: String,
)

@Serializable
data class AiWorkspaceHowToSourceDto(
    val documentId: String,
    val title: String,
    val section: String,
    val route: String? = null,
    val lastVerifiedAt: Long,
)

@Serializable
data class AiWorkspaceHowToResultDto(
    val answer: String,
    val steps: List<String>,
    val sources: List<AiWorkspaceHowToSourceDto>,
)

@Serializable
data class AiWorkspaceStepResultDto(
    val insight: AiOperatingInsightResponse? = null,
    val query: AiWorkspaceQueryResultDto? = null,
    val howTo: AiWorkspaceHowToResultDto? = null,
    val priceProposal: AiPriceProposalResponse? = null,
    val execution: ExecuteAiPriceProposalResponse? = null,
)

@Serializable
data class AiWorkspaceStepErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

@Serializable
data class AiWorkspaceStepDto(
    val stepId: String,
    val tool: String,
    val kind: AiWorkspaceStepKind,
    val status: AiWorkspaceStepStatus,
    val dependsOn: List<String> = emptyList(),
    val displayTitle: String,
    val result: AiWorkspaceStepResultDto? = null,
    val proposalId: String? = null,
    val error: AiWorkspaceStepErrorDto? = null,
)

@Serializable
data class AiWorkspaceMessageDto(
    val messageId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
)

@Serializable
data class AiWorkspaceRunDto(
    val runId: String,
    val messageId: String,
    val status: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val steps: List<AiWorkspaceStepDto> = emptyList(),
)

@Serializable
data class AiWorkspaceSessionSummaryDto(
    val sessionId: String,
    val title: String,
    val expert: AiWorkspaceExpert,
    val locale: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AiWorkspaceSessionDto(
    val sessionId: String,
    val title: String,
    val expert: AiWorkspaceExpert,
    val locale: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<AiWorkspaceMessageDto>,
    val runs: List<AiWorkspaceRunDto>,
)

@Serializable
data class AiWorkspaceSessionListResponse(
    val sessions: List<AiWorkspaceSessionSummaryDto>,
)

@Serializable
data class AiWorkspaceEventDto(
    val sequence: Long,
    val type: String,
    val occurredAt: Long,
    val runId: String,
    val step: AiWorkspaceStepDto? = null,
    val runStatus: String? = null,
)

@Serializable
data class AiWorkspaceErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

object AiWorkspaceTools {
    const val OPERATING_INSIGHT = "report.operating_insight"
    const val REPORT_QUERY = "report.query"
    const val HOW_TO_SEARCH = "product.howto_search"
    const val MENU_UPDATE_PRICE = "menu.update_price"
    val all = setOf(OPERATING_INSIGHT, REPORT_QUERY, HOW_TO_SEARCH, MENU_UPDATE_PRICE)
}

object AiWorkspaceEvidenceUnits {
    const val MINOR_UNIT = "MINOR_UNIT"
    const val COUNT = "COUNT"
    const val BASIS_POINTS = "BASIS_POINTS"
    val all = setOf(MINOR_UNIT, COUNT, BASIS_POINTS)
}
