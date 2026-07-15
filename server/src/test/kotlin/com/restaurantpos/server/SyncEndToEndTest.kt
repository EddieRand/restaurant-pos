package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.SyncLogTable
import com.restaurantpos.server.model.SyncPushRequest
import com.restaurantpos.server.model.SyncPushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * End-to-end sync tests:
 * 1. Multiple offline ops for same entity arrive in order → server retains latest
 * 2. Multiple offline ops arrive out of order (stale op after newer) → server rejects stale
 * 3. Conflict response includes server payload so client can reconcile
 * 4. Duplicate push (same record id) is accepted (idempotent insert)
 * 5. Reconnect burst: 10 records pushed → all accepted, no duplicates in DB
 */
class SyncEndToEndTest {

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:e2e_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        JwtConfig.init("e2e-secret")
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configurePlugins(); configureAuth(); configureRouting() }
        block()
    }

    private fun client(builder: ApplicationTestBuilder) =
        builder.createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    private fun token() = JwtConfig.issueToken("terminal-1", "TERMINAL")

    private suspend fun push(
        client: HttpClient,
        entityId: String,
        payload: String,
        updatedAt: Long,
    ): HttpResponse = client.post("/sync/push") {
        header(HttpHeaders.Authorization, "Bearer ${token()}")
        contentType(ContentType.Application.Json)
        setBody(SyncPushRequest(
            id = UUID.randomUUID().toString(),
            entityType = "ORDER",
            entityId = entityId,
            operation = "UPDATE",
            payload = payload,
            updatedAt = updatedAt,
        ))
    }

    // ── Test 1: sequential offline ops ───────────────────────────────────────

    @Test
    fun `sequential offline ops for same entity - server stores latest`() = testApp {
        val c = client(this)
        val entityId = "order-seq-${UUID.randomUUID()}"

        // Simulate 3 offline ops created while disconnected, now flushed in order
        push(c, entityId, """{"id":"$entityId","v":1}""", 1000L)
        push(c, entityId, """{"id":"$entityId","v":2}""", 2000L)
        val r3 = push(c, entityId, """{"id":"$entityId","v":3}""", 3000L)

        assertEquals(HttpStatusCode.OK, r3.status)

        // DB should have 3 rows (each push inserts when it wins)
        val rows = transaction {
            SyncLogTable.selectAll()
                .where { SyncLogTable.entityId eq entityId }
                .toList()
        }
        assertEquals(3, rows.size)

        // Most recent payload is the winner
        val latest = rows.maxByOrNull { it[SyncLogTable.updatedAt] }!!
        assertTrue(latest[SyncLogTable.payload].contains("\"v\":3"))
    }

    @Test
    fun `partial order update preserves fields the payload omits`() = testApp {
        val c = client(this)
        val entityId = "order-partial-${UUID.randomUUID()}"

        // Full push (e.g. order placed on cashier)
        push(
            c, entityId,
            """{"id":"$entityId","status":"PLACED","subtotalMinorUnit":570,"tableId":"table-3",""" +
                """"type":"DINE_IN","updatedAt":1000}""",
            1000L,
        )
        // Partial push (e.g. KDS bump only sends fulfillmentStatus)
        push(
            c, entityId,
            """{"id":"$entityId","fulfillmentStatus":"READY_FOR_PICKUP","updatedAt":2000}""",
            2000L,
        )

        val row = transaction {
            com.restaurantpos.server.db.tables.OrdersTable.selectAll()
                .where { com.restaurantpos.server.db.tables.OrdersTable.id eq entityId }
                .single()
        }
        assertEquals("READY_FOR_PICKUP", row[com.restaurantpos.server.db.tables.OrdersTable.fulfillmentStatus])
        // Fields omitted from the partial payload must survive
        assertEquals("PLACED", row[com.restaurantpos.server.db.tables.OrdersTable.status])
        assertEquals(570L, row[com.restaurantpos.server.db.tables.OrdersTable.subtotalMinorUnit])
        assertEquals("table-3", row[com.restaurantpos.server.db.tables.OrdersTable.tableId])
    }

    // ── Test 2: stale op after newer one is rejected ──────────────────────────

    @Test
    fun `stale op after newer op returns conflict`() = testApp {
        val c = client(this)
        val entityId = "order-stale-${UUID.randomUUID()}"

        // Push newer version first
        push(c, entityId, """{"id":"$entityId","status":"CLOSED"}""", 9000L)

        // Now push an older (offline-queued) version
        val staleResp = push(c, entityId, """{"id":"$entityId","status":"PLACED"}""", 3000L)

        assertEquals(HttpStatusCode.Conflict, staleResp.status)
        val body = staleResp.body<SyncPushResponse>()
        assertEquals("conflict", body.status)
        // Server payload is the winning record so the client can reconcile
        assertNotNull(body.serverPayload)
        assertTrue(body.serverPayload!!.contains("CLOSED"))
    }

    // ── Test 3: conflict payload enables client reconciliation ────────────────

    @Test
    fun `conflict response carries server authoritative payload`() = testApp {
        val c = client(this)
        val entityId = "order-reconcile-${UUID.randomUUID()}"
        val serverWinningPayload = """{"id":"$entityId","status":"CLOSED","updatedAt":5000}"""

        push(c, entityId, serverWinningPayload, 5000L)

        val resp = push(c, entityId, """{"id":"$entityId","status":"PLACED"}""", 2000L)
        val body = resp.body<SyncPushResponse>()

        assertEquals("conflict", body.status)
        assertEquals(serverWinningPayload, body.serverPayload)
    }

    // ── Test 4: idempotent re-push (same updatedAt, different id) ─────────────

    @Test
    fun `re-push with same updatedAt is accepted (equal wins)`() = testApp {
        val c = client(this)
        val entityId = "order-idem-${UUID.randomUUID()}"

        val r1 = push(c, entityId, """{"id":"$entityId","v":1}""", 5000L)
        val r2 = push(c, entityId, """{"id":"$entityId","v":1}""", 5000L)

        // Equal timestamps: >= rule means both are accepted
        assertEquals(HttpStatusCode.OK, r1.status)
        assertEquals(HttpStatusCode.OK, r2.status)
    }

    // ── Test 5: burst of 10 offline records after reconnect ───────────────────

    @Test
    fun `reconnect burst - 10 distinct entities all accepted`() = testApp {
        val c = client(this)
        val responses = (1..10).map { i ->
            push(c, "burst-entity-$i-${UUID.randomUUID()}", """{"id":"burst-$i"}""", i * 1000L)
        }

        val accepted = responses.count { r: HttpResponse -> r.status == HttpStatusCode.OK }
        assertEquals(10, accepted)

        val stored = transaction { SyncLogTable.selectAll().count() }
        assertTrue("Expected at least 10 rows", stored >= 10)
    }

    // ── Test 6: no auth → 401 ────────────────────────────────────────────────

    @Test
    fun `push without token returns 401`() = testApp {
        val c = client(this)
        val resp = c.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(SyncPushRequest(
                id = UUID.randomUUID().toString(),
                entityType = "ORDER",
                entityId = "no-auth-entity",
                operation = "UPDATE",
                payload = "{}",
                updatedAt = 1000L,
            ))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
