package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.MenuItemDao
import com.restaurantpos.core.database.dao.MenuProfileDao
import com.restaurantpos.core.database.dao.ModifierGroupDao
import com.restaurantpos.core.database.entity.MenuItemEntity
import com.restaurantpos.core.database.entity.ModifierEntity
import com.restaurantpos.core.database.entity.ModifierGroupEntity
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.model.MenuItem
import com.restaurantpos.core.model.ModifierGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Menu items are authoritative on the server (pushed down to devices).
 * The POS client does not sync menu changes UP — no SyncWriter needed here.
 */
class RoomMenuItemRepository(
    private val dao: MenuItemDao,
    private val menuProfileDao: MenuProfileDao,
    private val modifierGroupDao: ModifierGroupDao,
) : MenuItemRepository {

    override fun observeAll(): Flow<List<MenuItem>> =
        combine(dao.observeAll(), menuProfileDao.observeAllLinks()) { items, links ->
            val profileIdsByItem = links.groupBy({ it.menuItemId }, { it.menuProfileId })
            items.map { it.toDomain(profileIdsByItem[it.id] ?: emptyList()) }
        }

    override fun observeAvailable(): Flow<List<MenuItem>> =
        combine(dao.observeAvailable(), menuProfileDao.observeAllLinks()) { items, links ->
            val profileIdsByItem = links.groupBy({ it.menuItemId }, { it.menuProfileId })
            items.map { it.toDomain(profileIdsByItem[it.id] ?: emptyList()) }
        }

    override fun observeAvailableForContext(channel: String, timeHhmm: String): Flow<List<MenuItem>> {
        val todayDow = LocalDate.now().dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }
        return combine(
            dao.observeAvailable(),
            menuProfileDao.observeAll(),
            menuProfileDao.observeAllLinks(),
        ) { items, profiles, links ->
            val profileMap = profiles.associate { it.id to it.toDomain() }
            val profileIdsByItem = links.groupBy({ it.menuItemId }, { it.menuProfileId })
            items
                .map { it.toDomain(profileIdsByItem[it.id] ?: emptyList()) }
                .filter { item ->
                    if (item.menuProfileIds.isEmpty()) return@filter true
                    val activeProfiles = item.menuProfileIds.mapNotNull { profileMap[it] }
                    if (activeProfiles.isEmpty()) return@filter false
                    activeProfiles.any { p -> p.matchesContext(channel, timeHhmm, todayDow) }
                }
        }
    }

    override suspend fun getById(id: String): MenuItem? =
        dao.getById(id)?.toDomain()

    override suspend fun save(item: MenuItem) =
        dao.upsert(MenuItemEntity.fromDomain(item))

    override suspend fun upsertAll(items: List<MenuItem>) =
        dao.upsertAll(items.map { MenuItemEntity.fromDomain(it) })

    override suspend fun setSoldOut(id: String, soldOut: Boolean) =
        dao.setSoldOut(id, soldOut)

    override suspend fun bulkSetSoldOut(ids: List<String>, soldOut: Boolean) =
        dao.bulkSetSoldOut(ids, soldOut)

    override suspend fun getModifierGroups(menuItemId: String): List<ModifierGroup> {
        val groups = modifierGroupDao.getByMenuItem(menuItemId)
        return groups.map { g ->
            val mods = modifierGroupDao.getModifiersByGroup(g.id)
            g.toDomain(mods)
        }
    }

    override suspend fun saveModifierGroups(menuItemId: String, groups: List<ModifierGroup>) {
        val existingGroups = modifierGroupDao.getByMenuItem(menuItemId)
        val existingGroupIds = existingGroups.map { it.id }
        if (existingGroupIds.isNotEmpty()) {
            modifierGroupDao.deleteModifiersByGroups(existingGroupIds)
        }
        modifierGroupDao.deleteGroupsByMenuItem(menuItemId)

        val groupEntities = groups.mapIndexed { i, g -> ModifierGroupEntity.fromDomain(menuItemId, g, i) }
        modifierGroupDao.upsertGroups(groupEntities)

        val modifierEntities = groups.flatMap { g ->
            g.modifiers.mapIndexed { i, m -> ModifierEntity.fromDomain(g.id, m, i) }
        }
        if (modifierEntities.isNotEmpty()) {
            modifierGroupDao.upsertModifiers(modifierEntities)
        }
    }
}
