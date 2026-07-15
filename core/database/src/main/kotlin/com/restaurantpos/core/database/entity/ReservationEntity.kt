package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Reservation
import com.restaurantpos.core.model.ReservationStatus

@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey val id: String,
    val tableId: String,
    val guestName: String,
    val guestCount: Int,
    val scheduledAt: Long,
    val notes: String = "",
    val status: String = ReservationStatus.CONFIRMED.name,
    val updatedAt: Long = 0L,
) {
    fun toDomain() = Reservation(
        id = id,
        tableId = tableId,
        guestName = guestName,
        guestCount = guestCount,
        scheduledAt = scheduledAt,
        notes = notes,
        status = ReservationStatus.valueOf(status),
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(r: Reservation) = ReservationEntity(
            id = r.id,
            tableId = r.tableId,
            guestName = r.guestName,
            guestCount = r.guestCount,
            scheduledAt = r.scheduledAt,
            notes = r.notes,
            status = r.status.name,
            updatedAt = r.updatedAt,
        )
    }
}
