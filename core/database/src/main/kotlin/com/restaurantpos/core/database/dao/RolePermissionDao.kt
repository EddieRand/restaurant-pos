package com.restaurantpos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.restaurantpos.core.database.entity.RolePermissionEntity

/**
 * `role_permissions` 表数据访问接口。
 *
 * 角色→权限映射是纯关系表，无额外字段，
 * 所有操作均以 `roleId` + `permissionKey` 复合主键为条件。
 *
 * @see com.restaurantpos.core.database.entity.RolePermissionEntity
 * @see com.restaurantpos.core.model.RolePermission
 */
@Dao
interface RolePermissionDao {

    /* ── 写入 ──────────────────────────────────────────────── */

    /** 插入单条映射（幂等，REPLACE 覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RolePermissionEntity)

    /** 批量插入（幂等） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RolePermissionEntity>)

    /**
     * 整量替换某角色的全部权限（事务）。
     *
     * 典型用法：Web Admin 权限矩阵保存时，
     * 先删除该角色所有旧映射，再批量插入新映射。
     *
     * @param roleId       角色 ID（如 "manager"）
     * @param permissions  新的权限 key 列表（如 ["order.create", "payment.process"]）
     */
    @Transaction
    suspend fun replaceAllForRole(roleId: String, permissions: List<String>) {
        deleteByRoleId(roleId)
        if (permissions.isNotEmpty()) {
            insertAll(permissions.map { RolePermissionEntity(roleId, it) })
        }
    }

    /* ── 查询 ──────────────────────────────────────────────── */

    /** 查询某角色拥有的全部权限 key */
    @Query("SELECT permissionKey FROM role_permissions WHERE roleId = :roleId")
    suspend fun getPermissionKeysForRole(roleId: String): List<String>

    /**
     * 检查某角色是否有某权限。
     * @return `true` 当且仅当映射表中存在对应行
     */
    @Query(
        """
        SELECT COUNT(*) > 0
        FROM role_permissions
        WHERE roleId = :roleId AND permissionKey = :permissionKey
        LIMIT 1
        """
    )
    suspend fun hasPermission(roleId: String, permissionKey: String): Boolean

    /** 查询所有角色→权限映射（全量同步用） */
    @Query("SELECT * FROM role_permissions ORDER BY roleId, permissionKey")
    suspend fun getAll(): List<RolePermissionEntity>

    /** 查询某角色的全部映射实体 */
    @Query("SELECT * FROM role_permissions WHERE roleId = :roleId ORDER BY permissionKey")
    suspend fun getByRoleId(roleId: String): List<RolePermissionEntity>

    /* ── 删除 ──────────────────────────────────────────────── */

    @Query("DELETE FROM role_permissions WHERE roleId = :roleId AND permissionKey = :permissionKey")
    suspend fun delete(roleId: String, permissionKey: String)

    @Query("DELETE FROM role_permissions WHERE roleId = :roleId")
    suspend fun deleteByRoleId(roleId: String)

    @Query("DELETE FROM role_permissions")
    suspend fun deleteAll()
}
