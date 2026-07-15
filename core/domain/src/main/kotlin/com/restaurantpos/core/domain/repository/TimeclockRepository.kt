package com.restaurantpos.core.domain.repository

interface TimeclockRepository {
    /** Returns the open timecard id if this operator is currently clocked in, null otherwise. */
    suspend fun activeTimecardId(operatorId: String): String?

    /** Clock in. Returns the new timecard id. */
    suspend fun clockIn(operatorId: String, operatorName: String, terminalId: String): String

    /** Clock out the given timecard. */
    suspend fun clockOut(timecardId: String)
}
