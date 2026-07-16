package com.restaurantpos.server

import com.restaurantpos.server.ai.AiGrowthCopyGenerator
import com.restaurantpos.server.ai.AiGrowthService
import com.restaurantpos.server.ai.GrowthGeneratedCopy
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.AiActionAuditsTable
import com.restaurantpos.server.db.tables.CampaignsTable
import com.restaurantpos.server.db.tables.CouponsTable
import com.restaurantpos.server.model.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AiGrowthRoutesTest {
    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:growth_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("growth-secret")
    }

    @Test
    fun `briefing is cached and revised proposal executes idempotently`() = testApplication {
        var generations = 0
        val service = AiGrowthService(AiGrowthCopyGenerator { _, _ ->
            generations++
            GrowthGeneratedCopy("今日增长重点", "基于真实经营证据生成。", listOf("建议一", "建议二", "建议三"), "短视频文案")
        })
        application { configurePlugins(); configureAuth(); configureRouting(aiGrowthService = service) }
        val c = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val token = c.post("/auth/login/password") {
            contentType(ContentType.Application.Json)
            setBody(PasswordLoginRequest("admin@pos.local", "admin123"))
        }.body<LoginResponse>().token
        fun HttpRequestBuilder.auth() { header(HttpHeaders.Authorization, "Bearer $token") }

        val first = c.get("/admin/ai/growth/briefings/today") { auth() }.body<AiGrowthBriefingResponse>()
        val second = c.get("/admin/ai/growth/briefings/today") { auth() }.body<AiGrowthBriefingResponse>()
        assertEquals(first.briefingId, second.briefingId)
        assertEquals(1, generations)
        assertTrue(first.evidence.any { it.dataMode == AiGrowthDataMode.DEMO_SIGNAL })

        val proposal = c.post("/admin/ai/growth/proposals") {
            auth(); contentType(ContentType.Application.Json)
            setBody(CreateAiGrowthProposalRequest(500, 7, "INACTIVE_30_DAYS"))
        }.body<AiGrowthProposalResponse>()
        val revised = c.post("/admin/ai/growth/proposals/${proposal.proposalId}/revise") {
            auth(); contentType(ContentType.Application.Json)
            setBody(ReviseAiGrowthProposalRequest(fixedAmountMinorUnit = 800))
        }.body<AiGrowthProposalResponse>()
        assertNotEquals(proposal.proposalId, revised.proposalId)
        assertEquals(2, revised.version)

        val old = c.post("/admin/ai/growth/proposals/${proposal.proposalId}/execute") {
            auth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiGrowthProposalRequest(true, "old-key"))
        }
        assertEquals(HttpStatusCode.Conflict, old.status)

        suspend fun execute() = c.post("/admin/ai/growth/proposals/${revised.proposalId}/execute") {
            auth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiGrowthProposalRequest(true, "growth-idem-1"))
        }
        val executed = execute().body<ExecuteAiGrowthProposalResponse>()
        val replay = execute().body<ExecuteAiGrowthProposalResponse>()
        assertFalse(executed.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(executed.auditId, replay.auditId)
        transaction {
            assertEquals(1L, CouponsTable.selectAll().count())
            assertEquals(1L, CampaignsTable.selectAll().count())
            assertEquals("DRAFT", CampaignsTable.selectAll().single()[CampaignsTable.status])
            assertEquals(1L, AiActionAuditsTable.selectAll().count())
        }
    }
}
