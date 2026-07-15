package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.Payment
import com.restaurantpos.core.model.PaymentMethod
import com.restaurantpos.core.model.PaymentStatus
import java.util.UUID

/**
 * Issues a refund against a previously paid payment.
 *
 * - Creates a new Payment record with status=REFUNDED pointing to the original.
 * - Refund amount must be ≤ remaining refundable amount on the original payment.
 * - If net paid drops to ≤ 0, reopens the order (CLOSED→PLACED).
 */
class RefundUseCase(
    private val orderRepo: OrderRepository,
    private val paymentRepo: PaymentRepository,
) {
    data class Params(
        val originalPaymentId: String,
        val refundAmountMinorUnit: Long,
        val operatorId: String,
        val method: PaymentMethod = PaymentMethod.CASH,
        val now: Long = System.currentTimeMillis(),
    )

    sealed class Result {
        data class Success(val refundPayment: Payment, val order: Order) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        require(params.refundAmountMinorUnit > 0) { "Refund amount must be positive" }

        val originalPayment = paymentRepo.getById(params.originalPaymentId)
            ?: return Result.Error("Payment ${params.originalPaymentId} not found")

        if (originalPayment.status != PaymentStatus.PAID) {
            return Result.Error("Payment must be PAID to refund, was ${originalPayment.status}")
        }

        val orderPayments = paymentRepo.getByOrder(originalPayment.orderId)
        val alreadyRefunded = orderPayments
            .filter { it.refundedPaymentId == params.originalPaymentId }
            .sumOf { it.amountMinorUnit }
        val maxRefund = originalPayment.amountMinorUnit - alreadyRefunded
        if (params.refundAmountMinorUnit > maxRefund) {
            return Result.Error("Refund ${params.refundAmountMinorUnit} exceeds available ${maxRefund}")
        }

        val refundPayment = Payment(
            id = UUID.randomUUID().toString(),
            orderId = originalPayment.orderId,
            amountMinorUnit = params.refundAmountMinorUnit,
            method = params.method,
            status = PaymentStatus.REFUNDED,
            operatorId = params.operatorId,
            refundedPaymentId = params.originalPaymentId,
            createdAt = params.now,
        )
        paymentRepo.save(refundPayment)

        // Mark original as REFUNDED if fully covered
        if (alreadyRefunded + params.refundAmountMinorUnit >= originalPayment.amountMinorUnit) {
            paymentRepo.updateStatus(params.originalPaymentId, PaymentStatus.REFUNDED)
        }

        val order = orderRepo.getById(originalPayment.orderId)
            ?: return Result.Error("Order ${originalPayment.orderId} not found")

        // Reopen order if net paid ≤ 0
        val allPayments = paymentRepo.getByOrder(originalPayment.orderId)
        val netPaid = allPayments
            .filter { it.status == PaymentStatus.PAID }.sumOf { it.amountMinorUnit } -
            allPayments
            .filter { it.status == PaymentStatus.REFUNDED }.sumOf { it.amountMinorUnit }

        val updatedOrder = if (netPaid <= 0 && order.status == OrderStatus.CLOSED) {
            val reopened = order.copy(status = OrderStatus.PLACED, updatedAt = params.now)
            orderRepo.save(reopened)
            reopened
        } else order

        return Result.Success(refundPayment = refundPayment, order = updatedOrder)
    }
}
