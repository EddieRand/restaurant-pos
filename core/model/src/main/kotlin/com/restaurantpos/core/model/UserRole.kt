package com.restaurantpos.core.model

/**
 * @deprecated 替换为 [Role] data class + [RolePermission] 映射表。
 *
 * 迁移路径：
 * 1. DB Migration：将 `UserEntity.role` 从 `UserRole` 枚举名（`ADMIN`/`MANAGER`/`CASHIER`/`WAITER`）
 *    转为小写 `roleId` 字符串（`"admin"`/`"manager"`/`"cashier"`/`"waiter"`）
 * 2. `User.role: UserRole` → `User.roleId: String`
 * 3. `CheckPermissionUseCase` 改为查 `role_permissions` 表，不再调用 [permissions] 扩展属性
 * 4. 旧权限映射（[permissions]）仅在 Migration 时用于写入默认 `role_permissions` 种子数据
 */
@Deprecated(
    message = "替换为 Role + RolePermission，见 PermissionKey / Role / RolePermission",
    replaceWith = ReplaceWith("Role / RolePermission")
)
enum class UserRole { ADMIN, MANAGER, CASHIER, WAITER }
