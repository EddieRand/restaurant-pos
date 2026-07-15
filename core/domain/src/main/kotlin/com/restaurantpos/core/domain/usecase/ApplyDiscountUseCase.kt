package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus

/**
 * Applies a discount to an order or a single item.
 *
 * - Item-level: stores a negative priceAdjustment as a SelectedModifier with id="discount".
 * - Order-level: sets Order.discountMinorUnit.
 * - discountMinorUnit must be positive (reduction) and ≤ the applicable subtotal.
 */
class ApplyDiscountUseCase(
    private val orderRepo: OrderRepository,
) {
    sealed class Target {
        data class OrderLevel(val discountMinorUnit: Long) : Target()
        data class ItemLevel(val itemId: String, val discountMinorUnit: Long) : Target()
    }

    sealed class Result {
        data class Success(val order: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(orderId: String, target: Target): Result {
        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order $orderId not found")

        if (order.status == OrderStatus.CLOSED || order.status == OrderStatus.VOIDED) {
            return Result.Error("Cannot apply discount to ${order.status} order")
        }

        return when (target) {
            is Target.OrderLevel -> applyOrderDiscount(order, target.discountMinorUnit)
            is Target.ItemLevel -> applyItemDiscount(order, target.itemId, target.discountMinorUnit)
        }
    }

    private suspend fun applyOrderDiscount(order: Order, discountMinorUnit: Long): Result {
        if (discountMinorUnit < 0) return Result.Error("Discount must be non-negative")
        if (discountMinorUnit > order.subtotalMinorUnit)
            return Result.Error("Discount $discountMinorUnit exceeds subtotal ${order.subtotalMinorUnit}")
        val updated = order.copy(
            discountMinorUnit = discountMinorUnit,
            updatedAt = System.currentTimeMillis(),
        )
        orderRepo.save(updated)
        return Result.Success(updated)
    }

    private suspend fun applyItemDiscount(order: Order, itemId: String, discountMinorUnit: Long): Result {
        val items = orderRepo.getItemsByOrder(order.id)
        val item = items.find { it.id == itemId }
            ?: return Result.Error("Item $itemId not found in order ${order.id}")
        if (discountMinorUnit < 0) return Result.Error("Discount must be non-negative")
        if (discountMinorUnit > item.effectiveUnitPrice * item.quantity)
            return Result.Error("Item discount $discountMinorUnit exceeds item total")

        // Store as a special modifier with negative adjustment (per-unit equivalent)
        val perUnitDiscount = -(discountMinorUnit / item.quantity)
        val discountModifier = com.restaurantpos.core.model.SelectedModifier(
            groupId = "discount",
            modifierId = "discount",
            nameSnapshot = mapOf("en" to "Discount"),
            priceAdjustmentMinorUnit = perUnitDiscount,
        )
        val existingModifiers = item.selectedModifiers.filter { it.groupId != "discount" }
        val updatedItem = item.copy(selectedModifiers = existingModifiers + discountModifier)
        orderRepo.saveItems(listOf(updatedItem))

        // Recalculate order subtotal
        val allItems = items.map { if (it.id == itemId) updatedItem else it }
        val newSubtotal = allItems.sumOf { it.lineTotalMinorUnit }
        val updatedOrder = order.copy(subtotalMinorUnit = newSubtotal, updatedAt = System.currentTimeMillis())
        orderRepo.save(updatedOrder)
        return Result.Success(updatedOrder)
    }
}
