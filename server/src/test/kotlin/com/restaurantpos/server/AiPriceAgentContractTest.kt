package com.restaurantpos.server

import com.restaurantpos.server.model.AiPriceChangeDto
import com.restaurantpos.server.model.AiPriceProposalResponse
import com.restaurantpos.server.model.ExecuteAiPriceProposalRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPriceAgentContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `proposal contract preserves integer money and server percentage`() {
        val proposal = AiPriceProposalResponse(
            proposalId = "proposal-1",
            status = "PROPOSED",
            tool = "menu.update_price",
            createdAt = 1_000,
            expiresAt = 301_000,
            requiresConfirmation = true,
            currencyCode = "CNY",
            minorUnitDigits = 2,
            changes = listOf(
                AiPriceChangeDto("item-1", "宫保鸡丁", 3_800, 4_300, 500, 1_316),
            ),
            warnings = emptyList(),
        )

        val encoded = json.encodeToString(proposal)
        val decoded = json.decodeFromString<AiPriceProposalResponse>(encoded)

        assertEquals(4_300L, decoded.changes.single().newPriceMinorUnit)
        assertEquals(1_316L, decoded.changes.single().deltaPercentBasisPoints)
        assertTrue(decoded.requiresConfirmation)
        assertTrue(encoded.contains("\"warnings\":[]"))
    }

    @Test
    fun `execute contract contains reference and confirmation but no price`() {
        val encoded = json.encodeToString(ExecuteAiPriceProposalRequest(true, "key-1"))

        assertTrue(encoded.contains("idempotencyKey"))
        assertTrue(encoded.contains("confirmed"))
        assertFalse(encoded.contains("price", ignoreCase = true))
        assertFalse(encoded.contains("itemId"))
    }
}
