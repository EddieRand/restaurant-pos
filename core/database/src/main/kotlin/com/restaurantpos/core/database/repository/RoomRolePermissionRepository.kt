package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.RoleDao
import com.restaurantpos.core.database.dao.RolePermissionDao
import com.restaurantpos.core.database.entity.RoleEntity
import com.restaurantpos.core.database.entity.RolePermissionEntity
import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.model.PermissionKey
import com.restaurantpos.core.model.Role
import com.restaurantpos.core.model.RolePermission

/**
 * Room 实现的 [RolePermissionRepository]（Batch 46）。
 *
 * 所有权限数据从本地 Room DB 读取，通过 SyncEngine 从服务端下行同步。
 */
class RoomRolePermissionRepository(
    private val roleDao: RoleDao,
    private val rolePermissionDao: RolePermissionDao,
) : RolePermissionRepository {

    override suspend fun getAllRoles(): List<Role> =
        roleDao.getAll().map { it.toDomain() }

    override suspend fun getPermissionKeys(roleId: String): List<String> =
        rolePermissionDao.getPermissionKeysForRole(roleId)

    override suspend fun hasPermission(roleId: String, permissionKey: PermissionKey): Boolean =
        rolePermissionDao.hasPermission(roleId, permissionKey.key)

    override suspend fun replacePermissions(roleId: String, permissionKeys: List<String>) {
        rolePermissionDao.replaceAllForRole(roleId, permissionKeys)
    }

    override suspend fun getAllMappings(): List<RolePermission> =
        rolePermissionDao.getAll().map { it.toDomain() }
}

private fun RoleEntity.toDomain() = Role(
    id          = id,
    displayName = displayName,
    isBuiltin  = isBuiltin,
    sortOrder   = sortOrder,
)

private fun RolePermissionEntity.toDomain() = RolePermission(
    roleId       = roleId,
    permissionKey = permissionKey,
)
