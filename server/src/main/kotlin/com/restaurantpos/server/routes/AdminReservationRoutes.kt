package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.*
import com.restaurantpos.server.model.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable data class ReservationDto(
    val id: String, val customerName: String, val phone: String,
    val customerId: String? = null, val partySize: Int, val date: String, val time: String,
    val tableId: String? = null, val status: String, val notes: String? = null,
    val internalNotes: String? = null, val shiftId: String? = null, val source: String,
    val guestTags: List<String> = emptyList(), val confirmationCode: String? = null,
    val estimatedDurationMinutes: Int? = null, val depositAmount: Long? = null,
    val depositPaid: Boolean = false, val reminderSentAt: Long? = null,
    val seatedAt: Long? = null, val completedAt: Long? = null, val createdAt: Long,
)

@Serializable data class CreateReservationRequest(
    val id: String = UUID.randomUUID().toString(),
    val customerName: String, val phone: String, val customerId: String? = null,
    val partySize: Int, val date: String, val time: String,
    val tableId: String? = null, val notes: String? = null, val internalNotes: String? = null,
    val shiftId: String? = null, val source: String = "ONLINE",
    val guestTags: List<String> = emptyList(), val confirmationCode: String? = null,
    val estimatedDurationMinutes: Int? = null, val depositAmount: Long? = null,
)

@Serializable data class UpdateReservationRequest(
    val customerName: String? = null, val phone: String? = null, val partySize: Int? = null,
    val date: String? = null, val time: String? = null, val tableId: String? = null,
    val status: String? = null, val notes: String? = null, val internalNotes: String? = null,
    val guestTags: List<String>? = null, val seatedAt: Long? = null, val completedAt: Long? = null,
    val depositPaid: Boolean? = null,
)

@Serializable data class WaitlistEntryDto(
    val id: String, val customerName: String, val phone: String,
    val customerId: String? = null, val partySize: Int, val requestedAt: Long,
    val quotedWaitMinutes: Int, val status: String, val notifiedAt: Long? = null,
    val expiresAt: Long? = null, val tableId: String? = null,
    val preferences: List<String> = emptyList(), val seatedAt: Long? = null, val notes: String? = null,
)

@Serializable data class CreateWaitlistRequest(
    val id: String = UUID.randomUUID().toString(),
    val customerName: String, val phone: String, val customerId: String? = null,
    val partySize: Int, val quotedWaitMinutes: Int = 0,
    val preferences: List<String> = emptyList(), val notes: String? = null,
)

@Serializable data class UpdateWaitlistRequest(
    val status: String? = null, val tableId: String? = null, val quotedWaitMinutes: Int? = null,
    val notifiedAt: Long? = null, val expiresAt: Long? = null, val seatedAt: Long? = null,
)

@Serializable data class ShiftDto(
    val id: String, val name: String, val startTime: String, val endTime: String,
    val daysOfWeek: List<Int>, val maxCovers: Int, val coverGoal: Int,
    val defaultTurnTimeMinutes: Int, val slotIntervalMinutes: Int,
    val bookingCutoffMinutes: Int, val enabled: Boolean,
)

@Serializable data class CreateShiftRequest(
    val id: String = UUID.randomUUID().toString(),
    val name: String, val startTime: String, val endTime: String,
    val daysOfWeek: List<Int> = emptyList(), val maxCovers: Int = 100, val coverGoal: Int = 80,
    val defaultTurnTimeMinutes: Int = 90, val slotIntervalMinutes: Int = 30,
    val bookingCutoffMinutes: Int = 60, val enabled: Boolean = true,
)

@Serializable data class UpdateShiftRequest(
    val name: String? = null, val startTime: String? = null, val endTime: String? = null,
    val daysOfWeek: List<Int>? = null, val maxCovers: Int? = null, val coverGoal: Int? = null,
    val defaultTurnTimeMinutes: Int? = null, val slotIntervalMinutes: Int? = null,
    val bookingCutoffMinutes: Int? = null, val enabled: Boolean? = null,
)

@Serializable data class SpecialDayDto(
    val id: String, val date: String, val label: String,
    val maxCoversOverride: Int? = null, val closed: Boolean,
)

@Serializable data class CreateSpecialDayRequest(
    val id: String = UUID.randomUUID().toString(),
    val date: String, val label: String, val maxCoversOverride: Int? = null, val closed: Boolean = false,
)

@Serializable data class UpdateSpecialDayRequest(
    val label: String? = null, val maxCoversOverride: Int? = null, val closed: Boolean? = null,
)

@Serializable data class SlotAvailabilityDto(val time: String, val covers: Int, val capacity: Int, val available: Boolean)

