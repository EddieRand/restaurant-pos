package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.JwtConfig
import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.db.tables.RolePermissionsTable
import com.restaurantpos.server.db.tables.RolesTable
import com.restaurantpos.server.db.tables.UsersTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/* ── Role & Permission management routes (Batch 46 + 47) ──────── */

fun Route.adminRoleRoutes() {
    authenticate("jwt") {
        route("/admin/roles") {

            /* ── GET /admin/roles — list all roles with employee count ── */
            get {
                if (!call.requirePermission("staff.roles")) return@get
                val roles = transaction {
                    val userCounts = UsersTable
                        .select(UsersTable.role, UsersTable.id.count())
                        .groupBy(UsersTable.role)
                        .associate { it[UsersTable.role] to it[UsersTable.id.count()].toInt() }

                    RolesTable.selectAll()
                        .orderBy(RolesTable.sortOrder to SortOrder.ASC)
                        .map { row ->
                            val roleId = row[RolesTable.id]
                            RoleDto(
                                id          = roleId,
                                displayName = row[RolesTable.displayName],
                                isBuiltin   = row[RolesTable.isBuiltin],
                                sortOrder   = row[RolesTable.sortOrder],
                                userCount   = userCounts[roleId] ?: 0,
                            )
                        }
                }
                call.respond(roles)
            }

            /* ── POST /admin/roles — create a custom role ── */
            post {
                if (!call.requirePermission("staff.roles")) return@post
                val body = call.receive<CreateRoleRequest>()
                val trimmedId = body.id.trim().lowercase()

                if (trimmedId.isBlank() || !trimmedId.matches(Regex("^[a-z][a-z0-9_-]*$"))) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "角色 ID 只能包含小写字母、数字、下划线和连字符，且必须以字母开头"))
                    return@post
                }
                if (body.displayName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "角色名称不能为空"))
                    return@post
                }

                val conflict = transaction {
                    val exists = RolesTable.selectAll().where { RolesTable.id eq trimmedId }.count() > 0
                    if (exists) return@transaction true
                    val maxSort = RolesTable.selectAll().maxOfOrNull { it[RolesTable.sortOrder] } ?: 0
                    RolesTable.insert {
                        it[id]          = trimmedId
                        it[displayName] = body.displayName.trim()
                        it[isBuiltin]  = false
                        it[sortOrder]   = maxSort + 1
                    }
                    false
                }

                if (conflict) {
                    call.respond(HttpStatusCode.Conflict, mapOf("message" to "角色 ID「$trimmedId」已存在"))
                    return@post
                }
                call.respond(
                    HttpStatusCode.Created,
                    RoleDto(id = trimmedId, displayName = body.displayName.trim(), isBuiltin = false, sortOrder = 0, userCount = 0),
                )
            }

            /* ── PUT /admin/roles/{id} — update role display name ── */
            put("/{id}") {
                if (!call.requirePermission("staff.roles")) return@put
                val roleId = call.parameters["id"]!!
                val body = call.receive<UpdateRoleRequest>()
                if (body.displayName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "角色名称不能为空"))
                    return@put
                }
                val updated = transaction {
                    RolesTable.update({ RolesTable.id eq roleId }) {
                        it[displayName] = body.displayName.trim()
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, mapOf("message" to "角色不存在"))
                else call.respond(mapOf("updated" to true))
            }

            /* ── DELETE /admin/roles/{id} — delete a non-builtin role ── */
            delete("/{id}") {
                if (!call.requirePermission("staff.roles")) return@delete
                val roleId = call.parameters["id"]!!

                // Perform all DB operations in a single transaction, return the result
                data class DeleteResult(
                    val notFound: Boolean = false,
                    val isBuiltin: Boolean = false,
                    val userCount: Int = 0,
                    val deleted: Boolean = false,
                )

                val result = transaction {
                    val role = RolesTable.selectAll().where { RolesTable.id eq roleId }.firstOrNull()
                    if (role == null) return@transaction DeleteResult(notFound = true)
                    if (role[RolesTable.isBuiltin]) return@transaction DeleteResult(isBuiltin = true)
                    val uc = UsersTable.selectAll().where { UsersTable.role eq roleId }.count().toInt()
                    if (uc > 0) return@transaction DeleteResult(userCount = uc)
                    RolePermissionsTable.deleteWhere { RolePermissionsTable.roleId eq roleId }
                    RolesTable.deleteWhere { RolesTable.id eq roleId }
                    DeleteResult(deleted = true)
                }

                when {
                    result.notFound -> call.respond(HttpStatusCode.NotFound, mapOf("message" to "角色不存在"))
                    result.isBuiltin -> call.respond(HttpStatusCode.Forbidden, mapOf("message" to "内置角色不可删除"))
                    result.userCount > 0 -> call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("message" to "该角色下还有 ${result.userCount} 名员工，请先将他们切换到其他角色后再删除", "userCount" to result.userCount),
                    )
                    else -> call.respond(mapOf("deleted" to true))
                }
            }

            /* ── GET /admin/roles/{id}/permissions ── */
            get("/{id}/permissions") {
                if (!call.requirePermission("staff.roles")) return@get
                val roleId = call.parameters["id"]!!
                val permissions = transaction {
                    RolePermissionsTable
                        .selectAll()
                        .where { RolePermissionsTable.roleId eq roleId }
                        .map    { it[RolePermissionsTable.permissionKey] }
                }
                call.respond(mapOf("roleId" to roleId, "permissions" to permissions))
            }

            /* ── PUT /admin/roles/{id}/permissions ── */
            put("/{id}/permissions") {
                if (!call.requirePermission("staff.roles")) return@put
                val roleId = call.parameters["id"]!!
                val body = call.receive<RolePermissionsUpdateRequest>()

                // Prevent self-lockout: caller cannot remove their own "staff.roles"
                val callerRoleId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim(JwtConfig.CLAIM_ROLE)
                    ?.asString()
                    ?.lowercase()
                if (callerRoleId == roleId && !body.permissions.contains("staff.roles")) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("message" to "Cannot remove your own staff.roles permission"),
                    )
                    return@put
                }

                transaction {
                    RolePermissionsTable.deleteWhere { RolePermissionsTable.roleId eq roleId }
                    body.permissions.forEach { key ->
                        RolePermissionsTable.insert {
                            it[RolePermissionsTable.roleId]        = roleId
                            it[RolePermissionsTable.permissionKey] = key
                        }
                    }
                }
                call.respond(mapOf("updated" to true))
            }
        }
    }
}

/* ── DTOs ──────────────────────────────────────────────────────── */

@Serializable
data class RoleDto(
    val id:          String,
    val displayName: String,
    val isBuiltin:  Boolean,
    val sortOrder:    Int,
    val userCount:   Int = 0,
)

@Serializable
data class CreateRoleRequest(
    val id: String,
    val displayName: String,
)

@Serializable
data class UpdateRoleRequest(
    val displayName: String?,
)

@Serializable
data class RolePermissionsUpdateRequest(
    val permissions: List<String>,
)
