package com.restaurantpos.core.model

/**
 * 角色→权限映射（DB 持久化，替代旧 [UserRole.permissions] 扩展属性）。
 *
 * 每行记录一个角色对一个权限位的授权关系。
 * 查询某角色所有权限：`SELECT permissionKey FROM role_permissions WHERE roleId = ?`
 * 检查某角色是否有某权限：`SELECT EXISTS(...)`
 *
 * @see PermissionKey 权限位枚举（27 个，字符串 key 如 "order.create"）
 * @see Role 角色实体
 * @see com.restaurantpos.core.database.RolePermissionDao
 */
data class RolePermission(
    val roleId: String,        // 对应 Role.id（如 "admin"）
    val permissionKey: String,  // 对应 PermissionKey.key（如 "order.create"）
)
