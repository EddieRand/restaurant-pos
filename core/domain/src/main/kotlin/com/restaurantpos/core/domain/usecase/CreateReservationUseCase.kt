package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.Reservation
import com.restaurantpos.core.model.ReservationStatus
import com.restaurantpos.core.model.TableStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Creates a new reservation and marks the table as RESERVED.
 *
 * Validation:
 * - Table must be AVAILABLE at time of booking.
 * - No overlapping reservation exists for the same table on the same day
 *   (±4 h window around scheduledAt to avoid double-booking).
 */
class CreateReservationUseCase(
    private val tableRepo: TableRepository,
    private val reservationRepo: ReservationRepository,
    private val regionConfig: RegionConfig,
) {
    /** Window (ms) used to detect overlapping reservations: ±2 hours. */
    private val overlapWindowMs = 2 * 60 * 60 * 1_000L

    suspend operator fun invoke(
        tableId: String,
        guestName: String,
        guestCount: Int,
        scheduledAt: Long,
    ): Reservation {
        require(guestName.isNotBlank()) { "Guest name must not be blank" }
        require(guestCount > 0) { "Guest count must be positive" }

        val table = tableRepo.getById(tableId)
            ?: error("Table $tableId not found")
        require(table.status == TableStatus.AVAILABLE) {
            "Table must be AVAILABLE to reserve, was ${table.status}"
        }

        // Check for overlapping reservations on the same calendar day (timezone-aware)
        val zone = ZoneId.of(regionConfig.timeZone)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), zone).toLocalDate()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayReservations = reservationRepo.observeByDate(dayStart).first()
        val hasOverlap = dayReservations.any { existing ->
            existing.tableId == tableId &&
                existing.status == ReservationStatus.CONFIRMED &&
                kotlin.math.abs(existing.scheduledAt - scheduledAt) < overlapWindowMs
        }
        require(!hasOverlap) { "Table $tableId already has a reservation within 2 hours of $scheduledAt" }

        val reservation = Reservation(
            id = UUID.randomUUID().toString(),
            tableId = tableId,
            guestName = guestName,
            guestCount = guestCount,
            scheduledAt = scheduledAt,
            status = ReservationStatus.CONFIRMED,
        )
        reservationRepo.save(reservation)

        // Transition table: AVAILABLE -> RESERVED
        val newStatus = TableStateMachine.onReserved(table.status)
        tableRepo.updateStatusAndOrder(tableId, newStatus, null)

        return reservation
    }
}
