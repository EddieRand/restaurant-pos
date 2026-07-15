package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.*
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminCashierShiftRoutes() {
    authenticate("jwt") {
        route("/admin/shifts") {

            // List shifts (paginated)
            get {
                val page     = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20

                val shifts = transaction {
                    CashierShiftsTable.selectAll()
                        .orderBy(CashierShiftsTable.openedAt, SortOrder.DESC)
                        .limit(pageSize, offset = (page * pageSize).toLong())
                        .map { it.toShiftDto() }
                }
                call.respond(shifts)
            }

            // Get shift detail (with cash movements + sales summary)
            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val dto = transaction {
                    val row = CashierShiftsTable.selectAll()
                        .where { CashierShiftsTable.id eq id }
                        .firstOrNull() ?: return@transaction null

                    val movements = CashMovementsTable.selectAll()
                        .where { CashMovementsTable.shiftId eq id }
                        .orderBy(CashMovementsTable.createdAt, SortOrder.ASC)
                        .map { it.toMovementDto() }

                    // Calculate cash from payments within shift window
                    val openedAt  = row[CashierShiftsTable.openedAt]
                    val closedAt  = row[CashierShiftsTable.closedAt] ?: System.currentTimeMillis()
                    val terminalId = row[CashierShiftsTable.terminalId]

                    val shiftOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                            (OrdersTable.sourceTerminalId eq terminalId) and
                            (OrdersTable.createdAt greaterEq openedAt) and
                            (OrdersTable.createdAt lessEq closedAt)
                        }
                        .toList()

                    val shiftOrderIds = shiftOrders.map { it[OrdersTable.id] }

                    val cashPaid = if (shiftOrderIds.isEmpty()) 0L else
                        PaymentsTable.selectAll()
                            .where {
                                (PaymentsTable.orderId inList shiftOrderIds) and
                                (PaymentsTable.method eq "CASH") and
                                (PaymentsTable.status inList listOf("PAID", "COMPLETED"))
                            }
                            .sumOf { it[PaymentsTable.amountMinorUnit] }

                    val cashRefunded = if (shiftOrderIds.isEmpty()) 0L else
                        PaymentsTable.selectAll()
                            .where {
                                (PaymentsTable.orderId inList shiftOrderIds) and
                                (PaymentsTable.method eq "CASH") and
                                (PaymentsTable.status eq "REFUNDED")
                            }
                            .sumOf { it[PaymentsTable.amountMinorUnit] }

                    val moneyIn  = movements.filter { it.type == "IN" }.sumOf { it.amountMinorUnit }
                    val moneyOut = movements.filter { it.type == "OUT" }.sumOf { it.amountMinorUnit }

                    val opening = row[CashierShiftsTable.openingCashMinorUnit]
                    val expected = opening + cashPaid - cashRefunded + moneyIn - moneyOut

                    val gross     = shiftOrders.sumOf { it[OrdersTable.subtotalMinorUnit] }
                    val refunds   = if (shiftOrderIds.isEmpty()) 0L else
                        PaymentsTable.selectAll()
                            .where {
                                (PaymentsTable.orderId inList shiftOrderIds) and
                                (PaymentsTable.status eq "REFUNDED")
                            }
                            .sumOf { it[PaymentsTable.amountMinorUnit] }
                    val discounts = shiftOrders.sumOf { it[OrdersTable.discountMinorUnit] }
                    val tax       = shiftOrders.sumOf { it[OrdersTable.taxTotalMinorUnit] }
                    val net       = gross - refunds - discounts

                    val actual = row[CashierShiftsTable.actualClosingCashMinorUnit]

                    row.toShiftDto().copy(
                        cashPaymentsMinorUnit  = cashPaid,
                        cashRefundsMinorUnit   = cashRefunded,
                        moneyInMinorUnit       = moneyIn,
                        moneyOutMinorUnit      = moneyOut,
                        expectedCashMinorUnit  = expected,
                        differencMinorUnit     = (actual ?: 0L) - expected,
                        grossSalesMinorUnit    = gross,
                        refundsMinorUnit       = refunds,
                        discountsMinorUnit     = discounts,
                        netSalesMinorUnit      = net,
                        taxMinorUnit           = tax,
                        movements              = movements,
                    )
                }
                if (dto == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(dto)
            }

            // Open a new shift (called from POS terminal)
            post {
                val req = call.receive<CreateCashierShiftRequest>()
                transaction {
                    // Assign sequential shift number
                    val lastNumber = CashierShiftsTable.selectAll()
                        .orderBy(CashierShiftsTable.shiftNumber, SortOrder.DESC)
                        .firstOrNull()?.get(CashierShiftsTable.shiftNumber) ?: 0

                    CashierShiftsTable.insert {
                        it[id]                   = req.id
                        it[shiftNumber]          = lastNumber + 1
                        it[terminalId]           = req.terminalId
                        it[terminalName]         = req.terminalName
                        it[storeName]            = req.storeName
                        it[openedByOperatorId]   = req.openedByOperatorId
                        it[openedByName]         = req.openedByName
                        it[openedAt]             = req.openedAt
                        it[openingCashMinorUnit] = req.openingCashMinorUnit
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            // Close a shift
            patch("/{id}/close") {
                val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<CloseShiftRequest>()
                val updated = transaction {
                    CashierShiftsTable.update({ CashierShiftsTable.id eq id }) {
                        it[closedByOperatorId]         = req.closedByOperatorId
                        it[closedByName]               = req.closedByName
                        it[closedAt]                   = req.closedAt
                        it[actualClosingCashMinorUnit] = req.actualClosingCashMinorUnit
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(mapOf("closed" to true))
            }

            // Add cash movement to a shift
            post("/{id}/movements") {
                val shiftId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<CreateCashMovementRequest>()
                transaction {
                    CashMovementsTable.insert {
                        it[id]              = req.id
                        it[CashMovementsTable.shiftId] = shiftId
                        it[type]            = req.type
                        it[amountMinorUnit] = req.amountMinorUnit
                        it[description]     = req.description
                        it[operatorId]      = req.operatorId
                        it[operatorName]    = req.operatorName
                        it[createdAt]       = req.createdAt
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
        }
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toShiftDto() = CashierShiftDto(
    id                         = this[CashierShiftsTable.id],
    shiftNumber                = this[CashierShiftsTable.shiftNumber],
    terminalId                 = this[CashierShiftsTable.terminalId],
    terminalName               = this[CashierShiftsTable.terminalName],
    storeName                  = this[CashierShiftsTable.storeName],
    openedByOperatorId         = this[CashierShiftsTable.openedByOperatorId],
    openedByName               = this[CashierShiftsTable.openedByName],
    closedByOperatorId         = this[CashierShiftsTable.closedByOperatorId],
    closedByName               = this[CashierShiftsTable.closedByName],
    openedAt                   = this[CashierShiftsTable.openedAt],
    closedAt                   = this[CashierShiftsTable.closedAt],
    openingCashMinorUnit       = this[CashierShiftsTable.openingCashMinorUnit],
    actualClosingCashMinorUnit = this[CashierShiftsTable.actualClosingCashMinorUnit],
)

private fun org.jetbrains.exposed.sql.ResultRow.toMovementDto() = CashMovementDto(
    id              = this[CashMovementsTable.id],
    shiftId         = this[CashMovementsTable.shiftId],
    type            = this[CashMovementsTable.type],
    amountMinorUnit = this[CashMovementsTable.amountMinorUnit],
    description     = this[CashMovementsTable.description],
    operatorId      = this[CashMovementsTable.operatorId],
    operatorName    = this[CashMovementsTable.operatorName],
    createdAt       = this[CashMovementsTable.createdAt],
)
