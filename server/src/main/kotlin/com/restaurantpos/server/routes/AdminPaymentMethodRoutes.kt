package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.PaymentMethodsTable
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

fun Route.adminPaymentMethodRoutes() {
    authenticate("jwt") {
        route("/admin/payment-methods") {

            get {
                val methods = transaction {
                    PaymentMethodsTable.selectAll()
                        .orderBy(PaymentMethodsTable.sortOrder to SortOrder.ASC)
                        .map { it.toPaymentMethodDto() }
                }
                call.respond(methods)
            }

            post {
                val req = call.receive<CreatePaymentMethodRequest>()
                val id = UUID.randomUUID().toString()
                val conflict = transaction {
                    val exists = PaymentMethodsTable.selectAll()
                        .where { PaymentMethodsTable.code eq req.code }
                        .any()
                    if (exists) return@transaction true
                    PaymentMethodsTable.insert {
                        it[PaymentMethodsTable.id]          = id
                        it[code]        = req.code
                        it[baseType]    = req.baseType
                        it[displayName] = req.displayName
                        it[color]       = req.color
                        it[sortOrder]   = req.sortOrder
                        it[isActive]    = req.isActive
                    }
                    false
                }
                if (conflict) call.respond(HttpStatusCode.Conflict, ErrorResponse("Payment method code already exists"))
                else call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            route("/{id}") {
                patch {
                    val pmId = call.parameters["id"]!!
                    val req = call.receive<UpdatePaymentMethodRequest>()
                    val updated = transaction {
                        val row = PaymentMethodsTable.selectAll().where { PaymentMethodsTable.id eq pmId }.firstOrNull()
                        if (row != null && row[PaymentMethodsTable.code] == "CASH" && req.isActive == false) {
                            return@transaction -1
                        }
                        PaymentMethodsTable.update({ PaymentMethodsTable.id eq pmId }) { stmt ->
                            req.displayName?.let { stmt[displayName] = it }
                            req.color?.let { stmt[color] = it }
                            req.sortOrder?.let { stmt[sortOrder] = it }
                            req.isActive?.let { stmt[isActive] = it }
                        }
                    }
                    if (updated == -1) return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cash payment method cannot be deactivated"))
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Payment method not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val pmId = call.parameters["id"]!!
                    val deleted = transaction {
                        val row = PaymentMethodsTable.selectAll().where { PaymentMethodsTable.id eq pmId }.firstOrNull()
                        if (row != null && row[PaymentMethodsTable.code] == "CASH") return@transaction -1
                        PaymentMethodsTable.deleteWhere { id eq pmId }
                    }
                    if (deleted == -1) call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cash payment method cannot be deleted"))
                    else if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Payment method not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }
        }
    }
}

private fun ResultRow.toPaymentMethodDto() = PaymentMethodDto(
    id          = this[PaymentMethodsTable.id],
    code        = this[PaymentMethodsTable.code],
    baseType    = this[PaymentMethodsTable.baseType],
    displayName = this[PaymentMethodsTable.displayName],
    color       = this[PaymentMethodsTable.color],
    sortOrder   = this[PaymentMethodsTable.sortOrder],
    isActive    = this[PaymentMethodsTable.isActive],
)
