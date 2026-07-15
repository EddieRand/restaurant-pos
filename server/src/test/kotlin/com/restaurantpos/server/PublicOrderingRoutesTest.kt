package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.db.tables.QrCodesTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.model.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PublicOrderingRoutesTest {

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:qr_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("qr-secret")
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configurePlugins()
            configureAuth()
            configureRouting()
        }
        block()
    }

    private fun client(builder: ApplicationTestBuilder) =
        builder.createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private fun token() = JwtConfig.issueToken("admin-1", "ADMIN")

    private fun seedQr(code: String, scope: String, tableId: String? = null) {
        val now = System.currentTimeMillis()
        transaction {
            QrCodesTable.insert {
                it[QrCodesTable.code] = code
                it[QrCodesTable.scope] = scope
                it[QrCodesTable.tableId] = tableId
                it[enabled] = true
                it[expiresAt] = null
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    private fun seedMenu(itemId: String = "item-${UUID.randomUUID()}"): String {
        transaction {
            MenuItemsTable.insert {
                it[id] = itemId
                it[names] = """{"en":"Noodles"}"""
                it[priceMinorUnit] = 1200L
                it[taxRateId] = null
                it[categoryId] = "main"
                it[course] = 1
                it[isSoldOut] = false
                it[imageUrl] = null
                it[allergens] = ""
                it[updatedAt] = System.currentTimeMillis()
            }
        }
        return itemId
    }

    @Test
    fun `menu QR is read-only and rejects session creation`() = testApp {
        val c = client(this)
        val code = "menu-${UUID.randomUUID()}"
        seedQr(code, "MENU")

        val context = c.get("/public/qr/$code")
        assertEquals(HttpStatusCode.OK, context.status)
        assertTrue(context.body<PublicQrContextDto>().menuOnly)

        val create = c.post("/public/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateQrSessionRequest(code = code))
        }
        assertEquals(HttpStatusCode.BadRequest, create.status)
    }

    @Test
    fun `table QR joins the same active shared session`() = testApp {
        val c = client(this)
        val code = "table-${UUID.randomUUID()}"
        seedQr(code, "TABLE", tableId = "T1")

        val first = c.post("/public/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateQrSessionRequest(code = code, orderType = "DINE_IN"))
        }
        assertEquals(HttpStatusCode.Created, first.status)
        val s1 = first.body<QrSessionDto>()

        val second = c.post("/public/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateQrSessionRequest(code = code, orderType = "DINE_IN"))
        }
        assertEquals(HttpStatusCode.Created, second.status)
        val s2 = second.body<QrSessionDto>()

        assertEquals(s1.id, s2.id)
        assertEquals("T1", s2.tableId)
        assertNotNull(s2.sessionToken)
    }

    @Test
    fun `dine-in QR submit waits for staff confirmation then admin confirms`() = testApp {
        val c = client(this)
        val code = "dine-${UUID.randomUUID()}"
        val itemId = seedMenu()
        seedQr(code, "TABLE", tableId = "T7")

        val session = c.post("/public/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateQrSessionRequest(code = code, orderType = "DINE_IN"))
        }.body<QrSessionDto>()

        val cart = c.post("/public/sessions/${session.id}/cart") {
            header("X-QR-Session-Token", session.sessionToken!!)
            contentType(ContentType.Application.Json)
            setBody(UpdateQrCartRequest(items = listOf(QrCartItemDto(itemId, 2))))
        }
        assertEquals(HttpStatusCode.OK, cart.status)

        val submit = c.post("/public/sessions/${session.id}/submit") {
            header("X-QR-Session-Token", session.sessionToken!!)
        }
        assertEquals(HttpStatusCode.Created, submit.status)
        val submitted = submit.body<SubmitQrOrderResponse>()
        assertEquals("PENDING_CONFIRMATION", submitted.status)
        assertTrue(submitted.requiresStaffConfirmation)

        val confirm = c.post("/admin/orders/${submitted.orderId}/confirm-qr") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, confirm.status)

        val order = c.get("/public/orders/${submitted.orderId}").body<OrderDetailDto>()
        assertEquals("PLACED", order.order.status)
        assertEquals(2400L, order.order.subtotalMinorUnit)
    }

    @Test
    fun `pay before submit requires successful mock payment`() = testApp {
        val c = client(this)
        val code = "pickup-${UUID.randomUUID()}"
        val itemId = seedMenu()
        seedQr(code, "PICKUP")
        transaction {
            SettingsTable.insert {
                it[key] = "qrOrderingConfig"
                it[value] = """{"paymentTiming":"PAY_BEFORE_SUBMIT","firePolicy":"AUTO_FIRE"}"""
            }
        }

        val session = c.post("/public/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateQrSessionRequest(code = code, orderType = "TAKEAWAY", customerInfo = CustomerInfoDto(name = "A")))
        }.body<QrSessionDto>()
        c.post("/public/sessions/${session.id}/cart") {
            header("X-QR-Session-Token", session.sessionToken!!)
            contentType(ContentType.Application.Json)
            setBody(UpdateQrCartRequest(items = listOf(QrCartItemDto(itemId, 1))))
        }

        val rejected = c.post("/public/sessions/${session.id}/submit") {
            header("X-QR-Session-Token", session.sessionToken!!)
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)

        val intent = c.post("/public/payments/mock-intents") {
            contentType(ContentType.Application.Json)
            setBody(CreateMockPaymentIntentRequest(sessionId = session.id))
        }.body<MockPaymentIntentDto>()
        assertEquals(1200L, intent.amountMinorUnit)

        val confirmed = c.post("/public/payments/mock-intents/${intent.id}/confirm")
        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("SUCCEEDED", confirmed.body<MockPaymentIntentDto>().status)

        val submit = c.post("/public/sessions/${session.id}/submit") {
            header("X-QR-Session-Token", session.sessionToken!!)
        }
        assertEquals(HttpStatusCode.Created, submit.status)
        val submitted = submit.body<SubmitQrOrderResponse>()
        assertEquals("PLACED", submitted.status)
        assertFalse(submitted.requiresStaffConfirmation)
    }
}
