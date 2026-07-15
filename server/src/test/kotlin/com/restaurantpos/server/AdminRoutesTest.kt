package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.*
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

class AdminRoutesTest {

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:admintest_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("test-secret")
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

    private fun seedTable(id: String, name: String = id, sectionId: String = "main") {
        transaction {
            TablesTable.insert {
                it[TablesTable.id] = id
                it[TablesTable.name] = name
                it[TablesTable.sectionId] = sectionId
                it[capacity] = 4
                it[currentOrderId] = null
                it[status] = "AVAILABLE"
            }
        }
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    @Test
    fun `POST admin menu creates item and GET returns it`() = testApp {
        val c = client(this)
        val itemId = UUID.randomUUID().toString()

        val create = c.post("/admin/menu") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateMenuItemRequest(
                id = itemId,
                names = """{"en":"Burger","zh":"汉堡"}""",
                priceMinorUnit = 1200L,
                categoryId = "cat-1",
            ))
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/menu") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, list.status)
        val items = list.body<List<MenuItemDto>>()
        assertTrue(items.any { it.id == itemId && it.priceMinorUnit == 1200L })
    }

    @Test
    fun `PATCH admin menu updates isSoldOut`() = testApp {
        val c = client(this)
        val itemId = UUID.randomUUID().toString()
        transaction {
            MenuItemsTable.insert {
                it[id] = itemId
                it[names] = """{"en":"Salad"}"""
                it[priceMinorUnit] = 800L
                it[categoryId] = "cat-2"
                it[updatedAt] = System.currentTimeMillis()
            }
        }

        val patch = c.patch("/admin/menu/$itemId") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(UpdateMenuItemRequest(isSoldOut = true))
        }
        assertEquals(HttpStatusCode.OK, patch.status)

