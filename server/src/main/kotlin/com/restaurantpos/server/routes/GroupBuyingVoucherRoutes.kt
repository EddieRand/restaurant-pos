package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.db.tables.GroupBuyingRedemptionsTable
import com.restaurantpos.server.db.tables.GroupBuyingVouchersTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.util.UUID

private val supportedVoucherProviders = setOf("DOUYIN", "MEITUAN")

fun Route.groupBuyingVoucherRoutes() {
    authenticate("jwt") {
        route("/pos/group-buying-vouchers") {
            post("/validate") {
                if (!call.requirePosVoucherPermission()) return@post
                val req = call.receive<GroupBuyingVoucherValidateRequest>()
                val provider = req.provider.trim().uppercase()
                val code = req.code.trim().uppercase()
                if (provider !in supportedVoucherProviders || code.length !in 6..64) {
                    call.respond(HttpStatusCode.UnprocessableEntity, GroupBuyingVoucherErrorResponse("VOUCHER_INVALID_INPUT", "请选择平台并输入有效券码"))
                    return@post
                }
                val result = transaction { findVoucher(provider, code) }
                when {
                    result == null -> call.respond(HttpStatusCode.NotFound, GroupBuyingVoucherErrorResponse("VOUCHER_NOT_FOUND", "未找到该平台团购券"))
                    result.status == "REDEEMED" -> call.respond(HttpStatusCode.Conflict, GroupBuyingVoucherErrorResponse("VOUCHER_ALREADY_REDEEMED", "该团购券已核销"))
                    result.status != "AVAILABLE" -> call.respond(HttpStatusCode.UnprocessableEntity, GroupBuyingVoucherErrorResponse("VOUCHER_UNAVAILABLE", "该团购券当前不可用"))
                    result.expiresAt < System.currentTimeMillis() -> call.respond(HttpStatusCode.Gone, GroupBuyingVoucherErrorResponse("VOUCHER_EXPIRED", "该团购券已过期"))
                    else -> call.respond(result.toDto())
                }
            }

            post("/redeem") {
                if (!call.requirePosVoucherPermission()) return@post
                val req = call.receive<GroupBuyingVoucherRedeemRequest>()
                val provider = req.provider.trim().uppercase()
                val code = req.code.trim().uppercase()
                if (provider !in supportedVoucherProviders || code.length !in 6..64 || req.orderId.isBlank() || req.requestedAmountMinorUnit <= 0 || req.idempotencyKey.isBlank()) {
                    call.respond(HttpStatusCode.UnprocessableEntity, GroupBuyingVoucherErrorResponse("VOUCHER_INVALID_INPUT", "核销参数不完整"))
                    return@post
                }

                val result = transaction {
                    val voucher = findVoucher(provider, code) ?: return@transaction RedeemOutcome.Error("VOUCHER_NOT_FOUND")
                    val replay = GroupBuyingRedemptionsTable.selectAll()
                        .where { GroupBuyingRedemptionsTable.idempotencyKey eq req.idempotencyKey }
                        .singleOrNull()
                    if (replay != null) {
                        if (replay[GroupBuyingRedemptionsTable.voucherId] != voucher.id || replay[GroupBuyingRedemptionsTable.orderId] != req.orderId) {
                            return@transaction RedeemOutcome.Error("VOUCHER_IDEMPOTENCY_CONFLICT")
                        }
                        return@transaction RedeemOutcome.Success(
                            replay[GroupBuyingRedemptionsTable.id],
                            replay[GroupBuyingRedemptionsTable.redeemedAmountMinorUnit],
                            true,
                        )
                    }

                    if (voucher.status == "REDEEMED") return@transaction RedeemOutcome.Error("VOUCHER_ALREADY_REDEEMED")
                    if (voucher.status != "AVAILABLE") return@transaction RedeemOutcome.Error("VOUCHER_UNAVAILABLE")
                    if (voucher.expiresAt < System.currentTimeMillis()) return@transaction RedeemOutcome.Error("VOUCHER_EXPIRED")

                    val redeemedAmount = minOf(voucher.faceValueMinorUnit, req.requestedAmountMinorUnit)
                    val redemptionId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    GroupBuyingRedemptionsTable.insert {
                        it[id] = redemptionId
                        it[GroupBuyingRedemptionsTable.provider] = provider
                        it[voucherId] = voucher.id
                        it[codeLast4] = voucher.codeLast4
                        it[orderId] = req.orderId
                        it[operatorId] = req.operatorId
                        it[redeemedAmountMinorUnit] = redeemedAmount
                        it[idempotencyKey] = req.idempotencyKey
                        it[providerReference] = "${if (voucher.demo) "DEMO" else provider}-${UUID.randomUUID()}"
                        it[status] = "SUCCEEDED"
                        it[demo] = voucher.demo
                        it[createdAt] = now
                    }
                    GroupBuyingVouchersTable.update({ GroupBuyingVouchersTable.id eq voucher.id }) {
                        it[status] = "REDEEMED"
                    }
                    RedeemOutcome.Success(redemptionId, redeemedAmount, false)
                }

                when (result) {
                    is RedeemOutcome.Success -> call.respond(GroupBuyingVoucherRedeemResponse(result.id, result.amount, result.replay))
                    is RedeemOutcome.Error -> {
                        val (status, message) = when (result.code) {
                            "VOUCHER_NOT_FOUND" -> HttpStatusCode.NotFound to "未找到该平台团购券"
                            "VOUCHER_ALREADY_REDEEMED" -> HttpStatusCode.Conflict to "该团购券已核销"
                            "VOUCHER_EXPIRED" -> HttpStatusCode.Gone to "该团购券已过期"
                            "VOUCHER_IDEMPOTENCY_CONFLICT" -> HttpStatusCode.Conflict to "重复请求与原核销内容不一致"
                            else -> HttpStatusCode.UnprocessableEntity to "该团购券当前不可用"
                        }
                        call.respond(status, GroupBuyingVoucherErrorResponse(result.code, message))
                    }
                }
            }
        }

        get("/admin/group-buying-redemptions") {
            if (!call.requirePermission("report.daily")) return@get
            val rows = transaction {
                GroupBuyingRedemptionsTable.join(
                    GroupBuyingVouchersTable,
                    JoinType.INNER,
                    additionalConstraint = { GroupBuyingRedemptionsTable.voucherId eq GroupBuyingVouchersTable.id },
                )
                    .selectAll()
                    .orderBy(GroupBuyingRedemptionsTable.createdAt to SortOrder.DESC)
                    .map { row ->
                        GroupBuyingRedemptionDto(
                            id = row[GroupBuyingRedemptionsTable.id],
                            provider = row[GroupBuyingRedemptionsTable.provider],
                            maskedCode = "****${row[GroupBuyingRedemptionsTable.codeLast4]}",
                            title = row[GroupBuyingVouchersTable.title],
                            orderId = row[GroupBuyingRedemptionsTable.orderId],
                            operatorId = row[GroupBuyingRedemptionsTable.operatorId],
                            redeemedAmountMinorUnit = row[GroupBuyingRedemptionsTable.redeemedAmountMinorUnit],
                            providerReference = row[GroupBuyingRedemptionsTable.providerReference],
                            status = row[GroupBuyingRedemptionsTable.status],
                            demo = row[GroupBuyingRedemptionsTable.demo],
                            createdAt = row[GroupBuyingRedemptionsTable.createdAt],
                        )
                    }
            }
            call.respond(rows)
        }
    }
}

