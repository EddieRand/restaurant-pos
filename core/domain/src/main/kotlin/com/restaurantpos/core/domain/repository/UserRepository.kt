package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeActive(): Flow<List<User>>
    suspend fun getById(id: String): User?
    suspend fun getByPin(pinHash: String): User?
    suspend fun save(user: User)
    suspend fun setActive(id: String, isActive: Boolean)
}
