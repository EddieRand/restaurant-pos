package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Coupon
import com.restaurantpos.core.model.CouponType

@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey val id: String,
    val code: String,
    val type: String,
    val value: Long,
    val expiresAt: Long,
    val isActive: Int = 1,
) {
    fun toDomain() = Coupon(
        id = id,
        code = code,
        type = runCatching { CouponType.valueOf(type) }.getOrDefault(CouponType.FIXED),
        value = value,
        expiresAt = expiresAt,
        isActive = isActive == 1,
    )

    companion object {
        fun fromDomain(c: Coupon) = CouponEntity(
            id = c.id,
            code = c.code,
            type = c.type.name,
            value = c.value,
            expiresAt = c.expiresAt,
            isActive = if (c.isActive) 1 else 0,
        )
    }
}
