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
import java.util.UUID

fun Route.adminCrmRoutes() {
    authenticate("jwt") {

        // ── Customers ─────────────────────────────────────────────────────────

        route("/admin/customers") {

            get {
                val q = call.request.queryParameters["q"]
                val tierId = call.request.queryParameters["tierId"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val pageSize = 50

                val customers = transaction {
                    var query = CustomersTable.selectAll()
                    if (!q.isNullOrBlank()) {
                        query = query.where {
                            (CustomersTable.name like "%$q%") or (CustomersTable.phone like "%$q%")
                        }
                    }
                    if (!tierId.isNullOrBlank()) {
                        query = query.andWhere { CustomersTable.membershipTierId eq tierId }
                    }
                    query.orderBy(CustomersTable.registeredAt, SortOrder.DESC)
                        .limit(pageSize, offset = (page * pageSize).toLong())
                        .map { it.toCustomerDto() }
                }
                call.respond(customers)
            }

            post {
                val req = call.receive<CreateCustomerRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    CustomersTable.insert {
                        it[id] = req.id
                        it[name] = req.name
                        it[phone] = req.phone
                        it[email] = req.email
                        it[gender] = req.gender
                        it[birthday] = req.birthday
                        it[tags] = req.tags.joinToString("|")
                        it[notes] = req.notes
                        it[registeredAt] = now
                        it[lastVisitAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            get("/{id}") {
                val cid = call.parameters["id"]!!
                val customer = transaction {
                    CustomersTable.selectAll().where { CustomersTable.id eq cid }
                        .singleOrNull()?.toCustomerDto()
                }
                if (customer == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Customer not found"))
                else call.respond(customer)
            }

            patch("/{id}") {
                val cid = call.parameters["id"]!!
                val req = call.receive<UpdateCustomerRequest>()
                val updated = transaction {
                    CustomersTable.update({ CustomersTable.id eq cid }) { stmt ->
                        req.name?.let { stmt[name] = it }
                        req.phone?.let { stmt[phone] = it }
                        req.email.let { stmt[email] = it }         // allow null clear
                        req.gender.let { stmt[gender] = it }
                        req.birthday.let { stmt[birthday] = it }
                        req.tags?.let { stmt[tags] = it.joinToString("|") }
                        req.notes.let { stmt[notes] = it }
                        req.membershipTierId.let { stmt[membershipTierId] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Customer not found"))
                else call.respond(mapOf("updated" to true))
            }

            delete("/{id}") {
                val cid = call.parameters["id"]!!
                transaction { CustomersTable.deleteWhere { id eq cid } }
                call.respond(HttpStatusCode.NoContent)
            }

            // ── Loyalty transactions for a customer ───────────────────────────

            get("/{id}/loyalty-transactions") {
                val cid = call.parameters["id"]!!
                val txns = transaction {
                    LoyaltyTransactionsTable.selectAll()
                        .where { LoyaltyTransactionsTable.customerId eq cid }
                        .orderBy(LoyaltyTransactionsTable.createdAt, SortOrder.DESC)
                        .map { it.toLoyaltyTxnDto() }
                }
                call.respond(txns)
            }

            // Manual point adjustment (ADJUST transaction)
            post("/{id}/adjust-points") {
                val cid = call.parameters["id"]!!
                val req = call.receive<AdjustPointsRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    val existing = CustomersTable.selectAll()
                        .where { CustomersTable.id eq cid }
                        .singleOrNull() ?: return@transaction
                    val newPoints = (existing[CustomersTable.loyaltyPoints] + req.delta).coerceAtLeast(0L)
                    CustomersTable.update({ CustomersTable.id eq cid }) { it[loyaltyPoints] = newPoints }
                    LoyaltyTransactionsTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[customerId] = cid
                        it[type] = "ADJUST"
                        it[points] = req.delta
                        it[description] = req.description
                        it[createdAt] = now
                    }
                }
                call.respond(mapOf("ok" to true))
            }
        }

        // ── Membership Tiers ──────────────────────────────────────────────────

        route("/admin/loyalty/tiers") {

            get {
                val tiers = transaction {
                    MembershipTiersTable.selectAll()
                        .orderBy(MembershipTiersTable.sortOrder, SortOrder.ASC)
                        .map { it.toTierDto() }
                }
                call.respond(tiers)
            }

            post {
                val req = call.receive<CreateMembershipTierRequest>()
                transaction {
                    MembershipTiersTable.insert {
                        it[id] = req.id
                        it[name] = req.name
                        it[minPoints] = req.minPoints
                        it[benefits] = req.benefits.joinToString("|")
                        it[color] = req.color
                        it[discountPermille] = req.discountPermille
                        it[pointsMultiplier] = req.pointsMultiplier
                        it[sortOrder] = req.sortOrder
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            patch("/{id}") {
                val tid = call.parameters["id"]!!
                val req = call.receive<UpdateMembershipTierRequest>()
                val updated = transaction {
                    MembershipTiersTable.update({ MembershipTiersTable.id eq tid }) { stmt ->
                        req.name?.let { stmt[name] = it }
                        req.minPoints?.let { stmt[minPoints] = it }
                        req.benefits?.let { stmt[benefits] = it.joinToString("|") }
                        req.color?.let { stmt[color] = it }
                        req.discountPermille?.let { stmt[discountPermille] = it }
                        req.pointsMultiplier?.let { stmt[pointsMultiplier] = it }
                        req.sortOrder?.let { stmt[sortOrder] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Tier not found"))
                else call.respond(mapOf("updated" to true))
            }

            delete("/{id}") {
                val tid = call.parameters["id"]!!
                transaction { MembershipTiersTable.deleteWhere { id eq tid } }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── Campaigns ─────────────────────────────────────────────────────────

        route("/admin/campaigns") {

            get {
                val campaigns = transaction {
                    CampaignsTable.selectAll()
                        .orderBy(CampaignsTable.createdAt, SortOrder.DESC)
                        .map { it.toCampaignDto() }
                }
                call.respond(campaigns)
            }

            post {
                val req = call.receive<CreateCampaignRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    CampaignsTable.insert {
                        it[id] = req.id
                        it[name] = req.name
                        it[type] = req.type
                        it[targetSegment] = req.targetSegment
                        it[targetTierId] = req.targetTierId
                        it[couponId] = req.couponId
                        it[message] = req.message
                        it[scheduledAt] = req.scheduledAt
                        it[status] = "DRAFT"
                        it[sentCount] = 0
                        it[createdAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            patch("/{id}") {
                val cid = call.parameters["id"]!!
                val req = call.receive<UpdateCampaignRequest>()
                val updated = transaction {
                    CampaignsTable.update({ CampaignsTable.id eq cid }) { stmt ->
                        req.name?.let { stmt[name] = it }
                        req.targetSegment?.let { stmt[targetSegment] = it }
                        req.targetTierId.let { stmt[targetTierId] = it }
                        req.couponId.let { stmt[couponId] = it }
                        req.message?.let { stmt[message] = it }
                        req.scheduledAt.let { stmt[scheduledAt] = it }
                        req.status?.let { stmt[status] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Campaign not found"))
                else call.respond(mapOf("updated" to true))
            }

            delete("/{id}") {
                val cid = call.parameters["id"]!!
                transaction { CampaignsTable.deleteWhere { id eq cid } }
                call.respond(HttpStatusCode.NoContent)
            }

            // Mark as SENT (stub — real push would call a notification service)
            post("/{id}/send") {
                val cid = call.parameters["id"]!!
                val now = System.currentTimeMillis()
                transaction {
                    CampaignsTable.update({ CampaignsTable.id eq cid }) {
                        it[status] = "SENT"
                        it[scheduledAt] = now
                    }
                }
                call.respond(mapOf("sent" to true))
            }
        }
    }
}

// ── Result row mappers ────────────────────────────────────────────────────────

private fun ResultRow.toCustomerDto() = CustomerDto(
    id = this[CustomersTable.id],
    name = this[CustomersTable.name],
    phone = this[CustomersTable.phone],
    email = this[CustomersTable.email],
    gender = this[CustomersTable.gender],
    birthday = this[CustomersTable.birthday],
    tags = this[CustomersTable.tags].split("|").filter { it.isNotEmpty() },
    notes = this[CustomersTable.notes],
    totalSpendMinorUnit = this[CustomersTable.totalSpendMinorUnit],
    loyaltyPoints = this[CustomersTable.loyaltyPoints],
    membershipTierId = this[CustomersTable.membershipTierId],
    totalVisits = this[CustomersTable.totalVisits],
    lastVisitAt = this[CustomersTable.lastVisitAt],
    registeredAt = this[CustomersTable.registeredAt],
)

private fun ResultRow.toLoyaltyTxnDto() = LoyaltyTransactionDto(
    id = this[LoyaltyTransactionsTable.id],
    customerId = this[LoyaltyTransactionsTable.customerId],
    orderId = this[LoyaltyTransactionsTable.orderId],
    type = this[LoyaltyTransactionsTable.type],
    points = this[LoyaltyTransactionsTable.points],
    description = this[LoyaltyTransactionsTable.description],
    createdAt = this[LoyaltyTransactionsTable.createdAt],
)

private fun ResultRow.toTierDto() = MembershipTierDto(
    id = this[MembershipTiersTable.id],
    name = this[MembershipTiersTable.name],
    minPoints = this[MembershipTiersTable.minPoints],
    benefits = this[MembershipTiersTable.benefits].split("|").filter { it.isNotEmpty() },
    color = this[MembershipTiersTable.color],
    discountPermille = this[MembershipTiersTable.discountPermille],
    pointsMultiplier = this[MembershipTiersTable.pointsMultiplier],
    sortOrder = this[MembershipTiersTable.sortOrder],
)

private fun ResultRow.toCampaignDto() = CampaignDto(
    id = this[CampaignsTable.id],
    name = this[CampaignsTable.name],
    type = this[CampaignsTable.type],
    targetSegment = this[CampaignsTable.targetSegment],
    targetTierId = this[CampaignsTable.targetTierId],
    couponId = this[CampaignsTable.couponId],
    message = this[CampaignsTable.message],
    scheduledAt = this[CampaignsTable.scheduledAt],
    status = this[CampaignsTable.status],
    sentCount = this[CampaignsTable.sentCount],
    createdAt = this[CampaignsTable.createdAt],
)
