package com.restaurantpos.server

import com.restaurantpos.server.model.AiWorkspaceEvidenceUnits
import com.restaurantpos.server.model.AiWorkspaceExpert
import com.restaurantpos.server.model.AiWorkspaceMessageRequest
import com.restaurantpos.server.model.AiWorkspaceStepKind
import com.restaurantpos.server.model.AiWorkspaceStepStatus
import com.restaurantpos.server.model.AiWorkspaceTools
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkspaceContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `message contract uses stable enum values and defaults`() {
        val request = json.decodeFromString<AiWorkspaceMessageRequest>(
            """{"sessionId":"session-1","message":"分析今天营业情况"}""",
        )

        assertEquals(AiWorkspaceExpert.AUTO, request.expert)
        assertEquals("zh-CN", request.locale)
        assertEquals("/", request.context.currentRoute)
    }

    @Test
    fun `workspace registry exposes only approved tools and evidence units`() {
        assertEquals(7, AiWorkspaceTools.all.size)
        assertTrue(AiWorkspaceTools.all.contains(AiWorkspaceTools.MENU_UPDATE_PRICE))
        assertTrue(AiWorkspaceTools.all.contains(AiWorkspaceTools.GROWTH_DAILY_BRIEFING))
        assertTrue(AiWorkspaceTools.all.contains(AiWorkspaceTools.CRM_COUPON_CAMPAIGN_PROPOSAL))
        assertEquals(setOf("MINOR_UNIT", "COUNT", "BASIS_POINTS"), AiWorkspaceEvidenceUnits.all)
        assertEquals(
            setOf("ANALYSIS", "HOW_TO", "ACTION"),
            AiWorkspaceStepKind.entries.map { it.name }.toSet(),
        )
        assertTrue(AiWorkspaceStepStatus.entries.contains(AiWorkspaceStepStatus.AWAITING_CONFIRMATION))
    }
}
