package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.*

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val type: OrderType,
    val tableId: String?,
    val guestCount: Int,
    val sourceTerminalId: String,
    val operatorId: String = "",
    val subtotalMinorUnit: Long,
    val taxTotalMinorUnit: Long,
    val serviceChargeMinorUnit: Long,
    val tipMinorUnit: Long = 0L,
    val discountMinorUnit: Long,
    val status: OrderStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val orderNotes: String = "",
    val splitFromOrderId: String? = null,
    val mergedIntoOrderId: String? = null,
    /** Pipe-delimited list of secondary table ids merged into this order. */
    val mergedTableIds: String = "",
    val pickupCode: String? = null,
    val fulfillmentStatus: OrderFulfillmentStatus = OrderFulfillmentStatus.NOT_READY,
) {
    fun toDomain() = Order(
        id = id, type = type, tableId = tableId, guestCount = guestCount,
        sourceTerminalId = sourceTerminalId, operatorId = operatorId,
        subtotalMinorUnit = subtotalMinorUnit,
        taxTotalMinorUnit = taxTotalMinorUnit, serviceChargeMinorUnit = serviceChargeMinorUnit,
        tipMinorUnit = tipMinorUnit, discountMinorUnit = discountMinorUnit, status = status,
        createdAt = createdAt, updatedAt = updatedAt,
        orderNotes = orderNotes,
        splitFromOrderId = splitFromOrderId, mergedIntoOrderId = mergedIntoOrderId,
        mergedTableIds = if (mergedTableIds.isBlank()) emptyList()
                         else mergedTableIds.split("|"),
        pickupCode = pickupCode,
        fulfillmentStatus = fulfillmentStatus,
    )

    companion object {
        fun fromDomain(o: Order) = OrderEntity(
            id = o.id, type = o.type, tableId = o.tableId, guestCount = o.guestCount,
            sourceTerminalId = o.sourceTerminalId, operatorId = o.operatorId,
            subtotalMinorUnit = o.subtotalMinorUnit,
            taxTotalMinorUnit = o.taxTotalMinorUnit, serviceChargeMinorUnit = o.serviceChargeMinorUnit,
            tipMinorUnit = o.tipMinorUnit, discountMinorUnit = o.discountMinorUnit, status = o.status,
            createdAt = o.createdAt, updatedAt = o.updatedAt,
            orderNotes = o.orderNotes,
            splitFromOrderId = o.splitFromOrderId, mergedIntoOrderId = o.mergedIntoOrderId,
            mergedTableIds = o.mergedTableIds.joinToString("|"),
            pickupCode = o.pickupCode,
            fulfillmentStatus = o.fulfillmentStatus,
        )
    }
}
