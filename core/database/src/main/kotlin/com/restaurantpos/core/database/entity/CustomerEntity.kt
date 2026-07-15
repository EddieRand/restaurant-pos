package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: String = "",              // pipe-separated
    val notes: String? = null,
    val totalSpendMinorUnit: Long = 0L,
    val loyaltyPoints: Long = 0L,
    val membershipTierId: String? = null,
    val totalVisits: Int = 0,
    val lastVisitAt: Long = 0L,
    val registeredAt: Long,
    val updatedAt: Long = 0L,
) {
    companion object
}

@Entity(tableName = "loyalty_transactions")
data class LoyaltyTransactionEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val orderId: String? = null,
    val type: String,                   // EARN / REDEEM / ADJUST / EXPIRE
    val points: Long,
    val description: String,
    val createdAt: Long,
)
