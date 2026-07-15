package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.CouponRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Coupon

class ApplyCouponUseCase(
    private val couponRepo: CouponRepository,
    private val orderRepo: OrderRepository,
) {
    sealed interface Result {
        data class Success(val coupon: Coupon, val discountMinorUnit: Long) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(orderId: String, code: String, nowEpoch: Long): Result {
        val coupon = couponRepo.getByCode(code.trim().uppercase())
            ?: return Result.Error("Coupon not found: $code")
        if (!coupon.isActive) return Result.Error("Coupon is inactive")
        if (coupon.expiresAt < nowEpoch) return Result.Error("Coupon has expired")

        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order not found")

        val discount = coupon.discountFor(order.subtotalMinorUnit)
        orderRepo.save(order.copy(discountMinorUnit = discount))
        return Result.Success(coupon = coupon, discountMinorUnit = discount)
    }
}
