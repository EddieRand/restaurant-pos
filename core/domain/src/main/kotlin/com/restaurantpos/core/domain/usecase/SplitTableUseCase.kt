package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import com.restaurantpos.core.model.TableStatus
import java.util.UUID

/**
 * Splits selected items from a source order onto a target (AVAILABLE) table.
 *
 * Rules:
 * 1. Target table must be AVAILABLE.
 * 2. A new Order (DRAFT) is created for the target table.
 * 3. Specified items are moved to the new order.
 * 4. Both orders recalculate their totals.
 * 5. Target table becomes OCCUPIED with the new order id.
 */
class SplitTableUseCase(
    private val tableRepo: TableRepository,
    private val orderRepo: OrderRepository,
) {
    suspend operator fun invoke(
        sourceOrderId: String,
        targetTableId: String,
        itemIdsToMove: List<String>,
    ): String {
        require(itemIdsToMove.isNotEmpty()) { "itemIdsToMove must not be empty" }

        val targetTable = tableRepo.getById(targetTableId)
            ?: error("Target table $targetTableId not found")
        require(targetTable.status == TableStatus.AVAILABLE) {
            "Target table must be AVAILABLE, was ${targetTable.status}"
        }

        val sourceOrder = requireNotNull(orderRepo.getById(sourceOrderId)) {
            "Source order $sourceOrderId not found"
        }

        val allSourceItems = orderRepo.getItemsByOrder(sourceOrderId)
        val itemsToMove = allSourceItems.filter { it.id in itemIdsToMove }
        require(itemsToMove.size == itemIdsToMove.size) {
            "Some item ids not found in source order"
        }

        // Create new order for target table
        val now = System.currentTimeMillis()
        val newOrderId = UUID.randomUUID().toString()
        val newOrder = Order(
            id = newOrderId,
            type = OrderType.DINE_IN,
            tableId = targetTableId,
            sourceTerminalId = sourceOrder.sourceTerminalId,
            status = OrderStatus.DRAFT,
            splitFromOrderId = sourceOrderId,
            createdAt = now,
            updatedAt = now,
        )
        orderRepo.save(newOrder)

        // Move items to new order
        val movedItems = itemsToMove.map { it.copy(orderId = newOrderId) }
        orderRepo.saveItems(movedItems)

        // Recalculate source order totals
        val remainingItems = allSourceItems.filter { it.id !in itemIdsToMove }
        val sourceNewSubtotal = remainingItems.sumOf { it.lineTotalMinorUnit }
        // taxTotal: proportional carry-over; full tax recalc deferred to Batch 6 tax engine
        val movedSubtotal = itemsToMove.sumOf { it.lineTotalMinorUnit }
        val totalOriginalSubtotal = allSourceItems.sumOf { it.lineTotalMinorUnit }
        val movedTaxFraction = if (totalOriginalSubtotal > 0L) {
            (sourceOrder.taxTotalMinorUnit * movedSubtotal) / totalOriginalSubtotal
        } else 0L
        val sourceNewTax = sourceOrder.taxTotalMinorUnit - movedTaxFraction

        orderRepo.updateTotals(sourceOrderId, sourceNewSubtotal, sourceNewTax)

        // Calculate new order totals
        val newSubtotal = movedItems.sumOf { it.lineTotalMinorUnit }
        orderRepo.updateTotals(newOrderId, newSubtotal, movedTaxFraction)

        // Transition target table: AVAILABLE -> OCCUPIED
        val newStatus = TableStateMachine.onSeated(targetTable.status)
        tableRepo.updateStatusAndOrder(targetTableId, newStatus, newOrderId)

        return newOrderId
    }
}
