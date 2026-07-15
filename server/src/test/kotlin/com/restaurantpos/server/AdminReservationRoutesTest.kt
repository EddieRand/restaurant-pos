package com.restaurantpos.server

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.DatabaseFactory
import com.restaurantpos.server.db.tables.*
import com.restaurantpos.server.routes.*
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

class AdminReservationRoutesTest {

    @Before
    fun setup() {
        DatabaseFactory.initWithUrl("jdbc:h2:mem:reservationtest_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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

    private fun seedShift(
        id: String = UUID.randomUUID().toString(),
        name: String = "Dinner",
        startTime: String = "18:00",
        endTime: String = "21:00",
        daysOfWeek: String = "",
        maxCovers: Int = 10,
        slotIntervalMinutes: Int = 30,
    ): String {
        transaction {
            ShiftsTable.insert {
                it[ShiftsTable.id] = id; it[ShiftsTable.name] = name
                it[ShiftsTable.startTime] = startTime; it[ShiftsTable.endTime] = endTime
                it[ShiftsTable.daysOfWeek] = daysOfWeek
                it[ShiftsTable.maxCovers] = maxCovers; it[ShiftsTable.coverGoal] = maxCovers
                it[ShiftsTable.defaultTurnTimeMinutes] = 90
                it[ShiftsTable.slotIntervalMinutes] = slotIntervalMinutes
                it[ShiftsTable.bookingCutoffMinutes] = 60; it[ShiftsTable.enabled] = true
            }
        }
        return id
    }

    private fun seedReservation(
        resId: String = UUID.randomUUID().toString(),
        resDate: String = "2026-06-10",
        resTime: String = "18:00",
        resPartySize: Int = 2,
        resStatus: String = "PENDING",
        resShiftId: String? = null,
    ): String {
        transaction {
            ReservationsTable.insert {
                it[ReservationsTable.id] = resId; it[customerName] = "Guest $resId"
                it[phone] = "13800000000"; it[customerId] = null
                it[ReservationsTable.partySize] = resPartySize
                it[ReservationsTable.date] = resDate; it[ReservationsTable.time] = resTime
                it[tableId] = null; it[ReservationsTable.status] = resStatus
                it[notes] = null; it[internalNotes] = null
                it[ReservationsTable.shiftId] = resShiftId; it[bookingSource] = "ONLINE"
                it[guestTags] = ""; it[confirmationCode] = "CODE0001"
                it[estimatedDurationMinutes] = 90; it[depositAmount] = null
                it[createdAt] = System.currentTimeMillis()
            }
        }
        return resId
    }

    // ── Reservations ──────────────────────────────────────────────────────────

    @Test
    fun `POST admin reservations creates entry with auto-generated confirmation code`() = testApp {
        val c = client(this)
        val rid = UUID.randomUUID().toString()

        val create = c.post("/admin/reservations") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateReservationRequest(
                    id = rid, customerName = "张伟", phone = "13800138001",
                    partySize = 4, date = "2026-06-10", time = "18:00",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        val items = list.body<List<ReservationDto>>()
        val created = items.first { it.id == rid }
        assertEquals("张伟", created.customerName)
        assertEquals("PENDING", created.status)
        assertNotNull(created.confirmationCode)
        assertEquals(8, created.confirmationCode!!.length)
        assertEquals(created.confirmationCode, created.confirmationCode!!.uppercase())
    }

    @Test
    fun `POST admin reservations with malformed body fails deserialization`() = testApp {
        // Note: the route performs no explicit field validation — a body missing required
        // fields fails at JSON deserialization and is caught by the generic Throwable
        // StatusPages handler (Application.kt), which responds 500, not 400/422.
        // This test documents the current behavior; tightening it to 400 would require
        // adding either field validation in the route or a ContentTransformationException
        // handler in StatusPages.
        val c = client(this)
        val resp = c.post("/admin/reservations") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"customerName":"张伟"}""")
        }
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
    }

    @Test
    fun `GET admin reservations filters by date status and shiftId`() = testApp {
        val c = client(this)
        val shiftId = seedShift()
        seedReservation(resDate = "2026-06-10", resStatus = "PENDING", resShiftId = shiftId)
        seedReservation(resDate = "2026-06-10", resStatus = "CONFIRMED", resShiftId = null)
        seedReservation(resDate = "2026-06-11", resStatus = "PENDING", resShiftId = shiftId)

        val byDate = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertEquals(2, byDate.size)
        assertTrue(byDate.all { it.date == "2026-06-10" })

        val byStatus = c.get("/admin/reservations?date=2026-06-10&status=CONFIRMED") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertEquals(1, byStatus.size)
        assertEquals("CONFIRMED", byStatus.first().status)

        val byShift = c.get("/admin/reservations?shiftId=$shiftId") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertEquals(2, byShift.size)
        assertTrue(byShift.all { it.shiftId == shiftId })
    }

    @Test
    fun `PATCH admin reservations updates status table assignment and deposit`() = testApp {
        val c = client(this)
        val rid = seedReservation(resStatus = "PENDING")
        val tableId = "table-1"
        val seatedAt = System.currentTimeMillis()

        val patch = c.patch("/admin/reservations/$rid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateReservationRequest(
                    status = "SEATED", tableId = tableId, seatedAt = seatedAt, depositPaid = true,
                )
            )
        }
        assertEquals(HttpStatusCode.OK, patch.status)

        val list = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        val updated = list.first { it.id == rid }
        assertEquals("SEATED", updated.status)
        assertEquals(tableId, updated.tableId)
        assertEquals(seatedAt, updated.seatedAt)
        assertTrue(updated.depositPaid)
    }

    @Test
    fun `PATCH admin reservations on unknown id returns not found`() = testApp {
        val c = client(this)
        val resp = c.patch("/admin/reservations/does-not-exist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(UpdateReservationRequest(status = "CONFIRMED"))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `DELETE admin reservations removes entry`() = testApp {
        val c = client(this)
        val rid = seedReservation()

        val del = c.delete("/admin/reservations/$rid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.NoContent, del.status)

        val list = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertTrue(list.none { it.id == rid })
    }

    @Test
    fun `POST and PATCH admin reservations roundtrip guest tags`() = testApp {
        val c = client(this)
        val rid = UUID.randomUUID().toString()

        c.post("/admin/reservations") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateReservationRequest(
                    id = rid, customerName = "陈静", phone = "13600136001",
                    partySize = 2, date = "2026-06-10", time = "18:30",
                    guestTags = listOf("VIP", "BIRTHDAY"),
                )
            )
        }

        var list = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertEquals(listOf("VIP", "BIRTHDAY"), list.first { it.id == rid }.guestTags)

        c.patch("/admin/reservations/$rid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(UpdateReservationRequest(guestTags = listOf("FIRST_TIME")))
        }

        list = c.get("/admin/reservations?date=2026-06-10") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ReservationDto>>()
        assertEquals(listOf("FIRST_TIME"), list.first { it.id == rid }.guestTags)
    }

    // ── Waitlist ──────────────────────────────────────────────────────────────

    @Test
    fun `POST admin waitlist creates entry with WAITING status and requestedAt`() = testApp {
        val c = client(this)
        val wid = UUID.randomUUID().toString()
        val before = System.currentTimeMillis()

        val create = c.post("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateWaitlistRequest(
                    id = wid, customerName = "孙小姐", phone = "13900139001",
                    partySize = 3, quotedWaitMinutes = 25,
                )
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val after = System.currentTimeMillis()

        val list = c.get("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>()
        val entry = list.first { it.id == wid }
        assertEquals("WAITING", entry.status)
        assertTrue(entry.requestedAt in before..after)
    }

    @Test
    fun `POST admin waitlist notify transitions to NOTIFIED and sets 15 minute expiry`() = testApp {
        val c = client(this)
        val wid = UUID.randomUUID().toString()
        c.post("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateWaitlistRequest(id = wid, customerName = "周先生", phone = "13800138002", partySize = 2))
        }

        val before = System.currentTimeMillis()
        val notify = c.post("/admin/waitlist/$wid/notify") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, notify.status)
        val after = System.currentTimeMillis()

        val entry = c.get("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>().first { it.id == wid }

        assertEquals("NOTIFIED", entry.status)
        assertNotNull(entry.notifiedAt)
        assertNotNull(entry.expiresAt)
        assertTrue(entry.notifiedAt!! in before..after)
        val expectedExpiry = entry.notifiedAt!! + 15 * 60_000L
        assertEquals(expectedExpiry, entry.expiresAt)
    }

    @Test
    fun `POST admin waitlist seat transitions to SEATED and stores tableId`() = testApp {
        val c = client(this)
        val wid = UUID.randomUUID().toString()
        c.post("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateWaitlistRequest(id = wid, customerName = "吴一家", phone = "13700137004", partySize = 4))
        }

        val before = System.currentTimeMillis()
        val seat = c.post("/admin/waitlist/$wid/seat") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(mapOf("tableId" to "table-9"))
        }
        assertEquals(HttpStatusCode.OK, seat.status)
        val after = System.currentTimeMillis()

        val entry = c.get("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>().first { it.id == wid }

        assertEquals("SEATED", entry.status)
        assertEquals("table-9", entry.tableId)
        assertNotNull(entry.seatedAt)
        assertTrue(entry.seatedAt!! in before..after)
    }

    @Test
    fun `POST admin waitlist cancel transitions to CANCELLED`() = testApp {
        val c = client(this)
        val wid = UUID.randomUUID().toString()
        c.post("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateWaitlistRequest(id = wid, customerName = "黄女士", phone = "13700137005", partySize = 1))
        }

        val cancel = c.post("/admin/waitlist/$wid/cancel") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, cancel.status)

        val entry = c.get("/admin/waitlist?status=CANCELLED") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>().first { it.id == wid }
        assertEquals("CANCELLED", entry.status)
    }

    @Test
    fun `GET admin waitlist filters by status and orders by requestedAt`() = testApp {
        val c = client(this)
        val w1 = UUID.randomUUID().toString()
        val w2 = UUID.randomUUID().toString()
        transaction {
            WaitlistTable.insert {
                it[id] = w1; it[customerName] = "Earlier"; it[phone] = "111"; it[customerId] = null
                it[partySize] = 2; it[requestedAt] = 1_000L; it[quotedWaitMinutes] = 10
                it[status] = "WAITING"; it[notifiedAt] = null; it[expiresAt] = null
                it[tableId] = null; it[preferences] = ""; it[notes] = null; it[seatedAt] = null
            }
            WaitlistTable.insert {
                it[id] = w2; it[customerName] = "Later"; it[phone] = "222"; it[customerId] = null
                it[partySize] = 2; it[requestedAt] = 2_000L; it[quotedWaitMinutes] = 10
                it[status] = "NOTIFIED"; it[notifiedAt] = 1_500L; it[expiresAt] = null
                it[tableId] = null; it[preferences] = ""; it[notes] = null; it[seatedAt] = null
            }
        }

        val all = c.get("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>()
        assertEquals(listOf(w1, w2), all.map { it.id })

        val notified = c.get("/admin/waitlist?status=NOTIFIED") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>()
        assertEquals(listOf(w2), notified.map { it.id })
    }

    @Test
    fun `PATCH admin waitlist updates quotedWaitMinutes and tableId`() = testApp {
        val c = client(this)
        val wid = UUID.randomUUID().toString()
        c.post("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateWaitlistRequest(id = wid, customerName = "马先生", phone = "13700137006", partySize = 2))
        }

        c.patch("/admin/waitlist/$wid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(UpdateWaitlistRequest(quotedWaitMinutes = 45, tableId = "table-3"))
        }

        val entry = c.get("/admin/waitlist") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<WaitlistEntryDto>>().first { it.id == wid }
        assertEquals(45, entry.quotedWaitMinutes)
        assertEquals("table-3", entry.tableId)
    }

    // ── Shifts & availability ─────────────────────────────────────────────────

    @Test
    fun `POST admin shifts creates shift and parses daysOfWeek`() = testApp {
        val c = client(this)
        val sid = UUID.randomUUID().toString()

        val create = c.post("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateShiftRequest(
                    id = sid, name = "Lunch", startTime = "11:00", endTime = "14:00",
                    daysOfWeek = listOf(1, 2, 3, 4, 5), maxCovers = 50,
                )
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ShiftDto>>()
        val shift = list.first { it.id == sid }
        assertEquals(listOf(1, 2, 3, 4, 5), shift.daysOfWeek)
        assertEquals(50, shift.maxCovers)
    }

    @Test
    fun `POST admin shifts with empty daysOfWeek means every day`() = testApp {
        val c = client(this)
        val sid = UUID.randomUUID().toString()
        c.post("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(CreateShiftRequest(id = sid, name = "AllDay", startTime = "00:00", endTime = "23:00"))
        }

        val shift = c.get("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ShiftDto>>().first { it.id == sid }
        assertTrue(shift.daysOfWeek.isEmpty())
    }

    @Test
    fun `PATCH and DELETE admin shifts`() = testApp {
        val c = client(this)
        val sid = seedShift(name = "Original")

        val patch = c.patch("/admin/shifts/$sid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(com.restaurantpos.server.routes.UpdateShiftRequest(name = "Renamed", maxCovers = 30))
        }
        assertEquals(HttpStatusCode.OK, patch.status)

        var list = c.get("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ShiftDto>>()
        val patched = list.first { it.id == sid }
        assertEquals("Renamed", patched.name)
        assertEquals(30, patched.maxCovers)

        val del = c.delete("/admin/shifts/$sid") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.NoContent, del.status)

        list = c.get("/admin/shifts") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<ShiftDto>>()
        assertTrue(list.none { it.id == sid })
    }

    @Test
    fun `GET shift availability sums party sizes per slot and flags capacity`() = testApp {
        val c = client(this)
        // Shift: 18:00-19:00, 30-min slots, capacity 5
        val sid = seedShift(startTime = "18:00", endTime = "19:00", maxCovers = 5, slotIntervalMinutes = 30)
        val date = "2026-06-10"
        // Two reservations at 18:00 totalling 5 (== capacity -> not available)
        seedReservation(resDate = date, resTime = "18:00", resPartySize = 3, resShiftId = sid)
        seedReservation(resDate = date, resTime = "18:00", resPartySize = 2, resShiftId = sid)
        // One reservation at 18:30 totalling 2 (< capacity -> available)
        seedReservation(resDate = date, resTime = "18:30", resPartySize = 2, resShiftId = sid)
        // Cancelled reservation must be excluded from the tally
        seedReservation(resDate = date, resTime = "18:00", resPartySize = 10, resStatus = "CANCELLED", resShiftId = sid)

        val slots = c.get("/admin/shifts/$sid/availability?date=$date") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SlotAvailabilityDto>>()

        assertEquals(listOf("18:00", "18:30"), slots.map { it.time })

        val slot1800 = slots.first { it.time == "18:00" }
        assertEquals(5, slot1800.covers)
        assertEquals(5, slot1800.capacity)
        assertFalse(slot1800.available)

        val slot1830 = slots.first { it.time == "18:30" }
        assertEquals(2, slot1830.covers)
        assertTrue(slot1830.available)
    }

    @Test
    fun `GET shift availability with no reservations returns empty slots as available`() = testApp {
        val c = client(this)
        val sid = seedShift(startTime = "12:00", endTime = "13:00", maxCovers = 8, slotIntervalMinutes = 30)

        val slots = c.get("/admin/shifts/$sid/availability?date=2026-06-15") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SlotAvailabilityDto>>()

        assertEquals(listOf("12:00", "12:30"), slots.map { it.time })
        assertTrue(slots.all { it.covers == 0 && it.available })
    }

    // ── Special Days ──────────────────────────────────────────────────────────

    @Test
    fun `POST admin special-days creates closure with overrides`() = testApp {
        val c = client(this)
        val did = UUID.randomUUID().toString()

        val create = c.post("/admin/special-days") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                CreateSpecialDayRequest(
                    id = did, date = "2026-12-25", label = "Christmas",
                    maxCoversOverride = 20, closed = false,
                )
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val list = c.get("/admin/special-days?from=2026-12-01&to=2026-12-31") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SpecialDayDto>>()
        val day = list.first { it.id == did }
        assertEquals("Christmas", day.label)
        assertEquals(20, day.maxCoversOverride)
        assertFalse(day.closed)
    }

    @Test
    fun `GET admin special-days filters by date range and orders by date`() = testApp {
        val c = client(this)
        val d1 = UUID.randomUUID().toString()
        val d2 = UUID.randomUUID().toString()
        val d3 = UUID.randomUUID().toString()
        transaction {
            SpecialDaysTable.insert {
                it[id] = d1; it[date] = "2026-01-01"; it[label] = "New Year"
                it[maxCoversOverride] = null; it[closed] = true
            }
            SpecialDaysTable.insert {
                it[id] = d2; it[date] = "2026-02-14"; it[label] = "Valentine"
                it[maxCoversOverride] = null; it[closed] = false
            }
            SpecialDaysTable.insert {
                it[id] = d3; it[date] = "2026-12-25"; it[label] = "Christmas"
                it[maxCoversOverride] = null; it[closed] = true
            }
        }

        val ranged = c.get("/admin/special-days?from=2026-01-15&to=2026-12-01") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SpecialDayDto>>()
        assertEquals(listOf(d2), ranged.map { it.id })

        val all = c.get("/admin/special-days") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SpecialDayDto>>()
        assertEquals(listOf(d1, d2, d3), all.map { it.id })
    }

    @Test
    fun `PATCH and DELETE admin special-days`() = testApp {
        val c = client(this)
        val did = UUID.randomUUID().toString()
        transaction {
            SpecialDaysTable.insert {
                it[id] = did; it[date] = "2026-07-04"; it[label] = "Holiday"
                it[maxCoversOverride] = null; it[closed] = false
            }
        }

        val patch = c.patch("/admin/special-days/$did") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(UpdateSpecialDayRequest(label = "Updated Holiday", closed = true, maxCoversOverride = 15))
        }
        assertEquals(HttpStatusCode.OK, patch.status)

        var list = c.get("/admin/special-days") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SpecialDayDto>>()
        val patched = list.first { it.id == did }
        assertEquals("Updated Holiday", patched.label)
        assertTrue(patched.closed)
        assertEquals(15, patched.maxCoversOverride)

        val del = c.delete("/admin/special-days/$did") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.NoContent, del.status)

        list = c.get("/admin/special-days") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.body<List<SpecialDayDto>>()
        assertTrue(list.none { it.id == did })
    }
}
