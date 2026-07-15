package com.restaurantpos.server

import com.restaurantpos.server.ai.AiGeneratedAction
import com.restaurantpos.server.ai.AiGeneratedInsight
import com.restaurantpos.server.ai.AiGeneratedObservation
import com.restaurantpos.server.ai.AiInsightClient
import com.restaurantpos.server.ai.AiInsightDataSource
import com.restaurantpos.server.ai.AiInsightMetrics
import com.restaurantpos.server.ai.AiInsightService
import com.restaurantpos.server.ai.AiProviderException
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.model.AiInsightErrorResponse
import com.restaurantpos.server.model.AiOperatingInsightRequest
import com.restaurantpos.server.model.AiOperatingInsightResponse
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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AiInsightRoutesTest {
    private val metrics = AiInsightMetrics(
        fromMs = 1_000,
        toMs = 2_000,
        orderCount = 2,
        grossRevenueMinorUnit = 12_000,
        netRevenueMinorUnit = 11_000,
        averageOrderValueMinorUnit = 5_500,
        guestCount = 4,
        totalDiscountMinorUnit = 1_000,
        totalRefundMinorUnit = 0,
        paymentMethodBreakdown = mapOf("CASH" to 11_000),
        topItems = emptyList(),
        peakHours = emptyList(),
        previousPeriodOrderCount = 1,
        previousPeriodNetRevenueMinorUnit = 4_000,
    )
    private val generated = AiGeneratedInsight(
        headline = "营业增长",
        summary = "本期净营收为 110 元。",
        observations = listOf(AiGeneratedObservation("positive", "订单增长", "订单数较上期增加。", listOf("orderCount"))),
        actions = listOf(
            AiGeneratedAction("high", "保持出餐速度", "承接高峰需求"),
            AiGeneratedAction("medium", "优化套餐", "提升客单价"),
            AiGeneratedAction("low", "复盘折扣", "控制让利"),
        ),
    )

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:aitest_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("test-secret")
    }

    @Test
    fun `admin with report permission receives validated insight`() = testApplication {
        val service = AiInsightService(AiInsightDataSource { _, _, _ -> metrics }, AiInsightClient { _, _ -> generated }, "deepseek-v4-flash")
        application { configurePlugins(); configureAuth(); configureRouting(service) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val response = client.post("/admin/ai/operating-insight") {
            header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("admin-1", "admin")}")
            contentType(ContentType.Application.Json)
            setBody(AiOperatingInsightRequest(1_000, 2_000, "zh-CN"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AiOperatingInsightResponse>()
        assertEquals(11_000, body.snapshot.netRevenueMinorUnit)
        assertEquals(3, body.actions.size)
        assertEquals("deepseek-v4-flash", body.model)
    }

    @Test
    fun `endpoint requires authentication and validates range`() = testApplication {
        val service = AiInsightService(AiInsightDataSource { _, _, _ -> metrics }, AiInsightClient { _, _ -> generated }, "deepseek-v4-flash")
        application { configurePlugins(); configureAuth(); configureRouting(service) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/admin/ai/operating-insight").status)

        val invalid = client.post("/admin/ai/operating-insight") {
            header(HttpHeaders.Authorization, "Bearer ${JwtConfig.issueToken("admin-1", "admin")}")
            contentType(ContentType.Application.Json)
            setBody(AiOperatingInsightRequest(2_000, 1_000, "zh-CN"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("AI_INVALID_REQUEST", invalid.body<AiInsightErrorResponse>().code)
    }

    @Test
    fun `service retries once and caches by data fingerprint`() = runTest {
        var calls = 0
        val client = AiInsightClient { _, _ ->
            calls++
            if (calls == 1) throw AiProviderException("AI_RATE_LIMITED", "retry", true)
            generated
        }
        val service = AiInsightService(AiInsightDataSource { _, _, _ -> metrics }, client, "deepseek-v4-flash")
        val first = service.generate(AiOperatingInsightRequest(1_000, 2_000))
        val second = service.generate(AiOperatingInsightRequest(1_000, 2_000))
        assertEquals(2, calls)
        assertEquals(first, second)
        assertTrue(first.observations.first().evidenceKeys.contains("orderCount"))
    }
}
