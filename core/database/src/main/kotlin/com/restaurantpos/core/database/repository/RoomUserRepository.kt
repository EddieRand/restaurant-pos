package com.restaurantpos.core.database.repository

import com.restaurantpos.core.database.dao.UserDao
import com.restaurantpos.core.database.entity.UserEntity
import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserRepository(private val dao: UserDao) : UserRepository {
    override fun observeActive(): Flow<List<User>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: String): User? = dao.getById(id)?.toDomain()
    override suspend fun getByPin(pinHash: String): User? = dao.getByPin(pinHash)?.toDomain()
    override suspend fun save(user: User) = dao.upsert(UserEntity.fromDomain(user))
    override suspend fun setActive(id: String, isActive: Boolean) = dao.setActive(id, isActive)
}
