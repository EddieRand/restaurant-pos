package com.restaurantpos.server

import com.restaurantpos.server.ai.AiPriceAgentService
import com.restaurantpos.server.ai.AiPriceIntent
import com.restaurantpos.server.ai.AiPriceIntentClient
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.AiMutationAuditsTable
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.model.AiAgentErrorResponse
import com.restaurantpos.server.model.AiPriceProposalRequest
import com.restaurantpos.server.model.AiPriceProposalResponse
import com.restaurantpos.server.model.ExecuteAiPriceProposalRequest
import com.restaurantpos.server.model.ExecuteAiPriceProposalResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AiPriceAgentRoutesTest {
    private var clock = 10_000L

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:ai_price_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("test-secret")
        clock = 10_000L
        seedMenuItem("item-1", """{"zh":"宫保鸡丁","en":"Kung Pao Chicken"}""", 3_800L, 100L)
    }

    @Test
    fun `proposal executes once and same idempotency key replays original audit`() = testApplication {
        val service = service(AiPriceIntent("宫保鸡丁", "INCREASE", amountMajorUnit = "5"))
        application { configurePlugins(); configureAuth(); configureRouting(aiPriceAgentService = service) }
        val client = jsonClient(this)

        val proposal = createProposal(client)
        assertEquals(4_300L, proposal.changes.single().newPriceMinorUnit)
        assertEquals(500L, proposal.changes.single().deltaMinorUnit)
        assertEquals(1_316L, proposal.changes.single().deltaPercentBasisPoints)

        val first = execute(client, proposal.proposalId, "idem-key-0001")
        assertFalse(first.idempotentReplay)
        val replay = execute(client, proposal.proposalId, "idem-key-0001")
        assertTrue(replay.idempotentReplay)
        assertEquals(first.auditId, replay.auditId)
        val alreadyExecuted = client.post("/admin/ai/price-proposals/${proposal.proposalId}/execute") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, "different-idem-key"))
        }
        assertEquals(HttpStatusCode.Conflict, alreadyExecuted.status)
        assertEquals("AI_PROPOSAL_ALREADY_EXECUTED", alreadyExecuted.body<AiAgentErrorResponse>().code)
        transaction {
            assertEquals(4_300L, MenuItemsTable.selectAll().where { MenuItemsTable.id eq "item-1" }.single()[MenuItemsTable.priceMinorUnit])
            assertEquals(1L, AiMutationAuditsTable.selectAll().count())
        }
    }

    @Test
    fun `execute rejects stale proposal without overwriting human change`() = testApplication {
        val service = service(AiPriceIntent("宫保鸡丁", "SET", amountMajorUnit = "42"))
        application { configurePlugins(); configureAuth(); configureRouting(aiPriceAgentService = service) }
        val client = jsonClient(this)
        val proposal = createProposal(client)
        transaction {
            MenuItemsTable.update({ MenuItemsTable.id eq "item-1" }) {
                it[priceMinorUnit] = 4_100L
                it[updatedAt] = 101L
            }
        }

        val response = client.post("/admin/ai/price-proposals/${proposal.proposalId}/execute") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, "idem-key-stale"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("AI_PROPOSAL_STALE", response.body<AiAgentErrorResponse>().code)
        transaction {
            assertEquals(4_100L, MenuItemsTable.selectAll().where { MenuItemsTable.id eq "item-1" }.single()[MenuItemsTable.priceMinorUnit])
            assertEquals(0L, AiMutationAuditsTable.selectAll().count())
        }
    }

    @Test
    fun `expired proposal and second idempotency owner are rejected`() = testApplication {
        val service = service(AiPriceIntent("宫保鸡丁", "INCREASE", percentage = "10"))
        application { configurePlugins(); configureAuth(); configureRouting(aiPriceAgentService = service) }
        val client = jsonClient(this)
        val firstProposal = createProposal(client)
        execute(client, firstProposal.proposalId, "shared-idem-key")

        transaction {
            MenuItemsTable.update({ MenuItemsTable.id eq "item-1" }) { it[updatedAt] = 20_000L }
        }
        val secondProposal = createProposal(client)
        val conflict = client.post("/admin/ai/price-proposals/${secondProposal.proposalId}/execute") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, "shared-idem-key"))
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("AI_IDEMPOTENCY_CONFLICT", conflict.body<AiAgentErrorResponse>().code)

        clock += 5 * 60_000L
        val expired = client.post("/admin/ai/price-proposals/${secondProposal.proposalId}/execute") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, "expired-idem-key"))
        }
        assertEquals(HttpStatusCode.Gone, expired.status)
        assertEquals("AI_PROPOSAL_EXPIRED", expired.body<AiAgentErrorResponse>().code)
    }

    @Test
    fun `agent returns stable auth permission disabled and ambiguous errors`() = testApplication {
        val ambiguousService = service(AiPriceIntent("鸡", "SET", amountMajorUnit = "20"))
        seedMenuItem("item-2", """{"zh":"辣子鸡"}""", 2_000L, 200L)
        application { configurePlugins(); configureAuth(); configureRouting(aiPriceAgentService = ambiguousService) }
        val client = jsonClient(this)

        val unauthorized = client.post("/admin/ai/price-proposals")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertEquals("AI_UNAUTHORIZED", unauthorized.body<AiAgentErrorResponse>().code)

        val malformed = client.post("/admin/ai/price-proposals") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody("{")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("AI_INVALID_REQUEST", malformed.body<AiAgentErrorResponse>().code)

        val cashier = client.post("/admin/ai/price-proposals") {
            header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("cashier-1", "cashier")}")
            contentType(ContentType.Application.Json)
            setBody(AiPriceProposalRequest("改价"))
        }
        assertEquals(HttpStatusCode.Forbidden, cashier.status)
        assertEquals("AI_PERMISSION_DENIED", cashier.body<AiAgentErrorResponse>().code)

        val ambiguous = client.post("/admin/ai/price-proposals") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiPriceProposalRequest("把鸡改成20元"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, ambiguous.status)
        assertEquals("AI_TARGET_AMBIGUOUS", ambiguous.body<AiAgentErrorResponse>().code)
    }

    @Test
    fun `disabled feature does not call provider`() = testApplication {
        var providerCalls = 0
        val service = AiPriceAgentService(
            intentClient = AiPriceIntentClient { _, _, _, _ -> providerCalls++; AiPriceIntent("宫保鸡丁", "SET", "40") },
            enabled = false,
            priceUpdateEnabled = true,
        )
        application { configurePlugins(); configureAuth(); configureRouting(aiPriceAgentService = service) }
        val client = jsonClient(this)
        val response = client.post("/admin/ai/price-proposals") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiPriceProposalRequest("改成40元"))
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("AI_AGENT_DISABLED", response.body<AiAgentErrorResponse>().code)
        assertEquals(0, providerCalls)
    }

    private fun service(intent: AiPriceIntent) = AiPriceAgentService(
        intentClient = AiPriceIntentClient { _, _, _, _ -> intent },
        enabled = true,
        priceUpdateEnabled = true,
        now = { clock },
    )

    private fun seedMenuItem(id: String, names: String, price: Long, updatedAt: Long) {
        transaction {
            MenuItemsTable.insert {
                it[MenuItemsTable.id] = id
                it[MenuItemsTable.names] = names
                it[priceMinorUnit] = price
                it[categoryId] = "main"
                it[MenuItemsTable.updatedAt] = updatedAt
            }
        }
    }

    private fun jsonClient(builder: ApplicationTestBuilder) = builder.createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun createProposal(client: io.ktor.client.HttpClient): AiPriceProposalResponse {
        val response = client.post("/admin/ai/price-proposals") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiPriceProposalRequest("请调整宫保鸡丁价格"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private suspend fun execute(client: io.ktor.client.HttpClient, proposalId: String, key: String): ExecuteAiPriceProposalResponse {
        val response = client.post("/admin/ai/price-proposals/$proposalId/execute") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, key))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.adminAuth() {
        header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("admin-1", "admin")}")
    }
}
