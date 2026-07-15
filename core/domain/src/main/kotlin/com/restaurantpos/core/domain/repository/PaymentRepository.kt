package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Payment
import com.restaurantpos.core.model.PaymentStatus
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun observeByOrder(orderId: String): Flow<List<Payment>>
    suspend fun getByOrder(orderId: String): List<Payment>
    suspend fun getById(id: String): Payment?
    suspend fun save(payment: Payment)
    suspend fun updateStatus(id: String, status: PaymentStatus)
}
