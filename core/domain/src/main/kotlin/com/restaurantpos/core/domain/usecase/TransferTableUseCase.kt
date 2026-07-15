package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus

/**
 * Transfers an active order from its current table to a target table.
 *
 * Behaviour:
 *  1. Validates target table is AVAILABLE.
 *  2. Sets the original table status → AVAILABLE and clears its currentOrderId.
 *  3. Sets the target table status → OCCUPIED (or preserves ORDERED if already fired)
 *     and assigns currentOrderId = orderId.
 *  4. Updates order.tableId = targetTableId.
 */
class TransferTableUseCase(
    private val orderRepo: OrderRepository,
    private val tableRepo: TableRepository,
) {
    data class Params(
        val orderId: String,
        val targetTableId: String,
        val now: Long = System.currentTimeMillis(),
    )

    sealed class Result {
        data class Success(
            val updatedOrder: Order,
            val previousTable: Table?,
            val targetTable: Table,
        ) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val order = orderRepo.getById(params.orderId)
            ?: return Result.Error("Order ${params.orderId} not found")

        val targetTable = tableRepo.getById(params.targetTableId)
            ?: return Result.Error("Target table ${params.targetTableId} not found")

        if (targetTable.status != TableStatus.AVAILABLE) {
            return Result.Error("Target table ${params.targetTableId} is not available (status: ${targetTable.status})")
        }

        // Release the original table if the order has one
        var previousTable: Table? = null
        order.tableId?.let { oldTableId ->
            val oldTable = tableRepo.getById(oldTableId)
            if (oldTable != null) {
                tableRepo.updateStatusAndOrder(oldTableId, TableStatus.AVAILABLE, null)
                previousTable = oldTable
            }
        }

        // Assign the target table
        tableRepo.updateStatusAndOrder(params.targetTableId, TableStatus.OCCUPIED, params.orderId)
        val updatedTargetTable = targetTable.copy(
            status = TableStatus.OCCUPIED,
            currentOrderId = params.orderId,
        )

        // Update order with new tableId
        val updatedOrder = order.copy(
            tableId = params.targetTableId,
            updatedAt = params.now,
        )
        orderRepo.save(updatedOrder)

        return Result.Success(
            updatedOrder = updatedOrder,
            previousTable = previousTable,
            targetTable = updatedTargetTable,
        )
    }
}
