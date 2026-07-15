package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items WHERE isSoldOut = 0 ORDER BY categoryId, course")
    fun observeAvailable(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items")
    fun observeAll(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: String): MenuItemEntity?

    @Upsert
    suspend fun upsert(item: MenuItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MenuItemEntity>)

    @Query("UPDATE menu_items SET isSoldOut = :soldOut WHERE id = :id")
    suspend fun setSoldOut(id: String, soldOut: Boolean)

    @Query("UPDATE menu_items SET isSoldOut = :soldOut WHERE id IN (:ids)")
    suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean)
}
