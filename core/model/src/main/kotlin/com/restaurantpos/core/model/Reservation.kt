package com.restaurantpos.core.model

enum class ReservationStatus {
    CONFIRMED,
    SEATED,     // guest arrived, table converted to dine-in
    NO_SHOW,
    CANCELLED,
}

data class Reservation(
    val id: String,
    val tableId: String,
    val guestName: String,
    val guestCount: Int,
    val scheduledAt: Long,   // epoch millis
    val notes: String = "",
    val status: ReservationStatus = ReservationStatus.CONFIRMED,
    /** Last mutation time (epoch millis) — drives last-write-wins cross-device sync. */
    val updatedAt: Long = 0L,
)
