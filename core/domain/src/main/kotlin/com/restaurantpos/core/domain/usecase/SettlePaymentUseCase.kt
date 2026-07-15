package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.OrderStateMachine
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.model.*

/**
 * Records a payment and closes the order + table if fully paid.
 *  1. Validates payment amount <= remaining balance.
 *  2. Persists Payment record (status PAID).
 *  3. If fully paid: advances order READY→CLOSED, table CHECKOUT→DIRTY.
 */
class SettlePaymentUseCase(
    private val orderRepo: OrderRepository,
    private val paymentRepo: PaymentRepository,
    private val tableRepo: TableRepository,
) {
    data class Params(
        val payment: Payment,
    )

    sealed class Result {
        data class Success(val order: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val payment = params.payment
        val order = orderRepo.getById(payment.orderId)
            ?: return Result.Error("Order ${payment.orderId} not found")

        if (order.status != OrderStatus.READY && order.status != OrderStatus.PLACED) {
            return Result.Error("Order cannot be settled in status ${order.status}")
        }

        // Validate not overpaying
        val existing = paymentRepo.getByOrder(payment.orderId)
        val alreadyPaid = existing.sumOf { it.amountMinorUnit }
        val remaining = order.totalMinorUnit - alreadyPaid
        if (payment.amountMinorUnit > remaining) {
            return Result.Error("Payment ${payment.amountMinorUnit} exceeds remaining ${remaining}")
        }

        val paidPayment = payment.copy(status = PaymentStatus.PAID)
        paymentRepo.save(paidPayment)

        val newPaidTotal = alreadyPaid + payment.amountMinorUnit
        val fullyPaid = newPaidTotal >= order.totalMinorUnit

        if (!fullyPaid) return Result.Success(order)

        // Full payment — READY→CLOSED (auto-advance from PLACED if needed for cash flow)
        val readyOrder = if (order.status == OrderStatus.PLACED) {
            val readyStatus = OrderStateMachine.onAllServed(order.status)
            order.copy(status = readyStatus, updatedAt = System.currentTimeMillis())
                .also { orderRepo.save(it) }
        } else order

        val closedStatus = OrderStateMachine.onPaymentComplete(readyOrder.status)
        val now = System.currentTimeMillis()
        val closedOrder = readyOrder.copy(status = closedStatus, updatedAt = now)
        orderRepo.save(closedOrder)

        // Advance table CHECKOUT→DIRTY then leave cleanup to staff
        order.tableId?.let { tableId ->
            val table = tableRepo.getById(tableId)
            if (table != null) {
                // Drive table to CHECKOUT if not already
                val checkoutTable = when (table.status) {
                    TableStatus.ORDERED -> {
                        val s = TableStateMachine.onCheckoutStarted(table.status)
                        tableRepo.updateStatusAndOrder(tableId, s, order.id)
                        table.copy(status = s)
                    }
                    else -> table
                }
                if (checkoutTable.status == TableStatus.CHECKOUT) {
                    val dirtyStatus = TableStateMachine.onPaymentComplete(checkoutTable.status)
                    tableRepo.updateStatusAndOrder(tableId, dirtyStatus, null)
                }
            }
        }

        return Result.Success(closedOrder)
    }
}
