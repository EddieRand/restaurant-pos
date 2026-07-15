package com.restaurantpos.server.ai

import com.restaurantpos.server.model.AiWorkspaceContextDto
import com.restaurantpos.server.model.AiWorkspaceEvidenceDto
import com.restaurantpos.server.model.AiWorkspaceExpert
import com.restaurantpos.server.model.AiWorkspaceHowToSourceDto
import com.restaurantpos.server.model.AiWorkspacePeriodDto
import kotlinx.serialization.Serializable

@Serializable
data class AiWorkspacePlannedStep(
    val tool: String,
    val displayTitle: String,
    val instruction: String,
    val queryType: String? = null,
    /** One-based indexes of earlier steps. */
    val dependsOn: List<Int> = emptyList(),
)

@Serializable
data class AiWorkspacePlan(
    val steps: List<AiWorkspacePlannedStep>,
    val clarification: String? = null,
)

interface AiWorkspaceModelClient {
    suspend fun plan(
        message: String,
        expert: AiWorkspaceExpert,
        context: AiWorkspaceContextDto,
        allowedTools: Set<String>,
    ): AiWorkspacePlan

    suspend fun explainQuery(
        question: String,
        period: AiWorkspacePeriodDto,
        evidence: List<AiWorkspaceEvidenceDto>,
    ): String

    suspend fun answerHowTo(
        question: String,
        excerpts: List<AiHowToExcerpt>,
    ): AiWorkspaceHowToAnswer
}

data class AiHowToExcerpt(
    val source: AiWorkspaceHowToSourceDto,
    val text: String,
    val steps: List<String>,
)

@Serializable
data class AiWorkspaceHowToAnswer(
    val answer: String,
    val steps: List<String>,
)

class AiWorkspaceException(
    val code: String,
    override val message: String,
    val retryable: Boolean = false,
) : RuntimeException(message)
