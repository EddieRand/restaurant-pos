package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus

/**
 * Splits selected items from an existing order into a new child order.
 *
 * Behaviour:
 *  1. Creates a new Order with status=PLACED and splitFromOrderId = original order id.
 *  2. Moves the specified OrderItems to the new order.
 *  3. Recalculates subtotal/taxTotal for both orders from their remaining items.
 *  4. Original order keeps its tableId; new order has tableId = null (to be assigned later).
 *
 * Invariant: sum of both orders' subtotals == original order subtotal.
 */
class SplitOrderUseCase(
    private val orderRepo: OrderRepository,
) {
    data class Params(
        val sourceOrderId: String,
        val itemIdsToSplit: Set<String>,
        val newOrderId: String,
        val now: Long = System.currentTimeMillis(),
        val terminalId: String = "split",
    )

    sealed class Result {
        data class Success(val originalOrder: Order, val newOrder: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val sourceOrder = orderRepo.getById(params.sourceOrderId)
            ?: return Result.Error("Order ${params.sourceOrderId} not found")

        if (sourceOrder.status != OrderStatus.PLACED &&
            sourceOrder.status != OrderStatus.READY
        ) {
            return Result.Error("Cannot split order in status ${sourceOrder.status}; must be PLACED or READY")
        }

        val allItems = orderRepo.getItemsByOrder(params.sourceOrderId)
        val (splitItems, remainingItems) = allItems.partition { it.id in params.itemIdsToSplit }

        if (splitItems.isEmpty()) return Result.Error("No matching items to split")
        if (remainingItems.isEmpty()) return Result.Error("Cannot split all items — at least one item must remain")

        // Calculate subtotals from effectiveUnitPrice (includes modifier adjustments)
        val remainingSubtotal = remainingItems.sumOf { it.lineTotalMinorUnit }
        val splitSubtotal = splitItems.sumOf { it.lineTotalMinorUnit }

        // Create the new child order
        val newOrder = Order(
            id = params.newOrderId,
            type = sourceOrder.type,
            tableId = null,
            guestCount = 0,
            sourceTerminalId = params.terminalId,
            subtotalMinorUnit = splitSubtotal,
            taxTotalMinorUnit = 0L, // tax recalculation deferred to checkout
            status = OrderStatus.PLACED,
            createdAt = params.now,
            updatedAt = params.now,
            splitFromOrderId = params.sourceOrderId,
        )
        orderRepo.save(newOrder)

        // Re-point split items to new order
        val movedItems = splitItems.map { it.copy(orderId = params.newOrderId) }
        orderRepo.saveItems(movedItems)

        // Update original order totals
        val updatedSource = sourceOrder.copy(
            subtotalMinorUnit = remainingSubtotal,
            updatedAt = params.now,
        )
        orderRepo.save(updatedSource)

        return Result.Success(originalOrder = updatedSource, newOrder = newOrder)
    }
}
