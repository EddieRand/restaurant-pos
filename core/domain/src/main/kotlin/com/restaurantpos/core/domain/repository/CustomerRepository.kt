package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.model.LoyaltyTransaction
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun observeAll(): Flow<List<Customer>>
    suspend fun search(query: String): List<Customer>
    suspend fun getById(id: String): Customer?
    suspend fun getByPhone(phone: String): Customer?
    suspend fun save(customer: Customer)
    suspend fun addPoints(
        customerId: String,
        points: Long,
        orderId: String?,
        description: String,
    )
    suspend fun getTransactions(customerId: String): List<LoyaltyTransaction>

    /** Applies customers pulled from the server (last-write-wins by updatedAt); must not re-enqueue outbox. */
    suspend fun applyRemote(customers: List<Customer>)
}
