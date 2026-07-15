package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.ModifierEntity
import com.restaurantpos.core.database.entity.ModifierGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModifierGroupDao {

    @Query("SELECT * FROM modifier_groups WHERE menuItemId = :menuItemId ORDER BY sortOrder")
    fun observeByMenuItem(menuItemId: String): Flow<List<ModifierGroupEntity>>

    @Query("SELECT * FROM modifier_groups WHERE menuItemId = :menuItemId ORDER BY sortOrder")
    suspend fun getByMenuItem(menuItemId: String): List<ModifierGroupEntity>

    @Query("SELECT * FROM modifiers WHERE groupId = :groupId ORDER BY sortOrder")
    suspend fun getModifiersByGroup(groupId: String): List<ModifierEntity>

    @Upsert
    suspend fun upsertGroups(groups: List<ModifierGroupEntity>)

    @Upsert
    suspend fun upsertModifiers(modifiers: List<ModifierEntity>)

    @Query("DELETE FROM modifier_groups WHERE menuItemId = :menuItemId")
    suspend fun deleteGroupsByMenuItem(menuItemId: String)

    @Query("DELETE FROM modifiers WHERE groupId IN (:groupIds)")
    suspend fun deleteModifiersByGroups(groupIds: List<String>)
}
