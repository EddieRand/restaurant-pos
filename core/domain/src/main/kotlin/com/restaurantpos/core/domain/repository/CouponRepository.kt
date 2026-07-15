package com.restaurantpos.core.domain.repository

import com.restaurantpos.core.model.Coupon

interface CouponRepository {
    suspend fun getByCode(code: String): Coupon?
    suspend fun save(coupon: Coupon)
}
