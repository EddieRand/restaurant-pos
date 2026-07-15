package com.restaurantpos.core.model

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val totalSpendMinorUnit: Long = 0L,
    val loyaltyPoints: Long = 0L,
    val membershipTierId: String? = null,
    val totalVisits: Int = 0,
    val lastVisitAt: Long = 0L,
    val registeredAt: Long,
    /** Last mutation time (epoch millis) — drives last-write-wins cross-device sync. */
    val updatedAt: Long = 0L,
)

data class LoyaltyTransaction(
    val id: String,
    val customerId: String,
    val orderId: String? = null,
    val type: LoyaltyTxnType,
    val points: Long,
    val description: String,
    val createdAt: Long,
)

enum class LoyaltyTxnType { EARN, REDEEM, ADJUST, EXPIRE }
