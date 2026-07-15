package com.restaurantpos.server

import com.restaurantpos.server.ai.*
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.model.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class AiWorkspaceRoutesTest {
    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:workspace_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("test-secret")
        transaction {
            MenuItemsTable.insert {
                it[id] = "item-1"
                it[names] = """{"zh":"宫保鸡丁"}"""
                it[priceMinorUnit] = 3_800
                it[taxRateId] = null
                it[categoryId] = "main"
                it[updatedAt] = 100
            }
            OrdersTable.insert {
                it[id] = "order-1"
                it[type] = "DINE_IN"
                it[sourceTerminalId] = "cashier-1"
                it[status] = "CLOSED"
                it[createdAt] = 1_500
                it[updatedAt] = 1_600
            }
            OrderItemsTable.insert {
                it[id] = "order-item-1"
                it[orderId] = "order-1"
                it[menuItemId] = "item-1"
                it[menuItemNameSnapshot] = """{"zh":"宫保鸡丁"}"""
                it[quantity] = 2
                it[unitPriceMinorUnit] = 3_800
                it[status] = "SERVED"
            }
        }
    }

    @Test
    fun `compound run persists events and price execution links audit to step`() = testApplication {
        val model = FakeWorkspaceModel(
            AiWorkspacePlan(
                listOf(
                    AiWorkspacePlannedStep(AiWorkspaceTools.OPERATING_INSIGHT, "分析今日经营", "分析今日经营"),
                    AiWorkspacePlannedStep(AiWorkspaceTools.REPORT_QUERY, "查询热销菜品", "今天卖得最好的菜", "TOP_ITEMS"),
                    AiWorkspacePlannedStep(AiWorkspaceTools.MENU_UPDATE_PRICE, "生成改价提案", "把宫保鸡丁涨价5元"),
                ),
            ),
        )
        val insight = insightService()
        val price = AiPriceAgentService(
            intentClient = AiPriceIntentClient { _, _, _, _ -> AiPriceIntent("宫保鸡丁", "INCREASE", amountMajorUnit = "5") },
            enabled = true,
            priceUpdateEnabled = true,
        )
        val workspace = AiWorkspaceService(model, insight, price, enabled = true, permissionChecker = { _, _ -> true })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val sessionResponse = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(CreateAiWorkspaceSessionRequest())
        }
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        val sessionId = sessionResponse.body<AiWorkspaceSessionDto>().sessionId
        val acceptedResponse = client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(
                AiWorkspaceMessageRequest(
                    sessionId,
                    message = "分析营业情况，并把宫保鸡丁涨价5元",
                    context = AiWorkspaceContextDto(1_000, 2_000, "/"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, acceptedResponse.status)
        val accepted = acceptedResponse.body<AiWorkspaceMessageAcceptedResponse>()

        val restored = awaitRun(client, sessionId)
        val run = restored.runs.single()
        assertEquals(accepted.messageId, run.messageId)
        assertEquals("COMPLETED", run.status)
        assertEquals(AiWorkspaceStepStatus.SUCCEEDED, run.steps[0].status)
        assertEquals("宫保鸡丁", run.steps[1].result!!.query!!.evidence.single().dimensionValue)
        assertEquals(2L, run.steps[1].result!!.query!!.evidence.single().numericValue)
        assertEquals(AiWorkspaceStepStatus.AWAITING_CONFIRMATION, run.steps[2].status)
        val proposal = run.steps[2].result!!.priceProposal!!
        assertEquals(4_300L, proposal.changes.single().newPriceMinorUnit)

        val executeResponse = client.post("/admin/ai/price-proposals/${proposal.proposalId}/execute") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(ExecuteAiPriceProposalRequest(true, "workspace-idem-0001"))
        }
        assertEquals(HttpStatusCode.OK, executeResponse.status)
        val afterExecute = client.get("/admin/ai/workspace/sessions/$sessionId") { adminAuth() }
            .body<AiWorkspaceSessionDto>()
        val executed = afterExecute.runs.single().steps[2]
        assertEquals(AiWorkspaceStepStatus.EXECUTED, executed.status)
        assertTrue(executed.result!!.execution!!.auditId.isNotBlank())

        val eventText = client.get("/admin/ai/workspace/runs/${accepted.runId}/events?afterSequence=0") { adminAuth() }
            .bodyAsText()
        assertTrue(eventText.contains("event: plan.created"))
        assertTrue(eventText.contains("event: step.awaiting_confirmation"))
        assertTrue(eventText.contains("event: step.executed"))
        assertTrue(eventText.contains("event: run.completed"))
    }

    @Test
    fun `expert and RBAC reduce planner tool visibility`() = testApplication {
        val model = FakeWorkspaceModel(AiWorkspacePlan(listOf(AiWorkspacePlannedStep(AiWorkspaceTools.HOW_TO_SEARCH, "查看帮助", "怎么登录"))))
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(model, insight, price, enabled = true, permissionChecker = { _, _ -> true })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            cashierAuth(); contentType(ContentType.Application.Json)
            setBody(CreateAiWorkspaceSessionRequest(AiWorkspaceExpert.PRODUCT_HELP))
        }.body<AiWorkspaceSessionDto>().sessionId

        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            cashierAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, AiWorkspaceExpert.PRODUCT_HELP, "怎么登录后台"))
        }
        awaitRun(client, sessionId, admin = false)
        assertEquals(setOf(AiWorkspaceTools.HOW_TO_SEARCH), model.lastAllowedTools)
    }

    @Test
    fun `planner provider failure is persisted at run level and replayed`() = testApplication {
        val failingModel = object : AiWorkspaceModelClient {
            override suspend fun plan(message: String, expert: AiWorkspaceExpert, context: AiWorkspaceContextDto, allowedTools: Set<String>): AiWorkspacePlan {
                throw AiProviderException("AI_RATE_LIMITED", "rate limited", true)
            }
            override suspend fun explainQuery(question: String, period: AiWorkspacePeriodDto, evidence: List<AiWorkspaceEvidenceDto>) = error("unused")
            override suspend fun answerHowTo(question: String, excerpts: List<AiHowToExcerpt>) = error("unused")
        }
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(failingModel, insight, price, enabled = true, permissionChecker = { _, _ -> true })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json); setBody(CreateAiWorkspaceSessionRequest())
        }.body<AiWorkspaceSessionDto>().sessionId
        val accepted = client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, message = "分析今天"))
        }.body<AiWorkspaceMessageAcceptedResponse>()
        val restored = awaitRun(client, sessionId)
        assertEquals("FAILED", restored.runs.single().status)
        assertEquals("AI_RATE_LIMITED", restored.runs.single().error?.code)
        val events = client.get("/admin/ai/workspace/runs/${accepted.runId}/events") { adminAuth() }.bodyAsText()
        assertTrue(events.contains("AI_RATE_LIMITED"))
    }

    @Test
    fun `tool permission is checked again immediately before execution`() = testApplication {
        val model = FakeWorkspaceModel(
            AiWorkspacePlan(listOf(AiWorkspacePlannedStep(AiWorkspaceTools.MENU_UPDATE_PRICE, "生成改价提案", "把宫保鸡丁涨价5元"))),
        )
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(model, insight, price, enabled = true, permissionChecker = { _, _ -> false })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json); setBody(CreateAiWorkspaceSessionRequest())
        }.body<AiWorkspaceSessionDto>().sessionId
        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, message = "把宫保鸡丁涨价5元"))
        }
        val step = awaitRun(client, sessionId).runs.single().steps.single()
        assertEquals(AiWorkspaceStepStatus.FAILED, step.status)
        assertEquals("AI_PERMISSION_DENIED", step.error?.code)
        assertTrue(step.proposalId == null)
    }

    @Test
    fun `natural language period overrides page context`() = testApplication {
        val fixedNow = Instant.parse("2026-07-16T06:30:00Z").toEpochMilli()
        val model = FakeWorkspaceModel(
            AiWorkspacePlan(
                listOf(AiWorkspacePlannedStep(AiWorkspaceTools.REPORT_QUERY, "查询昨日经营", "昨天的营业情况", "SUMMARY")),
            ),
        )
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(
            model,
            insight,
            price,
            enabled = true,
            permissionChecker = { _, _ -> true },
            now = { fixedNow },
        )
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json); setBody(CreateAiWorkspaceSessionRequest())
        }.body<AiWorkspaceSessionDto>().sessionId

        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(
                AiWorkspaceMessageRequest(
                    sessionId,
                    message = "分析昨天的营业情况",
                    context = AiWorkspaceContextDto(1_000, 2_000, "/dashboard"),
                ),
            )
        }

        val period = awaitRun(client, sessionId).runs.single().steps.single().result!!.query!!.period
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(fixedNow).atZone(zone).toLocalDate()
        assertEquals(today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), period.fromMs)
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli() - 1, period.toMs)
    }

    @Test
    fun `missing report period asks choices and selected answer continues original request`() = testApplication {
        val model = FakeWorkspaceModel(
            AiWorkspacePlan(listOf(AiWorkspacePlannedStep(AiWorkspaceTools.REPORT_QUERY, "查询经营数据", "营业情况", "SUMMARY"))),
        )
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(model, insight, price, enabled = true, permissionChecker = { _, _ -> true })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json); setBody(CreateAiWorkspaceSessionRequest())
        }.body<AiWorkspaceSessionDto>().sessionId

        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, message = "分析营业情况"))
        }
        val awaiting = awaitRun(client, sessionId).runs.single()
        assertEquals("AWAITING_CLARIFICATION", awaiting.status)
        assertEquals("你希望分析哪个时间范围？", awaiting.clarification!!.question)
        assertEquals(listOf("今天", "昨天", "近 7 天", "本月"), awaiting.clarification.options.map { it.label })
        assertTrue(awaiting.steps.isEmpty())

        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, message = "原始请求：分析营业情况\n补充确认：日期范围：今天\n请继续处理。"))
        }
        val completed = awaitRunCount(client, sessionId, 2).runs.last()
        assertEquals("COMPLETED", completed.status)
        assertTrue(completed.steps.single().result?.query != null)
    }

    @Test
    fun `planner ambiguity is persisted as selectable clarification without executing`() = testApplication {
        val clarification = AiWorkspaceClarification(
            "你要调整哪一道宫保鸡丁？",
            listOf(
                AiWorkspaceClarificationOption("dine_in", "堂食菜单", "菜品范围：堂食菜单的宫保鸡丁"),
                AiWorkspaceClarificationOption("takeaway", "外卖菜单", "菜品范围：外卖菜单的宫保鸡丁"),
            ),
        )
        val model = FakeWorkspaceModel(AiWorkspacePlan(emptyList(), clarification))
        val insight = insightService()
        val price = AiPriceAgentService(null, enabled = true, priceUpdateEnabled = true)
        val workspace = AiWorkspaceService(model, insight, price, enabled = true, permissionChecker = { _, _ -> true })
        application { configurePlugins(); configureAuth(); configureRouting(insight, price, workspace) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val sessionId = client.post("/admin/ai/workspace/sessions") {
            adminAuth(); contentType(ContentType.Application.Json); setBody(CreateAiWorkspaceSessionRequest())
        }.body<AiWorkspaceSessionDto>().sessionId
        client.post("/admin/ai/workspace/sessions/$sessionId/messages") {
            adminAuth(); contentType(ContentType.Application.Json)
            setBody(AiWorkspaceMessageRequest(sessionId, message = "把宫保鸡丁涨价5元"))
        }

        val run = awaitRun(client, sessionId).runs.single()
        assertEquals("AWAITING_CLARIFICATION", run.status)
        assertEquals(2, run.clarification!!.options.size)
        assertTrue(run.steps.isEmpty())
    }

    private suspend fun awaitRun(
        client: io.ktor.client.HttpClient,
        sessionId: String,
        admin: Boolean = true,
    ): AiWorkspaceSessionDto {
        repeat(80) {
            val session = client.get("/admin/ai/workspace/sessions/$sessionId") {
                if (admin) adminAuth() else cashierAuth()
            }.body<AiWorkspaceSessionDto>()
            if (session.runs.singleOrNull()?.status in setOf("COMPLETED", "FAILED", "AWAITING_CLARIFICATION")) return session
            delay(25)
        }
        error("workspace run did not finish")
    }

    private suspend fun awaitRunCount(
        client: io.ktor.client.HttpClient,
        sessionId: String,
        count: Int,
    ): AiWorkspaceSessionDto {
        repeat(80) {
            val session = client.get("/admin/ai/workspace/sessions/$sessionId") { adminAuth() }.body<AiWorkspaceSessionDto>()
            if (session.runs.size == count && session.runs.last().status in setOf("COMPLETED", "FAILED", "AWAITING_CLARIFICATION")) return session
            delay(25)
        }
        error("workspace run count did not reach $count")
    }

    private fun insightService(): AiInsightService {
        val metrics = AiInsightMetrics(
            1_000, 2_000, 1, 4_300, 4_300, 4_300, 2, 0, 0,
            mapOf("CASH" to 4_300), emptyList(), emptyList(), 0, 0,
        )
        val generated = AiGeneratedInsight(
            "营业稳定", "今日完成一笔订单。",
            listOf(AiGeneratedObservation("neutral", "订单已同步", "当前有一笔已结订单。", listOf("orderCount"))),
            listOf(
                AiGeneratedAction("high", "关注高峰", "保持出餐"),
                AiGeneratedAction("medium", "复盘菜品", "观察销量"),
                AiGeneratedAction("low", "检查折扣", "控制让利"),
            ),
        )
        return AiInsightService(AiInsightDataSource { _, _, _ -> metrics }, AiInsightClient { _, _ -> generated }, "test-model")
    }

    private class FakeWorkspaceModel(private val plan: AiWorkspacePlan) : AiWorkspaceModelClient {
        var lastAllowedTools: Set<String> = emptySet()
        override suspend fun plan(message: String, expert: AiWorkspaceExpert, context: AiWorkspaceContextDto, allowedTools: Set<String>): AiWorkspacePlan {
            lastAllowedTools = allowedTools
            return plan
        }
        override suspend fun explainQuery(question: String, period: AiWorkspacePeriodDto, evidence: List<AiWorkspaceEvidenceDto>) = "基于真实聚合数据的回答"
        override suspend fun answerHowTo(question: String, excerpts: List<AiHowToExcerpt>) =
            AiWorkspaceHowToAnswer("请按帮助资料完成登录。", excerpts.first().steps)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.adminAuth() {
        header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("admin-1", "admin")}")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.cashierAuth() {
        header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("cashier-1", "cashier")}")
    }
}
