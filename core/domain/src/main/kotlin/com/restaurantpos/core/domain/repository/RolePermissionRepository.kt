package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.PermissionKey
import com.restaurantpos.core.model.Role
import com.restaurantpos.core.model.RolePermission

/**
 * 角色权限数据源（Batch 46 — RBAC 可配置化）。
 *
 * 实现：
 * - Android: [RoomRolePermissionRepository]（查 Room `role_permissions` 表）
 * - 服务端：直接查 PostgreSQL（不通过此接口）
 */
interface RolePermissionRepository {

    /** 查询所有内置角色 */
    suspend fun getAllRoles(): List<Role>

    /** 查询某角色拥有的所有权限 key */
    suspend fun getPermissionKeys(roleId: String): List<String>

    /** 检查某角色是否有某权限 */
    suspend fun hasPermission(roleId: String, permissionKey: PermissionKey): Boolean

    /** 整量替换某角色的权限映射 */
    suspend fun replacePermissions(roleId: String, permissionKeys: List<String>)

    /** 获取所有权限映射（同步用） */
    suspend fun getAllMappings(): List<RolePermission>
}
