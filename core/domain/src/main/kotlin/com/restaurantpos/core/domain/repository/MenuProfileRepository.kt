package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.MenuProfile
import kotlinx.coroutines.flow.Flow

interface MenuProfileRepository {
    fun observeAll(): Flow<List<MenuProfile>>
    suspend fun replaceAll(profiles: List<MenuProfile>)
    suspend fun getItemIdsForProfile(profileId: String): List<String>
    suspend fun setItemsForProfile(profileId: String, itemIds: List<String>)
}
