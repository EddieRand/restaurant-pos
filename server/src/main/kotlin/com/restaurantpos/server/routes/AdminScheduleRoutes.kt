package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.db.tables.EmployeeTimecardsTable
import com.restaurantpos.server.db.tables.ShiftSchedulesTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * 员工排班管理 + 工时成本估算（排班工时 vs 实际打卡工时 × 时薪）。
 */
fun Route.adminScheduleRoutes() {
    authenticate("jwt") {
        route("/admin/schedules") {

            // GET /admin/schedules?from=YYYY-MM-DD&to=YYYY-MM-DD&operatorId=
            get {
                if (!call.requirePermission("staff.manage")) return@get
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                val operatorId = call.request.queryParameters["operatorId"]

                val list = transaction {
                    var q = ShiftSchedulesTable
                        .join(UsersTable, JoinType.LEFT, additionalConstraint = { ShiftSchedulesTable.operatorId eq UsersTable.id })
                        .selectAll()
                    if (!from.isNullOrBlank()) q = q.andWhere { ShiftSchedulesTable.date greaterEq from }
                    if (!to.isNullOrBlank()) q = q.andWhere { ShiftSchedulesTable.date lessEq to }
                    if (!operatorId.isNullOrBlank()) q = q.andWhere { ShiftSchedulesTable.operatorId eq operatorId }
                    q.orderBy(ShiftSchedulesTable.date to SortOrder.ASC, ShiftSchedulesTable.startTime to SortOrder.ASC)
                        .map { it.toShiftScheduleDto() }
                }
                call.respond(list)
            }

            // GET /admin/schedules/labor-cost?from=YYYY-MM-DD&to=YYYY-MM-DD
            get("/labor-cost") {
                if (!call.requirePermission("staff.manage")) return@get
                val from = call.request.queryParameters["from"] ?: ""
                val to = call.request.queryParameters["to"] ?: ""

                val resp = transaction {
                    val users = UsersTable.selectAll().associateBy { it[UsersTable.id] }

                    val scheduledMinutesByOperator = ShiftSchedulesTable.selectAll()
                        .where {
                            (ShiftSchedulesTable.date greaterEq from) and (ShiftSchedulesTable.date lessEq to)
                        }
                        .groupBy { it[ShiftSchedulesTable.operatorId] }
                        .mapValues { (_, rows) ->
                            rows.sumOf { row ->
                                val start = row[ShiftSchedulesTable.startTime].split(":").map { it.toInt() }
                                val end = row[ShiftSchedulesTable.endTime].split(":").map { it.toInt() }
                                val startMin = start[0] * 60 + start[1]
                                val endMin = end[0] * 60 + end[1]
                                (endMin - startMin).coerceAtLeast(0)
                            }
                        }

                    // Timecard range is epoch millis — convert date strings (YYYY-MM-DD) to day bounds
                    val fromMs = runCatching { java.time.LocalDate.parse(from).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0L)
                    val toMs = runCatching { java.time.LocalDate.parse(to).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(Long.MAX_VALUE)

                    val actualMinutesByOperator = EmployeeTimecardsTable.selectAll()
                        .where {
                            (EmployeeTimecardsTable.clockInAt greaterEq fromMs) and
                                (EmployeeTimecardsTable.clockInAt lessEq toMs) and
                                EmployeeTimecardsTable.clockOutAt.isNotNull()
                        }
                        .groupBy { it[EmployeeTimecardsTable.operatorId] }
                        .mapValues { (_, rows) ->
                            rows.sumOf {
                                val out = it[EmployeeTimecardsTable.clockOutAt]!!
                                val inn = it[EmployeeTimecardsTable.clockInAt]
                                ((out - inn) / 60000).toInt()
                            }
                        }

                    val operatorIds = (scheduledMinutesByOperator.keys + actualMinutesByOperator.keys).distinct()
                    val summaries = operatorIds.map { opId ->
                        val user = users[opId]
                        val wage = user?.get(UsersTable.hourlyWageMinorUnit) ?: 0L
                        val scheduledMin = scheduledMinutesByOperator[opId] ?: 0
                        val actualMin = actualMinutesByOperator[opId] ?: 0
                        LaborCostSummaryDto(
                            operatorId = opId,
                            operatorName = user?.get(UsersTable.displayName) ?: opId,
                            hourlyWageMinorUnit = wage,
                            scheduledMinutes = scheduledMin,
                            actualMinutes = actualMin,
                            scheduledCostMinorUnit = wage * scheduledMin / 60,
                            actualCostMinorUnit = wage * actualMin / 60,
                        )
                    }.sortedByDescending { it.actualCostMinorUnit }

                    LaborCostReportResponse(
                        summaries = summaries,
                        totalScheduledCostMinorUnit = summaries.sumOf { it.scheduledCostMinorUnit },
                        totalActualCostMinorUnit = summaries.sumOf { it.actualCostMinorUnit },
                    )
                }
                call.respond(resp)
            }

            // POST /admin/schedules
            post {
                if (!call.requirePermission("staff.manage")) return@post
                val req = call.receive<CreateShiftScheduleRequest>()
                val now = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()

                transaction {
                    ShiftSchedulesTable.insert {
                        it[ShiftSchedulesTable.id] = id
                        it[operatorId] = req.operatorId
                        it[date] = req.date
                        it[startTime] = req.startTime
                        it[endTime] = req.endTime
                        it[notes] = req.notes
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            // PATCH /admin/schedules/{id}
            patch("/{id}") {
                if (!call.requirePermission("staff.manage")) return@patch
                val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<UpdateShiftScheduleRequest>()
                val now = System.currentTimeMillis()

                val updated = transaction {
                    ShiftSchedulesTable.update({ ShiftSchedulesTable.id eq id }) { stmt ->
                        req.date?.let { stmt[date] = it }
                        req.startTime?.let { stmt[startTime] = it }
                        req.endTime?.let { stmt[endTime] = it }
                        req.notes?.let { stmt[notes] = it }
                        stmt[updatedAt] = now
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(mapOf("updated" to true))
            }

            // DELETE /admin/schedules/{id}
            delete("/{id}") {
                if (!call.requirePermission("staff.manage")) return@delete
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = transaction {
                    ShiftSchedulesTable.deleteWhere { ShiftSchedulesTable.id eq id }
                }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(mapOf("deleted" to true))
            }
        }
    }
}

private fun ResultRow.toShiftScheduleDto(): ShiftScheduleDto {
    val operatorName = try {
        this[UsersTable.displayName]
    } catch (_: Exception) {
        this[ShiftSchedulesTable.operatorId]
    }
    return ShiftScheduleDto(
        id = this[ShiftSchedulesTable.id],
        operatorId = this[ShiftSchedulesTable.operatorId],
        operatorName = operatorName,
        date = this[ShiftSchedulesTable.date],
        startTime = this[ShiftSchedulesTable.startTime],
        endTime = this[ShiftSchedulesTable.endTime],
        notes = this[ShiftSchedulesTable.notes],
    )
}
