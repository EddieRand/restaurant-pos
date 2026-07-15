package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.MenuProfileDao
import com.restaurantpos.core.database.entity.MenuItemProfileEntity
import com.restaurantpos.core.database.entity.MenuProfileEntity
import com.restaurantpos.core.domain.repository.MenuProfileRepository
import com.restaurantpos.core.model.MenuProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMenuProfileRepository(
    private val dao: MenuProfileDao,
) : MenuProfileRepository {

    override fun observeAll(): Flow<List<MenuProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun replaceAll(profiles: List<MenuProfile>) {
        dao.upsertAll(profiles.map { MenuProfileEntity.fromDomain(it) })
        if (profiles.isNotEmpty()) {
            dao.deleteNotIn(profiles.map { it.id })
        }
    }

    override suspend fun getItemIdsForProfile(profileId: String): List<String> =
        dao.getItemIdsForProfile(profileId)

    override suspend fun setItemsForProfile(profileId: String, itemIds: List<String>) {
        dao.deleteLinksForProfile(profileId)
        if (itemIds.isNotEmpty()) {
            dao.upsertLinks(itemIds.map { MenuItemProfileEntity(menuItemId = it, menuProfileId = profileId) })
        }
    }
}
