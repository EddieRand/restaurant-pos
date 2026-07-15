package com.restaurantpos.core.database.repository

import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemorySessionRepository : SessionRepository {
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()
    override fun current(): User? = _currentUser.value
    override fun login(user: User) { _currentUser.value = user }
    override fun logout() { _currentUser.value = null }
}
