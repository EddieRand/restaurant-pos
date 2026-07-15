package com.restaurantpos.server.auth

import com.restaurantpos.server.db.tables.RolePermissionsTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * RoleGuard — 权限校验辅助函数（Batch 48）。
 *
 * 在每个 route handler 内部调用，确保在 authenticate("jwt") 之后执行：
 * ```kotlin
 * authenticate("jwt") {
 *     route("/admin/users") {
 *         get {
 *             if (!call.requirePermission("staff.manage")) return@get
 *             // handler body ...
 *         }
 *     }
 * }
 * ```
 *
 * 行为：
 * 1. 从 JWTPrincipal 提取 roleId（`JwtConfig.CLAIM_ROLE` 字段）
 * 2. 查 `role_permissions` 表是否有 `(roleId, requiredPermission)` 映射
 * 3. 无权限 → 403 `{"requiredPermission":"staff.manage"}`
 *
 * @return true = 有权限可继续, false = 已响应 403 应 return
 */
suspend fun ApplicationCall.requirePermission(permissionKey: String): Boolean {
    val jwtPrincipal = principal<JWTPrincipal>()
    val roleId = jwtPrincipal?.payload
        ?.getClaim(JwtConfig.CLAIM_ROLE)
        ?.asString()
        ?.lowercase()

    if (roleId.isNullOrBlank()) {
        respond(HttpStatusCode.Forbidden, mapOf("requiredPermission" to permissionKey))
        return false
    }

    val allowed = transaction {
        RolePermissionsTable
            .selectAll()
            .where {
                (RolePermissionsTable.roleId eq roleId) and
                    (RolePermissionsTable.permissionKey eq permissionKey)
            }
            .count() > 0
    }

    if (!allowed) {
        respond(HttpStatusCode.Forbidden, mapOf("requiredPermission" to permissionKey))
        return false
    }

    return true
}
