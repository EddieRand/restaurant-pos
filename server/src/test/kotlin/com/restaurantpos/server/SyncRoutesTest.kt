package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.SyncLogTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.db.tables.TablesTable
import com.restaurantpos.server.model.CdsStateResponse
import com.restaurantpos.server.model.SyncPullResponse
import com.restaurantpos.server.model.TableSyncPullResponse
import com.restaurantpos.server.model.UserSyncPullResponse
import com.restaurantpos.server.model.SyncPushRequest
import com.restaurantpos.server.model.SyncPushResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SyncRoutesTest {

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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

    private fun validToken() = JwtConfig.issueToken("user-1", "ADMIN")

    @Test
    fun `health endpoint returns ok`() = testApp {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `sync push without token returns 401`() = testApp {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(
                id = UUID.randomUUID().toString(),
                entityType = "ORDER",
                entityId = "order-1",
                operation = "CREATE",
                payload = "{}",
                updatedAt = 1000L,
            ))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `sync push with valid token stores record`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val syncId = UUID.randomUUID().toString()

        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(
                id = syncId,
                entityType = "ORDER",
                entityId = "order-abc",
                operation = "CREATE",
                payload = """{"id":"order-abc"}""",
                updatedAt = 2000L,
            ))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPushResponse>()
        assertEquals("accepted", body.status)

        // Verify persisted
        val count = transaction {
            SyncLogTable.selectAll().count { it[SyncLogTable.entityId] == "order-abc" }
        }
        assertEquals(1, count)
    }

    @Test
    fun `sync push returns conflict when server has newer version`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val entityId = "order-conflict-${System.currentTimeMillis()}"

        // Push newer version first
        client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(
                id = UUID.randomUUID().toString(),
                entityType = "ORDER",
                entityId = entityId,
                operation = "CREATE",
                payload = """{"id":"$entityId","v":2}""",
                updatedAt = 5000L,
            ))
        }

        // Try to push older version
        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(
                id = UUID.randomUUID().toString(),
                entityType = "ORDER",
                entityId = entityId,
                operation = "UPDATE",
                payload = """{"id":"$entityId","v":1}""",
                updatedAt = 3000L,
            ))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `sync pull without token returns 401`() = testApp {
        val response = client.get("/sync/pull?since=0")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `sync pull returns only menu items updated after the watermark`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val oldId = "menu-old-${System.currentTimeMillis()}"
        val newId = "menu-new-${System.currentTimeMillis()}"

        transaction {
            MenuItemsTable.insert {
                it[id] = oldId
                it[names] = """{"en":"Old Item"}"""
                it[priceMinorUnit] = 1000L
                it[categoryId] = "cat-1"
                it[updatedAt] = 1_000L
            }
            MenuItemsTable.insert {
                it[id] = newId
                it[names] = """{"en":"New Item"}"""
                it[priceMinorUnit] = 2000L
                it[categoryId] = "cat-1"
                it[updatedAt] = 5_000L
            }
        }

        val response = client.get("/sync/pull?since=2000") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPullResponse>()
        assertEquals(1, body.menuItems.size)
        assertEquals(newId, body.menuItems.first().id)
    }

    @Test
    fun `sync pull returns orders updated after the watermark with their items`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val oldOrder = "order-old-${System.currentTimeMillis()}"
        val newOrder = "order-new-${System.currentTimeMillis()}"

        transaction {
            OrdersTable.insert {
                it[id] = oldOrder
                it[type] = "TAKEAWAY"
                it[sourceTerminalId] = "kiosk-1"
                it[status] = "PLACED"
                it[createdAt] = 500L
                it[updatedAt] = 1_000L
            }
            OrdersTable.insert {
                it[id] = newOrder
                it[type] = "TAKEAWAY"
                it[sourceTerminalId] = "kiosk-1"
                it[status] = "PLACED"
                it[pickupCode] = "7"
                it[fulfillmentStatus] = "READY_FOR_PICKUP"
                it[createdAt] = 4_000L
                it[updatedAt] = 5_000L
            }
            OrderItemsTable.insert {
                it[id] = "$newOrder-item1"
                it[orderId] = newOrder
                it[menuItemId] = "mi-1"
                it[menuItemNameSnapshot] = """{"en":"Burger"}"""
                it[quantity] = 2
                it[unitPriceMinorUnit] = 1500L
                it[status] = "PLACED"
            }
            // 旧订单的 item 不应被下发
            OrderItemsTable.insert {
                it[id] = "$oldOrder-item1"
                it[orderId] = oldOrder
                it[menuItemId] = "mi-2"
                it[menuItemNameSnapshot] = """{"en":"Salad"}"""
                it[quantity] = 1
                it[unitPriceMinorUnit] = 900L
                it[status] = "PLACED"
            }
        }

        val response = client.get("/sync/pull?since=2000") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SyncPullResponse>()
        assertEquals(1, body.orders.size)
        val pulled = body.orders.first()
        assertEquals(newOrder, pulled.id)
        assertEquals("7", pulled.pickupCode)
        assertEquals("READY_FOR_PICKUP", pulled.fulfillmentStatus)
        assertEquals(1, body.orderItems.size)
        assertEquals("$newOrder-item1", body.orderItems.first().id)
        assertEquals(2, body.orderItems.first().quantity)
    }

    @Test
    fun `sync users without token returns 401`() = testApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/sync/users").status)
    }

    @Test
    fun `sync users returns full staff set with pinHash`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        transaction {
            UsersTable.insert {
                it[id] = "u-waiter"
                it[displayName] = "孙浩"
                it[role] = "waiter"
                it[pinHash] = "hash-3333"
                it[isActive] = true
                it[createdAt] = 1_000L
            }
        }

        val response = client.get("/sync/users") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<UserSyncPullResponse>()
        val u = body.users.single { it.id == "u-waiter" }
        assertEquals("waiter", u.roleId)
        assertEquals("hash-3333", u.pinHash)
        assertTrue(u.isActive)
    }

    @Test
    fun `sync tables returns rows updated after watermark`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        transaction {
            TablesTable.insert {
                it[id] = "tbl-old"; it[name] = "T-old"; it[sectionId] = "indoor"
                it[status] = "AVAILABLE"; it[updatedAt] = 1_000L
            }
            TablesTable.insert {
                it[id] = "tbl-new"; it[name] = "T-new"; it[sectionId] = "indoor"
                it[status] = "OCCUPIED"; it[updatedAt] = 5_000L
            }
        }
        val response = client.get("/sync/tables?since=2000") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<TableSyncPullResponse>()
        assertEquals(1, body.tables.size)
        assertEquals("tbl-new", body.tables.first().id)
        assertEquals("OCCUPIED", body.tables.first().status)
    }

    @Test
    fun `table push then pull round-trips status with last-write-wins`() = testApp {
        val c = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        suspend fun pushTable(payload: String, updatedAt: Long) = c.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(UUID.randomUUID().toString(), "TABLE", "tbl-1", "UPDATE", payload, updatedAt))
        }

        pushTable("""{"id":"tbl-1","name":"T1","sectionId":"indoor","status":"OCCUPIED","updatedAt":1000}""", 1000L)
        // Stale update must not win
        pushTable("""{"id":"tbl-1","status":"AVAILABLE","updatedAt":500}""", 500L)

        val row = transaction {
            TablesTable.selectAll().where { TablesTable.id eq "tbl-1" }.single()
        }
        assertEquals("OCCUPIED", row[TablesTable.status])  // stale push ignored
        assertEquals("T1", row[TablesTable.name])          // partial payload preserved name
    }

    @Test
    fun `cds state defaults to welcome with no state`() = testApp {
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val res = client.get("/public/cds/state")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.body<CdsStateResponse>()
        assertEquals("WELCOME", body.phase)
        assertNull(body.order)
    }

    @Test
    fun `pushed cds phase drives public cds state with order snapshot`() = testApp {
        val c = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val orderId = "order-cds-${System.currentTimeMillis()}"
        transaction {
            OrdersTable.insert {
                it[id] = orderId
                it[type] = "DINE_IN"
                it[sourceTerminalId] = "cashier-1"
                it[status] = "PLACED"
                it[subtotalMinorUnit] = 3725
                it[discountMinorUnit] = 250
                it[taxTotalMinorUnit] = 263
                it[serviceChargeMinorUnit] = 150
                it[createdAt] = 1_000L
                it[updatedAt] = 1_000L
            }
            OrderItemsTable.insert {
                it[id] = "$orderId-i1"
                it[OrderItemsTable.orderId] = orderId
                it[menuItemId] = "mi-1"
                it[menuItemNameSnapshot] = """{"en-US":"Signature Combo"}"""
                it[quantity] = 2
                it[unitPriceMinorUnit] = 1199
                it[status] = "PLACED"
            }
        }

        // Cashier broadcasts the ORDER phase via the sync push pipeline
        val push = c.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${validToken()}")
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    UUID.randomUUID().toString(), "CDS_STATE", "cashier-1", "UPDATE",
                    """{"id":"cashier-1","terminalId":"cashier-1","orderId":"$orderId","phase":"ORDER","updatedAt":2000}""",
                    2000L,
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, push.status)

        val body = c.get("/public/cds/state").body<CdsStateResponse>()
        assertEquals("ORDER", body.phase)
        assertEquals("DINE_IN".let { "Dine In" }, body.order?.type)
        assertEquals(2, body.order?.items?.first()?.qty)
        assertEquals("Signature Combo", body.order?.items?.first()?.name)
        assertEquals(37.25, body.order?.totals?.subtotal)
        assertEquals(38.88, body.order?.totals?.total)
    }

    @Test
    fun `cds state currency and store name follow the region config`() = testApp {
        val c = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        transaction {
            SettingsTable.insert {
                it[key] = "regionConfig"
                it[value] = """{"currencySymbol":"€","currencyMinorDigits":2,"cdsConfig":{"displayName":"Cafe Demo","welcomeTitle":"Welkom!","showModifiers":false}}"""
            }
        }

        val body = c.get("/public/cds/state").body<CdsStateResponse>()
        assertEquals("€", body.currencySymbol)
        assertEquals(2, body.minorDigits)
        assertEquals("Cafe Demo", body.store.name)
        // Display config flows from cdsConfig; unspecified fields keep their defaults.
        assertEquals("Welkom!", body.config.welcomeTitle)
        assertEquals(false, body.config.showModifiers)
        assertEquals(true, body.config.showRunningTotal)
    }

    @Test
    fun `pin login with unknown user returns 401`() = testApp {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/auth/login/pin") {
            contentType(ContentType.Application.Json)
            setBody("""{"terminalId":"t1","pin":"9999"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
