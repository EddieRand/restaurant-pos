package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.EmployeeTimecardsTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Route.adminTimecardRoutes() {
    authenticate("jwt") {
        route("/admin/timecards") {

            // GET /admin/timecards/active — employees currently clocked in (no clockOut)
            get("/active") {
                val active = transaction {
                    EmployeeTimecardsTable.selectAll()
                        .where { EmployeeTimecardsTable.clockOutAt.isNull() }
                        .orderBy(EmployeeTimecardsTable.clockInAt, SortOrder.DESC)
                        .map { it.toTimecardDto() }
                }
                call.respond(active)
            }

            // GET /admin/timecards/report — hours summary per employee
            get("/report") {
                val from = call.request.queryParameters["from"]?.toLongOrNull() ?: 0L
                val to   = call.request.queryParameters["to"]?.toLongOrNull() ?: Long.MAX_VALUE
                val resp = transaction {
                    val rows = EmployeeTimecardsTable.selectAll()
                        .where {
                            (EmployeeTimecardsTable.clockInAt greaterEq from) and
                            (EmployeeTimecardsTable.clockInAt lessEq to) and
                            EmployeeTimecardsTable.clockOutAt.isNotNull()
                        }.toList()

                    val grouped = rows.groupBy { it[EmployeeTimecardsTable.operatorId] }
                    val summaries = grouped.map { (opId, opRows) ->
                        val totalMin = opRows.sumOf {
                            val out = it[EmployeeTimecardsTable.clockOutAt]!!
                            val inn = it[EmployeeTimecardsTable.clockInAt]
                            ((out - inn) / 60000).toInt()
                        }
                        TimecardSummaryDto(
                            operatorId   = opId,
                            operatorName = opRows.first()[EmployeeTimecardsTable.operatorName],
                            totalMinutes = totalMin,
                            shiftsCount  = opRows.size,
                        )
                    }.sortedByDescending { it.totalMinutes }

                    HoursReportResponse(
                        summaries    = summaries,
                        totalMinutes = summaries.sumOf { it.totalMinutes },
                    )
                }
                call.respond(resp)
            }

            // GET /admin/timecards — list with optional filters
            get {
                val from       = call.request.queryParameters["from"]?.toLongOrNull()
                val to         = call.request.queryParameters["to"]?.toLongOrNull()
                val operatorId = call.request.queryParameters["operatorId"]
                val page       = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val pageSize   = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

                val (timecards, total) = transaction {
                    var q = EmployeeTimecardsTable.selectAll()
                    if (from != null) q = q.andWhere { EmployeeTimecardsTable.clockInAt greaterEq from }
                    if (to   != null) q = q.andWhere { EmployeeTimecardsTable.clockInAt lessEq to }
                    if (operatorId != null) q = q.andWhere { EmployeeTimecardsTable.operatorId eq operatorId }
                    val total = q.count().toInt()
                    val rows  = q.orderBy(EmployeeTimecardsTable.clockInAt, SortOrder.DESC)
                        .limit(pageSize).offset((page * pageSize).toLong())
                        .map { it.toTimecardDto() }
                    rows to total
                }
                call.respond(TimecardListResponse(timecards = timecards, total = total))
            }

            // POST /admin/timecards — manual admin creation
            post {
                val req = call.receive<CreateTimecardRequest>()
                val now = System.currentTimeMillis()
                val id  = UUID.randomUUID().toString()

                val resolvedName = if (req.operatorName.isNotBlank()) req.operatorName else transaction {
                    UsersTable.selectAll()
                        .where { UsersTable.id eq req.operatorId }
                        .firstOrNull()?.get(UsersTable.displayName) ?: ""
                }

                transaction {
                    EmployeeTimecardsTable.insert {
                        it[EmployeeTimecardsTable.id]           = id
                        it[EmployeeTimecardsTable.operatorId]   = req.operatorId
                        it[EmployeeTimecardsTable.operatorName] = resolvedName
                        it[EmployeeTimecardsTable.terminalId]   = ""
                        it[EmployeeTimecardsTable.clockInAt]    = req.clockInAt
                        it[EmployeeTimecardsTable.clockOutAt]   = req.clockOutAt
                        it[EmployeeTimecardsTable.clockInNote]  = req.clockInNote
                        it[EmployeeTimecardsTable.clockOutNote] = req.clockOutNote
                        it[EmployeeTimecardsTable.createdAt]    = now
                        it[EmployeeTimecardsTable.updatedAt]    = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            // PATCH /admin/timecards/{id}
            patch("/{id}") {
                val id  = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<UpdateTimecardRequest>()
                val now = System.currentTimeMillis()

                val updated = transaction {
                    EmployeeTimecardsTable.update({ EmployeeTimecardsTable.id eq id }) { row ->
                        if (req.clockInAt  != null) row[clockInAt]    = req.clockInAt
                        if (req.clockOutAt != null) row[clockOutAt]   = req.clockOutAt
                        if (req.clockInNote  != null) row[clockInNote]  = req.clockInNote
                        if (req.clockOutNote != null) row[clockOutNote] = req.clockOutNote
                        row[updatedAt] = now
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(mapOf("updated" to true))
            }

            // DELETE /admin/timecards/{id}
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = transaction {
                    EmployeeTimecardsTable.deleteWhere { EmployeeTimecardsTable.id eq id }
                }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(mapOf("deleted" to true))
            }

            // POST /admin/timecards/clockin — POS clock-in
            post("/clockin") {
                val req = call.receive<ClockInRequest>()
                val now = System.currentTimeMillis()

                val resolvedName = if (req.operatorName.isNotBlank()) req.operatorName else transaction {
                    UsersTable.selectAll()
                        .where { UsersTable.id eq req.operatorId }
                        .firstOrNull()?.get(UsersTable.displayName) ?: ""
                }

                val existingOpen = transaction {
                    EmployeeTimecardsTable.selectAll()
                        .where {
                            (EmployeeTimecardsTable.operatorId eq req.operatorId) and
                            EmployeeTimecardsTable.clockOutAt.isNull()
                        }.firstOrNull()?.get(EmployeeTimecardsTable.id)
                }
                if (existingOpen != null) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Employee is already clocked in"))
                    return@post
                }

                val id = UUID.randomUUID().toString()
                val dto = transaction {
                    EmployeeTimecardsTable.insert {
                        it[EmployeeTimecardsTable.id]           = id
                        it[EmployeeTimecardsTable.operatorId]   = req.operatorId
                        it[EmployeeTimecardsTable.operatorName] = resolvedName
                        it[EmployeeTimecardsTable.terminalId]   = req.terminalId
                        it[EmployeeTimecardsTable.clockInAt]    = now
                        it[EmployeeTimecardsTable.clockOutAt]   = null
                        it[EmployeeTimecardsTable.clockInNote]  = req.note
                        it[EmployeeTimecardsTable.clockOutNote] = ""
                        it[EmployeeTimecardsTable.createdAt]    = now
                        it[EmployeeTimecardsTable.updatedAt]    = now
                    }
                    TimecardDto(
                        id           = id,
                        operatorId   = req.operatorId,
                        operatorName = resolvedName,
                        terminalId   = req.terminalId,
                        clockInAt    = now,
                        clockOutAt   = null,
                        durationMinutes = null,
                        clockInNote  = req.note,
                        clockOutNote = "",
                        createdAt    = now,
                    )
                }
                call.respond(HttpStatusCode.Created, dto)
            }

            // POST /admin/timecards/{id}/clockout — POS clock-out
            post("/{id}/clockout") {
                val id  = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<ClockOutRequest>()
                val now = System.currentTimeMillis()

                val dto = transaction {
                    val row = EmployeeTimecardsTable.selectAll()
                        .where { EmployeeTimecardsTable.id eq id }
                        .firstOrNull() ?: return@transaction null
                    if (row[EmployeeTimecardsTable.clockOutAt] != null)
                        return@transaction null

                    EmployeeTimecardsTable.update({ EmployeeTimecardsTable.id eq id }) {
                        it[clockOutAt]   = now
                        it[clockOutNote] = req.note
                        it[updatedAt]    = now
                    }
                    val clockIn = row[EmployeeTimecardsTable.clockInAt]
                    row.toTimecardDto().copy(
                        clockOutAt      = now,
                        clockOutNote    = req.note,
                        durationMinutes = ((now - clockIn) / 60000).toInt(),
                    )
                }
                if (dto == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(dto)
            }
        }
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toTimecardDto(): TimecardDto {
    val clockIn  = this[EmployeeTimecardsTable.clockInAt]
    val clockOut = this[EmployeeTimecardsTable.clockOutAt]
    return TimecardDto(
        id              = this[EmployeeTimecardsTable.id],
        operatorId      = this[EmployeeTimecardsTable.operatorId],
        operatorName    = this[EmployeeTimecardsTable.operatorName],
        terminalId      = this[EmployeeTimecardsTable.terminalId],
        clockInAt       = clockIn,
        clockOutAt      = clockOut,
        durationMinutes = clockOut?.let { ((it - clockIn) / 60000).toInt() },
        clockInNote     = this[EmployeeTimecardsTable.clockInNote],
        clockOutNote    = this[EmployeeTimecardsTable.clockOutNote],
        createdAt       = this[EmployeeTimecardsTable.createdAt],
    )
}
