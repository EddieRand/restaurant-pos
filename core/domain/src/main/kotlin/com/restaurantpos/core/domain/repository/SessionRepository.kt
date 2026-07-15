package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.User
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val currentUser: Flow<User?>
    fun current(): User?
    fun login(user: User)
    fun logout()
}
