package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Reservation
import com.restaurantpos.core.model.ReservationStatus
import kotlinx.coroutines.flow.Flow

interface ReservationRepository {
    fun observeByDate(dateEpochMillis: Long): Flow<List<Reservation>>
    suspend fun getById(id: String): Reservation?
    suspend fun save(reservation: Reservation)
    suspend fun updateStatus(id: String, status: ReservationStatus)
}
