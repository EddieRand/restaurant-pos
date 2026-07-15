package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.model.TableStatus
/**
 * Merges a secondary table's order into the primary table's order.
 *
 * Rules:
 * 1. Both tables must be OCCUPIED or ORDERED.
 * 2. Secondary table's OrderItems are reassigned to the primary table's order.
 * 3. Secondary table becomes AVAILABLE with currentOrderId = null.
 * 4. Primary order subtotal/taxTotal is recalculated.
 * 5. Primary order records the merged secondary table id in mergedTableIds.
 */
class MergeTablesUseCase(
    private val tableRepo: TableRepository,
    private val orderRepo: OrderRepository,
) {
    private val validStatuses = setOf(TableStatus.OCCUPIED, TableStatus.ORDERED)

    suspend operator fun invoke(primaryTableId: String, secondaryTableId: String) {
        val primaryTable = tableRepo.getById(primaryTableId)
            ?: error("Primary table $primaryTableId not found")
        val secondaryTable = tableRepo.getById(secondaryTableId)
            ?: error("Secondary table $secondaryTableId not found")

        require(primaryTable.status in validStatuses) {
            "Primary table must be OCCUPIED or ORDERED, was ${primaryTable.status}"
        }
        require(secondaryTable.status in validStatuses) {
            "Secondary table must be OCCUPIED or ORDERED, was ${secondaryTable.status}"
        }

        val primaryOrderId = requireNotNull(primaryTable.currentOrderId) {
            "Primary table has no active order"
        }
        val secondaryOrderId = requireNotNull(secondaryTable.currentOrderId) {
            "Secondary table has no active order"
        }

        val primaryOrder = requireNotNull(orderRepo.getById(primaryOrderId)) {
            "Primary order $primaryOrderId not found"
        }
        val secondaryOrder = requireNotNull(orderRepo.getById(secondaryOrderId)) {
            "Secondary order $secondaryOrderId not found"
        }

        // Reassign secondary items to primary order
        val secondaryItems = orderRepo.getItemsByOrder(secondaryOrderId)
        val reassignedItems = secondaryItems.map { it.copy(orderId = primaryOrderId) }
        if (reassignedItems.isNotEmpty()) {
            orderRepo.saveItems(reassignedItems)
        }

        // Recalculate primary order totals — query after saveItems so all items are included
        val allItems = orderRepo.getItemsByOrder(primaryOrderId)
        val newSubtotal = allItems.sumOf { it.lineTotalMinorUnit }
        // taxTotal: carry over both orders' tax totals (tax recalc belongs to tax engine in Batch 6)
        val newTaxTotal = primaryOrder.taxTotalMinorUnit + secondaryOrder.taxTotalMinorUnit

        // Update primary order with new totals and record merged table
        val updatedMergedIds = primaryOrder.mergedTableIds + secondaryTableId
        val updatedPrimaryOrder = primaryOrder.copy(
            subtotalMinorUnit = newSubtotal,
            taxTotalMinorUnit = newTaxTotal,
            mergedTableIds = updatedMergedIds,
            updatedAt = System.currentTimeMillis(),
        )
        orderRepo.save(updatedPrimaryOrder)

        // Mark secondary table as available
        tableRepo.updateStatusAndOrder(secondaryTableId, TableStatus.AVAILABLE, null)
    }
}
