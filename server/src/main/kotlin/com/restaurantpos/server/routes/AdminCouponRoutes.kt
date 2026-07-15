package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.CouponsTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminCouponRoutes() {
    authenticate("jwt") {
        route("/admin/coupons") {

            get {
                val coupons = transaction {
                    CouponsTable.selectAll()
                        .orderBy(CouponsTable.expiresAt to SortOrder.DESC)
                        .map { it.toCouponDto() }
                }
                call.respond(coupons)
            }

            post {
                val req = call.receive<CreateCouponRequest>()
                transaction {
                    CouponsTable.insert {
                        it[id] = req.id
                        it[code] = req.code
                        it[type] = req.type
                        it[value] = req.value
                        it[expiresAt] = req.expiresAt
                        it[isActive] = true
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            patch("/{id}") {
                val couponId = call.parameters["id"]!!
                val req = call.receive<UpdateCouponRequest>()
                val updated = transaction {
                    CouponsTable.update({ CouponsTable.id eq couponId }) { stmt ->
                        req.code?.let { stmt[code] = it }
                        req.type?.let { stmt[type] = it }
                        req.value?.let { stmt[value] = it }
                        req.expiresAt?.let { stmt[expiresAt] = it }
                        req.isActive?.let { stmt[isActive] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Coupon not found"))
                else call.respond(mapOf("updated" to true))
            }
        }
    }
}

private fun ResultRow.toCouponDto() = CouponDto(
    id = this[CouponsTable.id],
    code = this[CouponsTable.code],
    type = this[CouponsTable.type],
    value = this[CouponsTable.value],
    expiresAt = this[CouponsTable.expiresAt],
    isActive = this[CouponsTable.isActive],
)