private data class VoucherRecord(
    val id: String,
    val provider: String,
    val codeLast4: String,
    val title: String,
    val faceValueMinorUnit: Long,
    val expiresAt: Long,
    val status: String,
    val demo: Boolean,
) {
    fun toDto() = GroupBuyingVoucherDto(provider, "****$codeLast4", title, faceValueMinorUnit, expiresAt, status, demo)
}

private fun findVoucher(provider: String, code: String): VoucherRecord? =
    GroupBuyingVouchersTable.selectAll()
        .where { (GroupBuyingVouchersTable.provider eq provider) and (GroupBuyingVouchersTable.codeHash eq sha256VoucherCode(code)) }
        .singleOrNull()
        ?.let { row -> VoucherRecord(
            row[GroupBuyingVouchersTable.id], row[GroupBuyingVouchersTable.provider], row[GroupBuyingVouchersTable.codeLast4],
            row[GroupBuyingVouchersTable.title], row[GroupBuyingVouchersTable.faceValueMinorUnit], row[GroupBuyingVouchersTable.expiresAt],
            row[GroupBuyingVouchersTable.status], row[GroupBuyingVouchersTable.demo],
        ) }

private fun sha256VoucherCode(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private sealed class RedeemOutcome {
    data class Success(val id: String, val amount: Long, val replay: Boolean) : RedeemOutcome()
    data class Error(val code: String) : RedeemOutcome()
}

/** Cashier uses a device JWT; admin callers still pass the ordinary RBAC check. */
private suspend fun ApplicationCall.requirePosVoucherPermission(): Boolean {
    val role = principal<JWTPrincipal>()?.payload
        ?.getClaim(JwtConfig.CLAIM_ROLE)?.asString()?.uppercase()
    return role == "TERMINAL" || requirePermission("payment.process")
}
