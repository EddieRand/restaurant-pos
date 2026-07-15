package com.restaurantpos.core.model

/**
 * 可配置角色（DB 持久化，不再硬编码为枚举）。
 *
 * 与旧 [UserRole] 枚举的区别：
 * - 存放在 `roles` 表，管理员可通过 Web Admin 调整权限映射
 * - `isBuiltin = true` 的内置角色不允许删除，但权限可调整
 * - `sortOrder` 控制 Web Admin 侧边栏/下拉框中的排序
 *
 * @see RolePermission 角色→权限映射
 * @see com.restaurantpos.core.database.RoleDao
 */
data class Role(
    val id: String,          // "admin" / "manager" / "cashier" / "waiter"（与旧 UserRole 枚举名对齐）
    val displayName: String,  // 显示名（多语言 key，如 "role.admin"）
    val isBuiltin: Boolean,   // 内置角色 → 不允许删除
    val sortOrder: Int,       // 排序（升序）
)
