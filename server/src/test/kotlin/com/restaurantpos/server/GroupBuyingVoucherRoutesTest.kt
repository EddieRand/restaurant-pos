package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.model.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class GroupBuyingVoucherRoutesTest {
    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:voucher_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("voucher-secret")
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configurePlugins(); configureAuth(); configureRouting() }
        block()
    }

    private fun client(builder: ApplicationTestBuilder) = builder.createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun token() = JwtConfig.issueToken("pos-1", "TERMINAL")
    private fun adminToken() = JwtConfig.issueToken("admin-1", "ADMIN")

    @Test
    fun `douyin demo voucher validates redeems idempotently and appears masked in admin ledger`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        val validate = c.post("/pos/group-buying-vouchers/validate") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(GroupBuyingVoucherValidateRequest("DOUYIN", "DY-DEMO-1001"))
        }
        assertEquals(HttpStatusCode.OK, validate.status)
        assertEquals(880L, validate.body<GroupBuyingVoucherDto>().faceValueMinorUnit)

        val request = GroupBuyingVoucherRedeemRequest(
            provider = "DOUYIN",
            code = "DY-DEMO-1001",
            orderId = "order-demo-1",
            operatorId = "cashier-1",
            requestedAmountMinorUnit = 756L,
            idempotencyKey = "idem-demo-1",
        )
        suspend fun redeem() = c.post("/pos/group-buying-vouchers/redeem") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val first = redeem()
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = first.body<GroupBuyingVoucherRedeemResponse>()
        assertEquals(756L, firstBody.redeemedAmountMinorUnit)
        assertFalse(firstBody.alreadyRedeemed)

        val replay = redeem()
        assertEquals(HttpStatusCode.OK, replay.status)
        assertTrue(replay.body<GroupBuyingVoucherRedeemResponse>().alreadyRedeemed)

        val ledger = c.get("/admin/group-buying-redemptions") { header(HttpHeaders.Authorization, "Bearer ${adminToken()}") }
        assertEquals(HttpStatusCode.OK, ledger.status)
        val rows = ledger.body<List<GroupBuyingRedemptionDto>>()
        assertEquals(1, rows.size)
        assertEquals("****1001", rows.single().maskedCode)
        assertEquals("order-demo-1", rows.single().orderId)
    }

    @Test
    fun `voucher cannot be consumed twice and idempotency key cannot change owner`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        suspend fun redeem(code: String, orderId: String, key: String) = c.post("/pos/group-buying-vouchers/redeem") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(GroupBuyingVoucherRedeemRequest("MEITUAN", code, orderId, "cashier-1", 500L, key))
        }

        assertEquals(HttpStatusCode.OK, redeem("MT-DEMO-1001", "order-1", "key-1").status)
        assertEquals(HttpStatusCode.Conflict, redeem("MT-DEMO-1001", "order-2", "key-2").status)

        // Same key with a different order must not replay another redemption.
        val conflict = redeem("MT-DEMO-1001", "order-2", "key-1")
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("VOUCHER_IDEMPOTENCY_CONFLICT", conflict.body<GroupBuyingVoucherErrorResponse>().code)
    }
}
