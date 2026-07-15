package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.MenuItem
import com.restaurantpos.core.model.ModifierGroup
import kotlinx.coroutines.flow.Flow

interface MenuItemRepository {
    fun observeAll(): Flow<List<MenuItem>>
    fun observeAvailable(): Flow<List<MenuItem>>
    /** Filters by time and channel context, respecting MenuProfile assignments. */
    fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>>
    suspend fun getById(id: String): MenuItem?
    suspend fun save(item: MenuItem)
    /** Bulk upsert from server pull-sync (Batch 44) — server is authoritative for menu content. */
    suspend fun upsertAll(items: List<MenuItem>)
    suspend fun setSoldOut(id: String, soldOut: Boolean)
    suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean)
    suspend fun getModifierGroups(menuItemId: String): List<ModifierGroup>
    suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>)
}
