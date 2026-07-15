package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.KitchenTicketStatus

@Entity(tableName = "kitchen_tickets")
data class KitchenTicketEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val orderItemIds: String,   // pipe-delimited list of item IDs
    val stationId: String,
    val course: Int,
    val status: String,
    val createdAt: Long,
    val bumpedAt: Long?,
    val updatedAt: Long = createdAt,
) {
    fun toDomain() = KitchenTicket(
        id = id,
        orderId = orderId,
        orderItemIds = orderItemIds.split("|").filter { it.isNotEmpty() },
        stationId = stationId,
        course = course,
        status = KitchenTicketStatus.valueOf(status),
        createdAt = createdAt,
        bumpedAt = bumpedAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(t: KitchenTicket) = KitchenTicketEntity(
            id = t.id,
            orderId = t.orderId,
            orderItemIds = t.orderItemIds.joinToString("|"),
            stationId = t.stationId,
            course = t.course,
            status = t.status.name,
            createdAt = t.createdAt,
            bumpedAt = t.bumpedAt,
            updatedAt = t.updatedAt,
        )
    }
}