        val list = c.get("/admin/menu") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val items = list.body<List<MenuItemDto>>()
        assertTrue(items.first { it.id == itemId }.isSoldOut)
    }

    @Test
    fun `POST bulk-availability updates multiple items`() = testApp {
        val c = client(this)
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        transaction {
            listOf(id1, id2).forEach { itemId ->
                MenuItemsTable.insert {
                    it[id] = itemId
                    it[names] = """{"en":"Item"}"""
                    it[priceMinorUnit] = 500L
                    it[categoryId] = "cat-1"
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }

        val resp = c.post("/admin/menu/bulk-availability") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(BulkAvailabilityRequest(ids = listOf(id1, id2), isSoldOut = true))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `DELETE admin menu removes item`() = testApp {
        val c = client(this)
        val itemId = UUID.randomUUID().toString()
        transaction {
            MenuItemsTable.insert {
                it[id] = itemId
                it[names] = """{"en":"ToDelete"}"""
                it[priceMinorUnit] = 100L
                it[categoryId] = "cat-1"
                it[updatedAt] = System.currentTimeMillis()
            }
        }

        val del = c.delete("/admin/menu/$itemId") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, del.status)

        val list = c.get("/admin/menu") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val items = list.body<List<MenuItemDto>>()
        assertFalse(items.any { it.id == itemId })
    }

    // ── Report ────────────────────────────────────────────────────────────────

    @Test
    fun `GET admin reports shift returns correct aggregates`() = testApp {
        val c = client(this)
        val now = System.currentTimeMillis()
        transaction {
            repeat(3) { i ->
                OrdersTable.insert {
                    it[id] = "rpt-order-$i"
                    it[type] = "DINE_IN"
                    it[status] = "CLOSED"
                    it[sourceTerminalId] = "t1"
                    it[subtotalMinorUnit] = 1000L
                    it[taxTotalMinorUnit] = 100L
                    it[serviceChargeMinorUnit] = 50L
                    it[tipMinorUnit] = 0L
                    it[discountMinorUnit] = 0L
                    it[guestCount] = 2
                    it[createdAt] = now - 1000
                    it[updatedAt] = now - 1000
                }
            }
        }

        val resp = c.get("/admin/reports/shift?from=0&to=${now + 1000}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val report = resp.body<ShiftReportDto>()
        assertEquals(3, report.orderCount)
        assertEquals(6, report.totalGuestCount)
        // grossRevenue = (1000+100+50)*3 = 3450
        assertEquals(3450L, report.grossRevenueMinorUnit)
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @Test
    fun `GET admin orders returns paginated list`() = testApp {
        val c = client(this)
        val now = System.currentTimeMillis()
        transaction {
            repeat(5) { i ->
                OrdersTable.insert {
                    it[id] = "list-order-$i"
                    it[type] = "DINE_IN"
                    it[status] = "CLOSED"
                    it[sourceTerminalId] = "t1"
                    it[subtotalMinorUnit] = 500L
                    it[guestCount] = 1
                    it[createdAt] = now - i * 1000
                    it[updatedAt] = now - i * 1000
                }
            }
        }

        val resp = c.get("/admin/orders?pageSize=3&page=0") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.body<OrderListResponse>()
        assertEquals(3, body.orders.size)
        assertTrue(body.total >= 5)
    }

    @Test
    fun `GET admin orders id returns 404 for missing order`() = testApp {
        val c = client(this)
        val resp = c.get("/admin/orders/nonexistent") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `POST admin orders reject QR changes pending confirmation order to rejected`() = testApp {
        val c = client(this)
        val orderId = "qr-reject-${UUID.randomUUID()}"
        val itemId = "qr-item-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        transaction {
            OrdersTable.insert {
                it[id] = orderId
                it[type] = "DINE_IN"
                it[tableId] = "T9"
                it[status] = "PENDING_CONFIRMATION"
                it[sourceTerminalId] = "qr:table-9"
                it[subtotalMinorUnit] = 1200L
                it[guestCount] = 2
                it[createdAt] = now
                it[updatedAt] = now
            }
            OrderItemsTable.insert {
                it[OrderItemsTable.id] = itemId
                it[OrderItemsTable.orderId] = orderId
                it[menuItemId] = "menu-1"
                it[menuItemNameSnapshot] = """{"en":"Noodles"}"""
                it[quantity] = 1
                it[unitPriceMinorUnit] = 1200L
                it[OrderItemsTable.status] = "PENDING_CONFIRMATION"
            }
        }

        val reject = c.post("/admin/orders/$orderId/reject-qr") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, reject.status)

        val detail = c.get("/admin/orders/$orderId") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<OrderDetailDto>()
        assertEquals("REJECTED", detail.order.status)
        assertEquals("qr:table-9", detail.order.sourceTerminalId)
        assertEquals("REJECTED", detail.items.first().status)
    }

    // ── QR Ordering ───────────────────────────────────────────────────────────

    @Test
    fun `admin QR ordering config and codes round trip`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        seedTable("T12", name = "12 号桌")

        val config = QrOrderingConfigDto(
            enabled = true,
            supportedOrderTypes = listOf("DINE_IN", "TAKEAWAY"),
            menuOnlyMode = false,
            paymentTiming = "PAY_AFTER_SUBMIT",
            firePolicy = "STAFF_CONFIRM",
            customerIdentityPolicy = "BY_ORDER_TYPE",
        )
        val putConfig = c.put("/admin/qr-ordering/config") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(config)
        }
        assertEquals(HttpStatusCode.OK, putConfig.status)

        val getConfig = c.get("/admin/qr-ordering/config") {
            header(HttpHeaders.Authorization, auth)
        }
        assertEquals("PAY_AFTER_SUBMIT", getConfig.body<QrOrderingConfigDto>().paymentTiming)

        val code = "table-${UUID.randomUUID()}"
        val create = c.post("/admin/qr-ordering/codes") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(CreateQrCodeRequest(code = code, scope = "TABLE", tableId = "T12"))
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("T12", create.body<QrCodeDto>().tableId)

        val patch = c.patch("/admin/qr-ordering/codes/$code") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(UpdateQrCodeRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.OK, patch.status)
        assertFalse(patch.body<QrCodeDto>().enabled)

        val list = c.get("/admin/qr-ordering/codes") {
            header(HttpHeaders.Authorization, auth)
        }.body<List<QrCodeDto>>()
        assertTrue(list.any { it.code == code && !it.enabled })

        val delete = c.delete("/admin/qr-ordering/codes/$code") {
            header(HttpHeaders.Authorization, auth)
        }
        assertEquals(HttpStatusCode.OK, delete.status)
    }

    @Test
    fun `table binding endpoint generates QR for unbound table`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        seedTable("T21", name = "21 号桌", sectionId = "hall-a")

        val before = c.get("/admin/qr-ordering/table-bindings") {
            header(HttpHeaders.Authorization, auth)
        }.body<TableQrBindingResponse>()
        val unbound = before.sections.flatMap { it.tables }.first { it.tableId == "T21" }
        assertNull(unbound.currentQr)

        val generated = c.post("/admin/qr-ordering/tables/T21/qr") {
            header(HttpHeaders.Authorization, auth)
        }
        assertEquals(HttpStatusCode.OK, generated.status)
        val binding = generated.body<TableQrBindingDto>()
        assertEquals("T21", binding.tableId)
        assertNotNull(binding.currentQr)
        assertTrue(binding.customerUrl!!.contains("/qr/?code="))
    }

    @Test
    fun `reset table QR disables old code and new code stays public`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        seedTable("T22", name = "22 号桌")

        val first = c.post("/admin/qr-ordering/tables/T22/qr") {
            header(HttpHeaders.Authorization, auth)
        }.body<TableQrBindingDto>()
        val oldCode = first.currentQr!!.code

        val reset = c.post("/admin/qr-ordering/tables/T22/qr/reset") {
            header(HttpHeaders.Authorization, auth)
        }
        assertEquals(HttpStatusCode.OK, reset.status)
        val after = reset.body<TableQrBindingDto>()
        val newCode = after.currentQr!!.code
        assertNotEquals(oldCode, newCode)
        assertEquals(1, after.disabledCodeCount)

        val oldContext = c.get("/public/qr/$oldCode")
        assertEquals(HttpStatusCode.NotFound, oldContext.status)

        val newContext = c.get("/public/qr/$newCode")
        assertEquals(HttpStatusCode.OK, newContext.status)
        assertEquals("T22", newContext.body<PublicQrContextDto>().tableId)
    }

    @Test
    fun `rebind physical QR points code to target table`() = testApp {
        val c = client(this)
        val auth = "Bearer ${token()}"
        seedTable("TA", name = "A 桌")
        seedTable("TB", name = "B 桌")

        val original = c.post("/admin/qr-ordering/tables/TA/qr") {
            header(HttpHeaders.Authorization, auth)
        }.body<TableQrBindingDto>()
        val code = original.currentQr!!.code

        val rebound = c.post("/admin/qr-ordering/codes/$code/rebind") {
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(RebindQrCodeRequest(tableId = "TB"))
        }
        assertEquals(HttpStatusCode.OK, rebound.status)
        assertEquals("TB", rebound.body<TableQrBindingDto>().tableId)

        val context = c.get("/public/qr/$code")
        assertEquals(HttpStatusCode.OK, context.status)
        assertEquals("TB", context.body<PublicQrContextDto>().tableId)
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @Test
    fun `POST admin users creates user and GET returns it`() = testApp {
        val c = client(this)
        val userId = UUID.randomUUID().toString()

        val create = c.post("/admin/users") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreatePosUserRequest(id = userId, displayName = "Alice", role = "CASHIER", pin = "1234"))
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/users") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val users = list.body<List<PosUserDto>>()
        assertTrue(users.any { it.id == userId && it.displayName == "Alice" })
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Test
    fun `PUT and GET admin settings round-trips value`() = testApp {
        val c = client(this)
        val value = """{"currencyCode":"USD","minorDigits":2}"""

        val put = c.put("/admin/settings/regionConfig") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(SettingDto(key = "regionConfig", value = value))
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val get = c.get("/admin/settings/regionConfig") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, get.status)
        val setting = get.body<SettingDto>()
        assertEquals(value, setting.value)
    }

    @Test
    fun `legacy region config key maps to canonical setting`() = testApp {
        val c = client(this)
        val value = """{"currencyCode":"AED","currencyMinorDigits":2}"""

        val put = c.put("/admin/settings/region-config") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(SettingDto(key = "region-config", value = value))
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val getCanonical = c.get("/admin/settings/regionConfig") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, getCanonical.status)
        val canonical = getCanonical.body<SettingDto>()
        assertEquals("regionConfig", canonical.key)
        assertEquals(value, canonical.value)

        val getLegacy = c.get("/admin/settings/region-config") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, getLegacy.status)
        val legacy = getLegacy.body<SettingDto>()
        assertEquals("regionConfig", legacy.key)
        assertEquals(value, legacy.value)
    }

    // ── Coupons ───────────────────────────────────────────────────────────────

    @Test
    fun `POST admin coupons creates and GET returns coupon`() = testApp {
        val c = client(this)
        val couponId = UUID.randomUUID().toString()

        val create = c.post("/admin/coupons") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateCouponRequest(
                id = couponId, code = "SAVE10", type = "FIXED",
                value = 1000L, expiresAt = Long.MAX_VALUE,
            ))
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/coupons") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val coupons = list.body<List<CouponDto>>()
        assertTrue(coupons.any { it.id == couponId && it.code == "SAVE10" })
    }

    // ── Combos ────────────────────────────────────────────────────────────────

    @Test
    fun `POST admin combos creates with components and GET returns them`() = testApp {
        val c = client(this)
        val comboId = UUID.randomUUID().toString()

        val create = c.post("/admin/combos") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateComboRequest(
                id = comboId,
                names = """{"en":"Set A"}""",
                comboPriceMinorUnit = 2500L,
                components = listOf(
                    ComboComponentRequest(id = UUID.randomUUID().toString(), menuItemId = "item-1", quantity = 1),
                    ComboComponentRequest(id = UUID.randomUUID().toString(), menuItemId = "item-2", quantity = 2),
                )
            ))
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/combos") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val combos = list.body<List<ComboDto>>()
        val combo = combos.first { it.id == comboId }
        assertEquals(2500L, combo.comboPriceMinorUnit)
        assertEquals(2, combo.components.size)
    }

    @Test
    fun `routes require JWT authentication`() = testApp {
        val c = client(this)
        val noAuthResp = c.get("/admin/menu")
        assertEquals(HttpStatusCode.Unauthorized, noAuthResp.status)
    }
}
