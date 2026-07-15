package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色→权限映射表（`role_permissions`）。
 *
 * 每行 = 一个角色对一个权限位的授权。
 * 复合主键：`(roleId, permissionKey)` 保证唯一性。
 *
 * 查询某角色所有权限：
 * ```sql
 * SELECT permissionKey FROM role_permissions WHERE roleId = ?
 * ```
 *
 * 检查某角色是否有某权限：
 * ```sql
 * SELECT COUNT(*) > 0 FROM role_permissions WHERE roleId = ? AND permissionKey = ?
 * ```
 *
 * @see com.restaurantpos.core.model.RolePermission 领域模型
 * @see RolePermissionDao
 * @see com.restaurantpos.core.model.PermissionKey 权限位枚举（27 个，字符串 key 如 "order.create"）
 */
@Entity(
    tableName = "role_permissions",
    primaryKeys = ["roleId", "permissionKey"],
    indices = [
        Index(value = ["roleId"]),
        Index(value = ["permissionKey"]),
    ]
)
data class RolePermissionEntity(
    val roleId: String,       // 对应 roles.id（如 "admin"）
    val permissionKey: String, // 对应 PermissionKey.key（如 "order.create"）
)
