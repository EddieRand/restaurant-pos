package com.restaurantpos.core.model

enum class KitchenTicketStatus {
    NEW,
    PREPARING,
    DONE,
    RECALLED,
}

data class KitchenTicket(
    val id: String,
    val orderId: String,
    val orderItemIds: List<String>,
    val stationId: String,
    val course: Int = 1,
    val status: KitchenTicketStatus = KitchenTicketStatus.NEW,
    val createdAt: Long,
    val bumpedAt: Long? = null,
    val updatedAt: Long = createdAt,
)
