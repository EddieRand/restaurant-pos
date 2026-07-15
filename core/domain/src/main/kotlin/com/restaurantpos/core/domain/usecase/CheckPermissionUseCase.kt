package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.model.PermissionKey
import com.restaurantpos.core.model.User

/**
 * 细粒度权限检查（Batch 46 — RBAC 可配置化）。
 *
 * 从 [RolePermissionRepository] 查 DB 判断用户权限，不再使用旧硬编码 [UserRole.permissions]。
 *
 * @param user          已登录用户（null = NotLoggedIn）
 * @param permissionKey 所需权限位（如 [PermissionKey.STAFF_MANAGE]）
 */
class CheckPermissionUseCase(
    private val rolePermissionRepo: RolePermissionRepository,
) {
    sealed interface Result {
        data object Allowed : Result
        data object Denied : Result
        data object NotLoggedIn : Result
    }

    suspend operator fun invoke(user: User?, permissionKey: PermissionKey): Result = when {
        user == null            -> Result.NotLoggedIn
        !user.isActive          -> Result.Denied
        else -> {
            val allowed = rolePermissionRepo.hasPermission(user.roleId, permissionKey)
            if (allowed) Result.Allowed else Result.Denied
        }
    }
}
