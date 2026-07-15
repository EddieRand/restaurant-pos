package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.WaiterCall
import com.restaurantpos.core.model.WaiterCallReason

@Entity(tableName = "waiter_calls")
data class WaiterCallEntity(
    @PrimaryKey val id: String,
    val tableId: String,
    val tableDisplayName: String,
    val reason: String,
    val createdAt: Long,
    val acknowledgedAt: Long?,
) {
    fun toDomain() = WaiterCall(
        id = id,
        tableId = tableId,
        tableDisplayName = tableDisplayName,
        reason = WaiterCallReason.valueOf(reason),
        createdAt = createdAt,
        acknowledgedAt = acknowledgedAt,
    )

    companion object {
        fun fromDomain(c: WaiterCall) = WaiterCallEntity(
            id = c.id,
            tableId = c.tableId,
            tableDisplayName = c.tableDisplayName,
            reason = c.reason.name,
            createdAt = c.createdAt,
            acknowledgedAt = c.acknowledgedAt,
        )
    }
}
