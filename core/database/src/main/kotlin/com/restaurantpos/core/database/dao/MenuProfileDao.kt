package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.MenuItemProfileEntity
import com.restaurantpos.core.database.entity.MenuProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuProfileDao {

    @Query("SELECT * FROM menu_profiles ORDER BY id")
    fun observeAll(): Flow<List<MenuProfileEntity>>

    @Upsert
    suspend fun upsertAll(profiles: List<MenuProfileEntity>)

    @Query("DELETE FROM menu_profiles WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("SELECT * FROM menu_item_profiles")
    fun observeAllLinks(): Flow<List<MenuItemProfileEntity>>

    @Query("SELECT menuProfileId FROM menu_item_profiles WHERE menuItemId = :itemId")
    suspend fun getProfileIdsForItem(itemId: String): List<String>

    @Query("SELECT menuItemId FROM menu_item_profiles WHERE menuProfileId = :profileId")
    suspend fun getItemIdsForProfile(profileId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<MenuItemProfileEntity>)

    @Query("DELETE FROM menu_item_profiles WHERE menuItemId = :itemId")
    suspend fun deleteLinksForItem(itemId: String)

    @Query("DELETE FROM menu_item_profiles WHERE menuProfileId = :profileId")
    suspend fun deleteLinksForProfile(profileId: String)
}
