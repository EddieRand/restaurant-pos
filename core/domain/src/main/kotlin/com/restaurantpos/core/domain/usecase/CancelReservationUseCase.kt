package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.ReservationStatus
import com.restaurantpos.core.model.TableStatus

/**
 * Cancels a CONFIRMED reservation and frees the table back to AVAILABLE.
 */
class CancelReservationUseCase(
    private val reservationRepo: ReservationRepository,
    private val tableRepo: TableRepository,
) {
    sealed interface Result {
        data object Success : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(reservationId: String): Result {
        val reservation = reservationRepo.getById(reservationId)
            ?: return Result.Error("Reservation $reservationId not found")
        if (reservation.status != ReservationStatus.CONFIRMED) {
            return Result.Error("Cannot cancel reservation in status ${reservation.status}")
        }

        reservationRepo.updateStatus(reservationId, ReservationStatus.CANCELLED)

        val table = tableRepo.getById(reservation.tableId)
        if (table != null && table.status == TableStatus.RESERVED) {
            val newStatus = TableStateMachine.onReservationReleased(table.status)
            tableRepo.updateStatusAndOrder(table.id, newStatus, null)
        }

        return Result.Success
    }
}
