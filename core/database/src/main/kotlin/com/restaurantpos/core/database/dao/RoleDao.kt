package com.restaurantpos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.restaurantpos.core.database.entity.RoleEntity

/**
 * `roles` 表数据访问接口。
 *
 * @see com.restaurantpos.core.database.entity.RoleEntity
 * @see com.restaurantpos.core.model.Role
 */
@Dao
interface RoleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(roles: List<RoleEntity>)

    @Query("SELECT * FROM roles ORDER BY sortOrder ASC")
    suspend fun getAll(): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RoleEntity?

    @Query("SELECT COUNT(*) FROM roles WHERE id = :id")
    suspend fun countById(id: String): Int

    @Query("DELETE FROM roles WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteById(id: String): Int
}
