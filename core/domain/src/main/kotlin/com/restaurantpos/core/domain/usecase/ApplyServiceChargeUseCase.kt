package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus

/**
 * Sets service charge (or tip) on an order.
 *
 * - chargeMinorUnit: absolute amount, must be ≥ 0.
 * - Replaces any existing serviceChargeMinorUnit on the order.
 */
class ApplyServiceChargeUseCase(
    private val orderRepo: OrderRepository,
) {
    sealed class Result {
        data class Success(val order: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(orderId: String, chargeMinorUnit: Long): Result {
        require(chargeMinorUnit >= 0) { "Service charge must be non-negative" }

        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order $orderId not found")

        if (order.status == OrderStatus.CLOSED || order.status == OrderStatus.VOIDED) {
            return Result.Error("Cannot apply service charge to ${order.status} order")
        }

        val updated = order.copy(
            serviceChargeMinorUnit = chargeMinorUnit,
            updatedAt = System.currentTimeMillis(),
        )
        orderRepo.save(updated)
        return Result.Success(updated)
    }
}
