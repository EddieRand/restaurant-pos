package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminOrderRoutes() {
    authenticate("jwt") {
        route("/admin/orders") {

            get {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to = params["to"]?.toLongOrNull() ?: Long.MAX_VALUE
                val status = params["status"]
                val query = params["query"]
                val operatorId = params["operatorId"]
                val paymentMethod = params["paymentMethod"]
                val page = params["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

                val (orders, total) = transaction {
                    var condition: Op<Boolean> = (OrdersTable.createdAt greaterEq from) and
                            (OrdersTable.createdAt lessEq to)
                    if (!status.isNullOrBlank()) condition = condition and (OrdersTable.status eq status)
                    if (!query.isNullOrBlank()) {
                        condition = condition and (OrdersTable.id like "%$query%")
                    }
                    if (!operatorId.isNullOrBlank()) {
                        condition = condition and (OrdersTable.operatorId eq operatorId)
                    }
                    if (!paymentMethod.isNullOrBlank()) {
                        val orderIdsWithMethod = PaymentsTable
                            .select(PaymentsTable.orderId)
                            .where { (PaymentsTable.method eq paymentMethod) and (PaymentsTable.status eq "PAID") }
                            .map { it[PaymentsTable.orderId] }
                            .distinct()
                        condition = if (orderIdsWithMethod.isEmpty()) {
                            condition and Op.FALSE
                        } else {
                            condition and orderIdsWithMethod.map { OrdersTable.id eq it }
                                .reduce { acc, op -> acc or op }
                        }
                    }
                    val total = OrdersTable.selectAll().where { condition }.count()

                    val displayNames = UsersTable.selectAll()
                        .associate { it[UsersTable.id] to it[UsersTable.displayName] }

                    val rows = OrdersTable.selectAll()
                        .where { condition }
                        .orderBy(OrdersTable.createdAt to SortOrder.DESC)
                        .limit(pageSize, (page * pageSize).toLong())
                        .map { it.toOrderSummaryDto(displayNames) }
                    rows to total
                }
                call.respond(OrderListResponse(orders, total, page, pageSize))
            }

            get("/{id}") {
                val orderId = call.parameters["id"]!!
                val detail = transaction {
                    val orderRow = OrdersTable.selectAll()
                        .where { OrdersTable.id eq orderId }
                        .firstOrNull() ?: return@transaction null

                    val operatorName = orderRow[OrdersTable.operatorId]
                        .takeIf { it.isNotBlank() }
                        ?.let { opId ->
                            UsersTable.selectAll().where { UsersTable.id eq opId }
                                .firstOrNull()?.get(UsersTable.displayName)
                        } ?: ""

                    val order = orderRow.toOrderSummaryDto(mapOf(orderRow[OrdersTable.operatorId] to operatorName))

                    val items = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq orderId }
                        .map { it.toOrderItemDto() }

                    val payments = PaymentsTable.selectAll()
                        .where { PaymentsTable.orderId eq orderId }
                        .map { it.toPaymentDto() }

                    OrderDetailDto(order, items, payments)
                }
                if (detail == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found"))
                else call.respond(detail)
            }

            post("/payments/{paymentId}/refund") {
                val paymentId = call.parameters["paymentId"]!!
                val req = call.receive<RefundRequest>()
                val operatorId = call.principal<JWTPrincipal>()?.payload
                    ?.getClaim(JwtConfig.CLAIM_USER_ID)?.asString() ?: ""

                val result = transaction {
                    val original = PaymentsTable.selectAll()
                        .where { PaymentsTable.id eq paymentId }
                        .firstOrNull() ?: return@transaction "not_found"

                    if (original[PaymentsTable.status] != "PAID") return@transaction "not_paid"

                    val alreadyRefunded = PaymentsTable.selectAll()
                        .where { PaymentsTable.refundedPaymentId eq paymentId }
                        .sumOf { it[PaymentsTable.amountMinorUnit] }

                    val remaining = original[PaymentsTable.amountMinorUnit] - alreadyRefunded
                    if (req.amountMinorUnit <= 0 || req.amountMinorUnit > remaining) return@transaction "invalid_amount"

                    val now = System.currentTimeMillis()
                    PaymentsTable.insert {
                        it[id] = "refund-${java.util.UUID.randomUUID()}"
                        it[orderId] = original[PaymentsTable.orderId]
                        it[amountMinorUnit] = req.amountMinorUnit
                        it[method] = original[PaymentsTable.method]
                        it[status] = "REFUNDED"
                        it[PaymentsTable.operatorId] = operatorId
                        it[refundedPaymentId] = paymentId
                        it[createdAt] = now
                    }

                    if (req.amountMinorUnit == remaining) {
                        PaymentsTable.update({ PaymentsTable.id eq paymentId }) {
                            it[status] = "REFUNDED"
                        }
                    }

                    original[PaymentsTable.orderId]
                }

                when (result) {
                    "not_found" -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Payment not found"))
                    "not_paid" -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Payment is not refundable"))
                    "invalid_amount" -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid refund amount"))
                    else -> {
                        val orderId = result as String
                        val detail = transaction {
                            val orderRow = OrdersTable.selectAll()
                                .where { OrdersTable.id eq orderId }
                                .first()
                            val operatorName = orderRow[OrdersTable.operatorId]
                                .takeIf { it.isNotBlank() }
                                ?.let { opId ->
                                    UsersTable.selectAll().where { UsersTable.id eq opId }
                                        .firstOrNull()?.get(UsersTable.displayName)
                                } ?: ""
                            val order = orderRow.toOrderSummaryDto(mapOf(orderRow[OrdersTable.operatorId] to operatorName))
                            val items = OrderItemsTable.selectAll()
                                .where { OrderItemsTable.orderId eq orderId }
                                .map { it.toOrderItemDto() }
                            val payments = PaymentsTable.selectAll()
                                .where { PaymentsTable.orderId eq orderId }
                                .map { it.toPaymentDto() }
                            OrderDetailDto(order, items, payments)
                        }
                        call.respond(detail)
                    }
                }
            }

            post("/{id}/confirm-qr") {
                val orderId = call.parameters["id"]!!
                val status = transaction {
                    val order = OrdersTable.selectAll()
                        .where { OrdersTable.id eq orderId }
                        .firstOrNull()
                        ?: return@transaction null
                    if (order[OrdersTable.status] != "PENDING_CONFIRMATION") {
                        return@transaction order[OrdersTable.status]
                    }
                    val now = System.currentTimeMillis()
                    OrdersTable.update({ OrdersTable.id eq orderId }) {
                        it[OrdersTable.status] = "PLACED"
                        it[updatedAt] = now
                    }
                    OrderItemsTable.update({ OrderItemsTable.orderId eq orderId }) {
                        it[OrderItemsTable.status] = "PLACED"
                    }
                    "PLACED"
                }
                if (status == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found"))
                else call.respond(mapOf("status" to status))
            }

            post("/{id}/reject-qr") {
                val orderId = call.parameters["id"]!!
                val status = transaction {
                    val order = OrdersTable.selectAll()
                        .where { OrdersTable.id eq orderId }
                        .firstOrNull()
                        ?: return@transaction null
                    if (order[OrdersTable.status] != "PENDING_CONFIRMATION") {
                        return@transaction order[OrdersTable.status]
                    }
                    val now = System.currentTimeMillis()
                    OrdersTable.update({ OrdersTable.id eq orderId }) {
                        it[OrdersTable.status] = "REJECTED"
                        it[updatedAt] = now
                    }
                    OrderItemsTable.update({ OrderItemsTable.orderId eq orderId }) {
                        it[OrderItemsTable.status] = "REJECTED"
                    }
                    "REJECTED"
                }
                if (status == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found"))
                else call.respond(mapOf("status" to status))
            }
        }
    }
}

private fun ResultRow.toOrderSummaryDto(displayNames: Map<String, String>) = OrderSummaryDto(
    id = this[OrdersTable.id],
    type = this[OrdersTable.type],
    tableId = this[OrdersTable.tableId],
    sourceTerminalId = this[OrdersTable.sourceTerminalId],
    guestCount = this[OrdersTable.guestCount],
    status = this[OrdersTable.status],
    subtotalMinorUnit = this[OrdersTable.subtotalMinorUnit],
    taxTotalMinorUnit = this[OrdersTable.taxTotalMinorUnit],
    serviceChargeMinorUnit = this[OrdersTable.serviceChargeMinorUnit],
    tipMinorUnit = this[OrdersTable.tipMinorUnit],
    discountMinorUnit = this[OrdersTable.discountMinorUnit],
    orderNotes = this[OrdersTable.orderNotes],
    createdAt = this[OrdersTable.createdAt],
    updatedAt = this[OrdersTable.updatedAt],
    operatorId = this[OrdersTable.operatorId],
    operatorName = displayNames[this[OrdersTable.operatorId]] ?: "",
)

private fun ResultRow.toOrderItemDto() = OrderItemDto(
    id = this[OrderItemsTable.id],
    orderId = this[OrderItemsTable.orderId],
    menuItemId = this[OrderItemsTable.menuItemId],
    menuItemNameSnapshot = this[OrderItemsTable.menuItemNameSnapshot],
    quantity = this[OrderItemsTable.quantity],
    unitPriceMinorUnit = this[OrderItemsTable.unitPriceMinorUnit],
    taxRateId = this[OrderItemsTable.taxRateId],
    course = this[OrderItemsTable.course],
    status = this[OrderItemsTable.status],
    notes = this[OrderItemsTable.notes],
    categoryId = this[OrderItemsTable.categoryId],
    allergenSnapshot = this[OrderItemsTable.allergenSnapshot],
    comboId = this[OrderItemsTable.comboId],
)

private fun ResultRow.toPaymentDto() = PaymentDto(
    id = this[PaymentsTable.id],
    orderId = this[PaymentsTable.orderId],
    amountMinorUnit = this[PaymentsTable.amountMinorUnit],
    method = this[PaymentsTable.method],
    status = this[PaymentsTable.status],
    operatorId = this[PaymentsTable.operatorId],
    refundedPaymentId = this[PaymentsTable.refundedPaymentId],
    createdAt = this[PaymentsTable.createdAt],
)