// ── Routes ────────────────────────────────────────────────────────────────────

fun Route.adminReservationRoutes() {
    authenticate("jwt") {

        // ── Reservations ──────────────────────────────────────────────────────

        route("/admin/reservations") {
            get {
                val date = call.request.queryParameters["date"]
                val status = call.request.queryParameters["status"]
                val shiftId = call.request.queryParameters["shiftId"]
                val list = transaction {
                    var q = ReservationsTable.selectAll()
                    if (!date.isNullOrBlank()) q = q.andWhere { ReservationsTable.date eq date }
                    if (!status.isNullOrBlank()) q = q.andWhere { ReservationsTable.status eq status }
                    if (!shiftId.isNullOrBlank()) q = q.andWhere { ReservationsTable.shiftId eq shiftId }
                    q.orderBy(ReservationsTable.date).orderBy(ReservationsTable.time).map { it.toReservationDto() }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateReservationRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    ReservationsTable.insert {
                        it[id] = req.id; it[customerName] = req.customerName; it[phone] = req.phone
                        it[customerId] = req.customerId; it[partySize] = req.partySize
                        it[date] = req.date; it[time] = req.time; it[tableId] = req.tableId
                        it[notes] = req.notes; it[internalNotes] = req.internalNotes
                        it[shiftId] = req.shiftId; it[bookingSource] = req.source
                        it[guestTags] = req.guestTags.joinToString("|")
                        it[confirmationCode] = req.confirmationCode ?: UUID.randomUUID().toString().take(8).uppercase()
                        it[estimatedDurationMinutes] = req.estimatedDurationMinutes
                        it[depositAmount] = req.depositAmount; it[createdAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val rid = call.parameters["id"]!!
                val req = call.receive<UpdateReservationRequest>()
                val updated = transaction {
                    ReservationsTable.update({ ReservationsTable.id eq rid }) { stmt ->
                        req.customerName?.let { stmt[customerName] = it }
                        req.phone?.let { stmt[phone] = it }
                        req.partySize?.let { stmt[partySize] = it }
                        req.date?.let { stmt[date] = it }
                        req.time?.let { stmt[time] = it }
                        req.tableId.let { stmt[tableId] = it }
                        req.status?.let { stmt[status] = it }
                        req.notes.let { stmt[notes] = it }
                        req.internalNotes.let { stmt[internalNotes] = it }
                        req.guestTags?.let { stmt[guestTags] = it.joinToString("|") }
                        req.seatedAt.let { stmt[seatedAt] = it }
                        req.completedAt.let { stmt[completedAt] = it }
                        req.depositPaid?.let { stmt[depositPaid] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Reservation not found"))
                else call.respond(mapOf("updated" to true))
            }
            delete("/{id}") {
                val rid = call.parameters["id"]!!
                transaction { ReservationsTable.deleteWhere { id eq rid } }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── Waitlist ──────────────────────────────────────────────────────────

        route("/admin/waitlist") {
            get {
                val status = call.request.queryParameters["status"]
                val list = transaction {
                    var q = WaitlistTable.selectAll()
                    if (!status.isNullOrBlank()) q = q.where { WaitlistTable.status eq status }
                    q.orderBy(WaitlistTable.requestedAt).map { it.toWaitlistDto() }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateWaitlistRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    WaitlistTable.insert {
                        it[id] = req.id; it[customerName] = req.customerName; it[phone] = req.phone
                        it[customerId] = req.customerId; it[partySize] = req.partySize
                        it[requestedAt] = now; it[quotedWaitMinutes] = req.quotedWaitMinutes
                        it[preferences] = req.preferences.joinToString("|")
                        it[notes] = req.notes
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val wid = call.parameters["id"]!!
                val req = call.receive<UpdateWaitlistRequest>()
                transaction {
                    WaitlistTable.update({ WaitlistTable.id eq wid }) { stmt ->
                        req.status?.let { stmt[status] = it }
                        req.tableId.let { stmt[tableId] = it }
                        req.quotedWaitMinutes?.let { stmt[quotedWaitMinutes] = it }
                        req.notifiedAt.let { stmt[notifiedAt] = it }
                        req.expiresAt.let { stmt[expiresAt] = it }
                        req.seatedAt.let { stmt[seatedAt] = it }
                    }
                }
                call.respond(mapOf("updated" to true))
            }
            post("/{id}/notify") {
                val wid = call.parameters["id"]!!
                val now = System.currentTimeMillis()
                transaction {
                    WaitlistTable.update({ WaitlistTable.id eq wid }) {
                        it[status] = "NOTIFIED"; it[notifiedAt] = now
                        it[expiresAt] = now + 15 * 60_000L
                    }
                }
                call.respond(mapOf("notified" to true))
            }
            post("/{id}/seat") {
                val wid = call.parameters["id"]!!
                val body = call.receiveNullable<Map<String, String>>() ?: emptyMap()
                val tableId = body["tableId"]
                val now = System.currentTimeMillis()
                transaction {
                    WaitlistTable.update({ WaitlistTable.id eq wid }) {
                        it[status] = "SEATED"; it[seatedAt] = now
                        tableId?.let { t -> it[WaitlistTable.tableId] = t }
                    }
                }
                call.respond(mapOf("seated" to true))
            }
            post("/{id}/cancel") {
                val wid = call.parameters["id"]!!
                transaction { WaitlistTable.update({ WaitlistTable.id eq wid }) { it[status] = "CANCELLED" } }
                call.respond(mapOf("cancelled" to true))
            }
        }

        // ── Shifts ────────────────────────────────────────────────────────────

        route("/admin/shifts") {
            get {
                val list = transaction { ShiftsTable.selectAll().orderBy(ShiftsTable.startTime).map { it.toShiftDto() } }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateShiftRequest>()
                transaction {
                    ShiftsTable.insert {
                        it[id] = req.id; it[name] = req.name; it[startTime] = req.startTime
                        it[endTime] = req.endTime; it[daysOfWeek] = req.daysOfWeek.joinToString(",")
                        it[maxCovers] = req.maxCovers; it[coverGoal] = req.coverGoal
                        it[defaultTurnTimeMinutes] = req.defaultTurnTimeMinutes
                        it[slotIntervalMinutes] = req.slotIntervalMinutes
                        it[bookingCutoffMinutes] = req.bookingCutoffMinutes; it[enabled] = req.enabled
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val sid = call.parameters["id"]!!
                val req = call.receive<UpdateShiftRequest>()
                transaction {
                    ShiftsTable.update({ ShiftsTable.id eq sid }) { stmt ->
                        req.name?.let { stmt[name] = it }; req.startTime?.let { stmt[startTime] = it }
                        req.endTime?.let { stmt[endTime] = it }
                        req.daysOfWeek?.let { stmt[daysOfWeek] = it.joinToString(",") }
                        req.maxCovers?.let { stmt[maxCovers] = it }; req.coverGoal?.let { stmt[coverGoal] = it }
                        req.defaultTurnTimeMinutes?.let { stmt[defaultTurnTimeMinutes] = it }
                        req.slotIntervalMinutes?.let { stmt[slotIntervalMinutes] = it }
                        req.bookingCutoffMinutes?.let { stmt[bookingCutoffMinutes] = it }
                        req.enabled?.let { stmt[enabled] = it }
                    }
                }
                call.respond(mapOf("updated" to true))
            }
            delete("/{id}") {
                val sid = call.parameters["id"]!!
                transaction { ShiftsTable.deleteWhere { id eq sid } }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{id}/availability") {
                val sid = call.parameters["id"]!!
                val date = call.request.queryParameters["date"] ?: ""
                val slots = transaction {
                    val shift = ShiftsTable.selectAll().where { ShiftsTable.id eq sid }.singleOrNull()
                        ?: return@transaction emptyList()
                    val maxCovers = shift[ShiftsTable.maxCovers]
                    val interval = shift[ShiftsTable.slotIntervalMinutes]
                    val startParts = shift[ShiftsTable.startTime].split(":").map { it.toInt() }
                    val endParts = shift[ShiftsTable.endTime].split(":").map { it.toInt() }
                    var startMins = startParts[0] * 60 + startParts[1]
                    val endMins = endParts[0] * 60 + endParts[1]
                    val reserved = if (date.isNotBlank()) {
                        ReservationsTable.selectAll()
                            .where { (ReservationsTable.shiftId eq sid) and (ReservationsTable.date eq date) and (ReservationsTable.status neq "CANCELLED") }
                            .groupBy { it[ReservationsTable.time] }
                            .mapValues { e -> e.value.sumOf { it[ReservationsTable.partySize] } }
                    } else emptyMap()
                    val result = mutableListOf<SlotAvailabilityDto>()
                    while (startMins < endMins) {
                        val hh = startMins / 60
                        val mm = startMins % 60
                        val timeStr = "%02d:%02d".format(hh, mm)
                        val booked = reserved[timeStr] ?: 0
                        result += SlotAvailabilityDto(timeStr, booked, maxCovers, booked < maxCovers)
                        startMins += interval
                    }
                    result
                }
                call.respond(slots)
            }
        }

        // ── Special Days ──────────────────────────────────────────────────────

        route("/admin/special-days") {
            get {
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                val list = transaction {
                    var q = SpecialDaysTable.selectAll()
                    if (!from.isNullOrBlank()) q = q.andWhere { SpecialDaysTable.date greaterEq from }
                    if (!to.isNullOrBlank()) q = q.andWhere { SpecialDaysTable.date lessEq to }
                    q.orderBy(SpecialDaysTable.date).map { it.toSpecialDayDto() }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateSpecialDayRequest>()
                transaction {
                    SpecialDaysTable.insert {
                        it[id] = req.id; it[date] = req.date; it[label] = req.label
                        it[maxCoversOverride] = req.maxCoversOverride; it[closed] = req.closed
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val did = call.parameters["id"]!!
                val req = call.receive<UpdateSpecialDayRequest>()
                transaction {
                    SpecialDaysTable.update({ SpecialDaysTable.id eq did }) { stmt ->
                        req.label?.let { stmt[label] = it }
                        req.maxCoversOverride.let { stmt[maxCoversOverride] = it }
                        req.closed?.let { stmt[closed] = it }
                    }
                }
                call.respond(mapOf("updated" to true))
            }
            delete("/{id}") {
                val did = call.parameters["id"]!!
                transaction { SpecialDaysTable.deleteWhere { id eq did } }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

// ── Row mappers ───────────────────────────────────────────────────────────────

private fun ResultRow.toReservationDto() = ReservationDto(
    id = this[ReservationsTable.id], customerName = this[ReservationsTable.customerName],
    phone = this[ReservationsTable.phone], customerId = this[ReservationsTable.customerId],
    partySize = this[ReservationsTable.partySize], date = this[ReservationsTable.date],
    time = this[ReservationsTable.time], tableId = this[ReservationsTable.tableId],
    status = this[ReservationsTable.status], notes = this[ReservationsTable.notes],
    internalNotes = this[ReservationsTable.internalNotes], shiftId = this[ReservationsTable.shiftId],
    source = this[ReservationsTable.bookingSource],
    guestTags = this[ReservationsTable.guestTags].split("|").filter { it.isNotEmpty() },
    confirmationCode = this[ReservationsTable.confirmationCode],
    estimatedDurationMinutes = this[ReservationsTable.estimatedDurationMinutes],
    depositAmount = this[ReservationsTable.depositAmount],
    depositPaid = this[ReservationsTable.depositPaid],
    reminderSentAt = this[ReservationsTable.reminderSentAt],
    seatedAt = this[ReservationsTable.seatedAt], completedAt = this[ReservationsTable.completedAt],
    createdAt = this[ReservationsTable.createdAt],
)

private fun ResultRow.toWaitlistDto() = WaitlistEntryDto(
    id = this[WaitlistTable.id], customerName = this[WaitlistTable.customerName],
    phone = this[WaitlistTable.phone], customerId = this[WaitlistTable.customerId],
    partySize = this[WaitlistTable.partySize], requestedAt = this[WaitlistTable.requestedAt],
    quotedWaitMinutes = this[WaitlistTable.quotedWaitMinutes], status = this[WaitlistTable.status],
    notifiedAt = this[WaitlistTable.notifiedAt], expiresAt = this[WaitlistTable.expiresAt],
    tableId = this[WaitlistTable.tableId],
    preferences = this[WaitlistTable.preferences].split("|").filter { it.isNotEmpty() },
    seatedAt = this[WaitlistTable.seatedAt], notes = this[WaitlistTable.notes],
)

private fun ResultRow.toShiftDto() = ShiftDto(
    id = this[ShiftsTable.id], name = this[ShiftsTable.name],
    startTime = this[ShiftsTable.startTime], endTime = this[ShiftsTable.endTime],
    daysOfWeek = this[ShiftsTable.daysOfWeek].split(",").filter { it.isNotEmpty() }.map { it.toInt() },
    maxCovers = this[ShiftsTable.maxCovers], coverGoal = this[ShiftsTable.coverGoal],
    defaultTurnTimeMinutes = this[ShiftsTable.defaultTurnTimeMinutes],
    slotIntervalMinutes = this[ShiftsTable.slotIntervalMinutes],
    bookingCutoffMinutes = this[ShiftsTable.bookingCutoffMinutes], enabled = this[ShiftsTable.enabled],
)

private fun ResultRow.toSpecialDayDto() = SpecialDayDto(
    id = this[SpecialDaysTable.id], date = this[SpecialDaysTable.date],
    label = this[SpecialDaysTable.label], maxCoversOverride = this[SpecialDaysTable.maxCoversOverride],
    closed = this[SpecialDaysTable.closed],
)
