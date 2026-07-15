package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Combo
import kotlinx.coroutines.flow.Flow

interface ComboRepository {
    fun observeActive(): Flow<List<Combo>>
    suspend fun getById(id: String): Combo?
    suspend fun save(combo: Combo)
}
