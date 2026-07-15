package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.db.tables.RolesTable
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
import java.security.MessageDigest

fun Route.adminUserRoutes() {
    authenticate("jwt") {
        route("/admin/users") {

            get {
                if (!call.requirePermission("staff.manage")) return@get
                val users = transaction {
                    // LEFT JOIN with roles table to get display names
                    UsersTable.join(RolesTable, JoinType.LEFT, additionalConstraint = { UsersTable.role eq RolesTable.id })
                        .selectAll()
                        .orderBy(UsersTable.createdAt to SortOrder.DESC)
                        .map { it.toPosUserDto() }
                }
                call.respond(users)
            }

            post {
                if (!call.requirePermission("staff.manage")) return@post
                val req = call.receive<CreatePosUserRequest>()
                val now = System.currentTimeMillis()

                // Validate role exists
                val validRole = transaction {
                    RolesTable.selectAll().where { RolesTable.id eq req.role.lowercase() }.count() > 0
                }
                if (!validRole) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("角色「${req.role}」不存在，请选择有效角色"))
                    return@post
                }

                transaction {
                    UsersTable.insert {
                        it[id] = req.id
                        it[displayName] = req.displayName
                        it[role] = req.role.lowercase()
                        it[pinHash] = sha256(req.pin)
                        it[isActive] = true
                        it[createdAt] = now
                        it[hourlyWageMinorUnit] = req.hourlyWageMinorUnit
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            patch("/{id}") {
                if (!call.requirePermission("staff.manage")) return@patch
                val userId = call.parameters["id"]!!
                val req = call.receive<UpdatePosUserRequest>()

                // Validate role if provided
                if (req.role != null) {
                    val validRole = transaction {
                        RolesTable.selectAll().where { RolesTable.id eq req.role.lowercase() }.count() > 0
                    }
                    if (!validRole) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("角色「${req.role}」不存在"))
                        return@patch
                    }
                }

                val updated = transaction {
                    UsersTable.update({ UsersTable.id eq userId }) { stmt ->
                        req.displayName?.let { stmt[displayName] = it }
                        req.role?.let { stmt[role] = it.lowercase() }
                        req.pin?.let { stmt[pinHash] = sha256(it) }
                        req.isActive?.let { stmt[isActive] = it }
                        req.hourlyWageMinorUnit?.let { stmt[hourlyWageMinorUnit] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                else call.respond(mapOf("updated" to true))
            }
        }
    }
}

private fun ResultRow.toPosUserDto(): PosUserDto {
    val roleId = this[UsersTable.role]
    // Fetch role display name from joined RolesTable (may be null if role deleted)
    val roleDisplayName = try {
        this[RolesTable.displayName]
    } catch (_: Exception) {
        roleId  // fallback: use roleId as display name
    }
    return PosUserDto(
        id               = this[UsersTable.id],
        displayName      = this[UsersTable.displayName],
        role             = roleId,
        roleDisplayName  = roleDisplayName,
        isActive         = this[UsersTable.isActive],
        createdAt        = this[UsersTable.createdAt],
        hourlyWageMinorUnit = this[UsersTable.hourlyWageMinorUnit],
    )
}

private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
