package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.OrderStateMachine
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.*

/**
 * Fires an order to the kitchen:
 *  1. Recalculates subtotal and tax from current items.
 *  2. Advances order status DRAFT→IN_PROGRESS→PLACED via state machine.
 *  3. Advances table status OCCUPIED→ORDERED.
 *  4. Persists all changes to local DB (Room is authoritative source).
 */
class PlaceOrderUseCase(
    private val orderRepo: OrderRepository,
    private val tableRepo: TableRepository,
    private val regionConfig: RegionConfig,
    private val allocatePickupCode: AllocatePickupCodeUseCase = AllocatePickupCodeUseCase(orderRepo),
) {
    data class Params(
        val orderId: String,
        val items: List<OrderItem>,
        val operatorId: String = "",
    )

    sealed class Result {
        data class Success(val order: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val order = orderRepo.getById(params.orderId)
            ?: return Result.Error("Order ${params.orderId} not found")

        if (params.items.isEmpty()) return Result.Error("Cannot place an empty order")

        // First fire: DRAFT→IN_PROGRESS→PLACED; re-fire (追加点菜): PLACED/READY→PLACED
        val placedStatus = when (order.status) {
            OrderStatus.DRAFT ->
                OrderStateMachine.onFire(OrderStateMachine.onFirstItemAdded(order.status))
            OrderStatus.IN_PROGRESS -> OrderStateMachine.onFire(order.status)
            OrderStatus.PLACED, OrderStatus.READY ->
                OrderStateMachine.onAdditionalItemsFired(order.status)
            else -> return Result.Error("Cannot place order in status ${order.status}")
        }

        // Persist fired items first so totals can be recalculated over the whole order —
        // on a re-fire params.items holds only the newly added items, not the full order.
        val placedItems = params.items.map { it.copy(status = OrderItemStatus.PLACED) }
        orderRepo.saveItems(placedItems)

        // Calculate totals — use lineTotalMinorUnit so modifier adjustments are included
        val activeItems = orderRepo.getItemsByOrder(params.orderId)
            .filter { it.status != OrderItemStatus.REFUNDED }
        val subtotal = activeItems.sumOf { it.lineTotalMinorUnit }
        val taxTotal = activeItems.sumOf { item ->
            val taxRate = item.taxRateId?.let { regionConfig.taxRateById(it) }
            taxRate?.taxOn(item.lineTotalMinorUnit) ?: 0L
        }
        val serviceCharge = if (regionConfig.serviceChargeRatePermille > 0) {
            subtotal * regionConfig.serviceChargeRatePermille / 1000
        } else {
            0L
        }

        // Self-pickup orders (no table) get a daily pickup number for the pickup display.
        val pickupCode = if (order.tableId == null && order.pickupCode == null) {
            allocatePickupCode()
        } else {
            order.pickupCode
        }

        val now = System.currentTimeMillis()
        val updatedOrder = order.copy(
            status = placedStatus,
            subtotalMinorUnit = subtotal,
            taxTotalMinorUnit = taxTotal,
            serviceChargeMinorUnit = serviceCharge,
            operatorId = params.operatorId.ifBlank { order.operatorId },
            pickupCode = pickupCode,
            updatedAt = now,
        )

        orderRepo.save(updatedOrder)

        // Advance table state OCCUPIED→ORDERED if applicable
        order.tableId?.let { tableId ->
            val table = tableRepo.getById(tableId)
            if (table != null && table.status == TableStatus.OCCUPIED) {
                val newTableStatus = TableStateMachine.onFirstOrderFired(table.status)
                tableRepo.updateStatusAndOrder(tableId, newTableStatus, order.id)
            }
        }

        return Result.Success(updatedOrder)
    }
}
