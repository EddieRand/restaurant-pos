package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import com.restaurantpos.core.model.ReservationStatus
import java.util.UUID

/**
 * Seats a confirmed reservation:
 * 1. Reservation.status -> SEATED
 * 2. Table: RESERVED -> AVAILABLE -> OCCUPIED (two-step state machine)
 * 3. A new DRAFT Order is created and linked to the table.
 *
 * Returns the new order id.
 */
class SeatReservationUseCase(
    private val reservationRepo: ReservationRepository,
    private val tableRepo: TableRepository,
    private val orderRepo: OrderRepository,
) {
    suspend operator fun invoke(
        reservationId: String,
        terminalId: String = "pos-1",
        operatorId: String = "",
    ): String {
        val reservation = reservationRepo.getById(reservationId)
            ?: error("Reservation $reservationId not found")
        require(reservation.status == ReservationStatus.CONFIRMED) {
            "Reservation must be CONFIRMED to seat, was ${reservation.status}"
        }

        val table = tableRepo.getById(reservation.tableId)
            ?: error("Table ${reservation.tableId} not found")

        // Step 1: Mark reservation as SEATED
        reservationRepo.updateStatus(reservationId, ReservationStatus.SEATED)

        // Step 2a: RESERVED -> AVAILABLE
        val availableStatus = TableStateMachine.onReservationReleased(table.status)
        tableRepo.updateStatusAndOrder(table.id, availableStatus, null)

        // Step 2b: AVAILABLE -> OCCUPIED
        val occupiedStatus = TableStateMachine.onSeated(availableStatus)

        // Step 3: Create new DRAFT order
        val now = System.currentTimeMillis()
        val orderId = UUID.randomUUID().toString()
        val order = Order(
            id = orderId,
            type = OrderType.DINE_IN,
            tableId = table.id,
            guestCount = reservation.guestCount,
            sourceTerminalId = terminalId,
            operatorId = operatorId,
            status = OrderStatus.DRAFT,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(order)

        tableRepo.updateStatusAndOrder(table.id, occupiedStatus, orderId)

        return orderId
    }
}
