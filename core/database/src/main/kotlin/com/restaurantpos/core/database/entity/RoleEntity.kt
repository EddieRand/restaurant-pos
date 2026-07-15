package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 可配置角色表（`roles`）。
 *
 * 与旧 [com.restaurantpos.core.model.UserRole] 枚举的区别：
 * - 持久化到 DB，管理员可通过 Web Admin 调整权限映射
 * - `id` 与旧枚举名的小写形式对齐（`"admin"` / `"manager"` / `"cashier"` / `"waiter"`）
 *
 * @see com.restaurantpos.core.model.Role 领域模型
 * @see RoleDao
 */
@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey val id: String,          // "admin" / "manager" / "cashier" / "waiter"
    val displayName: String,              // 多语言 key，如 "role.admin"
    val isBuiltin: Boolean,              // 内置角色 → 不允许删除
    val sortOrder: Int,                  // 升序排列
)
