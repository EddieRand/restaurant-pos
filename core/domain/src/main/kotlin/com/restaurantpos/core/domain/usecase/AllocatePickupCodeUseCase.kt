package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import java.util.Calendar

/**
 * Allocates the next kiosk pickup number for the day, cycling 1..99 then wrapping back to 1.
 * "Today" is determined by the device's local calendar day.
 */
class AllocatePickupCodeUseCase(private val orderRepo: OrderRepository) {

    companion object {
        const val MAX_PICKUP_CODE = 99
    }

    suspend operator fun invoke(now: Long = System.currentTimeMillis()): String {
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Count-based so the sequence keeps advancing after wrapping past 99
        // (MAX-based logic would return 1 forever once 99 exists today).
        val allocatedToday = orderRepo.countPickupCodesSince(startOfDay)
        val next = (allocatedToday % MAX_PICKUP_CODE) + 1
        return next.toString()
    }
}
