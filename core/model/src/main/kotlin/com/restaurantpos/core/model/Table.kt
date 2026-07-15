package com.restaurantpos.core.model

data class Table(
    val id: String,
    val name: String,
    val sectionId: String,
    val capacity: Int,
    val currentOrderId: String? = null,
    val status: TableStatus = TableStatus.AVAILABLE,
    /** Last mutation time (epoch millis) — drives last-write-wins cross-device sync. */
    val updatedAt: Long = 0L,
)
