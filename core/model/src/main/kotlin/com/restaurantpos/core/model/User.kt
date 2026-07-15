package com.restaurantpos.core.model

/**
 * 领域用户模型。
 *
 * @property roleId 角色 ID（小写，如 `"admin"` / `"manager"` / `"cashier"` / `"waiter"`），
 *                   对应 [Role.id，不再使用 [UserRole] 枚举。
 * @see Role
 * @see RolePermission
 * @see com.restaurantpos.core.database.entity.UserEntity
 */
data class User(
    val id: String,
    val displayName: String,
    val roleId: String,
    val pinHash: String,
    val isActive: Boolean = true,
    val createdAt: Long,
)
