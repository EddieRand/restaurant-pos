package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.db.tables.GiftCardTransactionsTable
import com.restaurantpos.server.db.tables.GiftCardsTable
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

/**
 * 礼品卡 / 储值卡管理 + 收银端储值支付（核销）。
 */
fun Route.adminGiftCardRoutes() {
    authenticate("jwt") {
        route("/admin/gift-cards") {

            // GET /admin/gift-cards?code=
            get {
                val code = call.request.queryParameters["code"]
                val cards = transaction {
                    var q = GiftCardsTable.selectAll()
                    if (!code.isNullOrBlank()) q = q.andWhere { GiftCardsTable.code eq code.uppercase() }
                    q.orderBy(GiftCardsTable.createdAt to SortOrder.DESC).map { it.toGiftCardDto() }
                }
                call.respond(cards)
            }

            // POST /admin/gift-cards — issue a new card
            post {
                if (!call.requirePermission("staff.manage")) return@post
                val req = call.receive<CreateGiftCardRequest>()
                val now = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()
                val code = req.code.trim().uppercase()

                val exists = transaction { GiftCardsTable.selectAll().where { GiftCardsTable.code eq code }.count() > 0 }
                if (exists) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("卡号「$code」已存在"))
                    return@post
                }

                transaction {
                    GiftCardsTable.insert {
                        it[GiftCardsTable.id] = id
                        it[GiftCardsTable.code] = code
                        it[balanceMinorUnit] = req.initialBalanceMinorUnit
                        it[customerId] = req.customerId
                        it[isActive] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    if (req.initialBalanceMinorUnit != 0L) {
                        GiftCardTransactionsTable.insert {
                            it[GiftCardTransactionsTable.id] = UUID.randomUUID().toString()
                            it[giftCardId] = id
                            it[type] = "ISSUE"
                            it[amountMinorUnit] = req.initialBalanceMinorUnit
                            it[note] = "开卡"
                            it[createdAt] = now
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id, "code" to code))
            }

            // GET /admin/gift-cards/{id}
            get("/{id}") {
                val id = call.parameters["id"]!!
                val card = transaction {
                    GiftCardsTable.selectAll().where { GiftCardsTable.id eq id }.singleOrNull()?.toGiftCardDto()
                }
                if (card == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Gift card not found"))
                else call.respond(card)
            }

            // GET /admin/gift-cards/{id}/transactions
            get("/{id}/transactions") {
                val id = call.parameters["id"]!!
                val txns = transaction {
                    GiftCardTransactionsTable.selectAll()
                        .where { GiftCardTransactionsTable.giftCardId eq id }
                        .orderBy(GiftCardTransactionsTable.createdAt to SortOrder.DESC)
                        .map { it.toGiftCardTransactionDto() }
                }
                call.respond(txns)
            }

            // PATCH /admin/gift-cards/{id} — toggle active
            patch("/{id}") {
                if (!call.requirePermission("staff.manage")) return@patch
                val id = call.parameters["id"]!!
                val req = call.receive<Map<String, Boolean>>()
                val updated = transaction {
                    GiftCardsTable.update({ GiftCardsTable.id eq id }) { stmt ->
                        req["isActive"]?.let { stmt[isActive] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Gift card not found"))
                else call.respond(mapOf("updated" to true))
            }

            // POST /admin/gift-cards/{id}/topup — manual recharge / adjustment (amount can be negative)
            post("/{id}/topup") {
                if (!call.requirePermission("staff.manage")) return@post
                val id = call.parameters["id"]!!
                val req = call.receive<GiftCardTopUpRequest>()
                val now = System.currentTimeMillis()

                val result = transaction {
                    val card = GiftCardsTable.selectAll().where { GiftCardsTable.id eq id }.singleOrNull()
                        ?: return@transaction null
                    val newBalance = card[GiftCardsTable.balanceMinorUnit] + req.amountMinorUnit
                    if (newBalance < 0) return@transaction "INSUFFICIENT"

                    GiftCardsTable.update({ GiftCardsTable.id eq id }) {
                        it[balanceMinorUnit] = newBalance
                        it[updatedAt] = now
                    }
                    GiftCardTransactionsTable.insert {
                        it[GiftCardTransactionsTable.id] = UUID.randomUUID().toString()
                        it[giftCardId] = id
                        it[type] = if (req.amountMinorUnit >= 0) "TOPUP" else "ADJUST"
                        it[amountMinorUnit] = req.amountMinorUnit
                        it[note] = req.note
                        it[createdAt] = now
                    }
                    "OK"
                }
                when (result) {
                    null -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Gift card not found"))
                    "INSUFFICIENT" -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("余额不足"))
                    else -> call.respond(mapOf("ok" to true))
                }
            }

            // POST /admin/gift-cards/redeem — checkout: deduct balance by code
            post("/redeem") {
                if (!call.requirePermission("payment.process")) return@post
                val req = call.receive<GiftCardRedeemRequest>()
                val now = System.currentTimeMillis()
                val code = req.code.trim().uppercase()

                if (req.amountMinorUnit <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("金额无效"))
                    return@post
                }

                val result = transaction {
                    val card = GiftCardsTable.selectAll().where { GiftCardsTable.code eq code }.singleOrNull()
                        ?: return@transaction "NOT_FOUND"
                    if (!card[GiftCardsTable.isActive]) return@transaction "INACTIVE"
                    val balance = card[GiftCardsTable.balanceMinorUnit]
                    if (balance < req.amountMinorUnit) return@transaction "INSUFFICIENT"

                    val id = card[GiftCardsTable.id]
                    GiftCardsTable.update({ GiftCardsTable.id eq id }) {
                        it[balanceMinorUnit] = balance - req.amountMinorUnit
                        it[updatedAt] = now
                    }
                    GiftCardTransactionsTable.insert {
                        it[GiftCardTransactionsTable.id] = UUID.randomUUID().toString()
                        it[giftCardId] = id
                        it[type] = "REDEEM"
                        it[amountMinorUnit] = -req.amountMinorUnit
                        it[orderId] = req.orderId
                        it[operatorId] = req.operatorId
                        it[note] = req.note
                        it[createdAt] = now
                    }
                    "OK:${balance - req.amountMinorUnit}"
                }

                when {
                    result == "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, ErrorResponse("卡号不存在"))
                    result == "INACTIVE" -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("该卡已停用"))
                    result == "INSUFFICIENT" -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("余额不足"))
                    result?.startsWith("OK:") == true ->
                        call.respond(mapOf("ok" to true, "remainingBalanceMinorUnit" to result.removePrefix("OK:").toLong()))
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

private fun ResultRow.toGiftCardDto() = GiftCardDto(
    id = this[GiftCardsTable.id],
    code = this[GiftCardsTable.code],
    balanceMinorUnit = this[GiftCardsTable.balanceMinorUnit],
    customerId = this[GiftCardsTable.customerId],
    isActive = this[GiftCardsTable.isActive],
    createdAt = this[GiftCardsTable.createdAt],
    updatedAt = this[GiftCardsTable.updatedAt],
)

private fun ResultRow.toGiftCardTransactionDto() = GiftCardTransactionDto(
    id = this[GiftCardTransactionsTable.id],
    giftCardId = this[GiftCardTransactionsTable.giftCardId],
    type = this[GiftCardTransactionsTable.type],
    amountMinorUnit = this[GiftCardTransactionsTable.amountMinorUnit],
    orderId = this[GiftCardTransactionsTable.orderId],
    operatorId = this[GiftCardTransactionsTable.operatorId],
    note = this[GiftCardTransactionsTable.note],
    createdAt = this[GiftCardTransactionsTable.createdAt],
)
