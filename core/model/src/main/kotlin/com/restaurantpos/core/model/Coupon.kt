package com.restaurantpos.core.model

enum class CouponType { PERCENT, FIXED }

data class Coupon(
    val id: String,
    val code: String,
    val type: CouponType,
    /** Percent: 0-100 (integer). Fixed: minor units. */
    val value: Long,
    val expiresAt: Long,
    val isActive: Boolean = true,
) {
    fun discountFor(subtotalMinorUnit: Long): Long = when (type) {
        CouponType.PERCENT -> (subtotalMinorUnit * value / 100L).coerceAtMost(subtotalMinorUnit)
        CouponType.FIXED -> value.coerceAtMost(subtotalMinorUnit)
    }
}
