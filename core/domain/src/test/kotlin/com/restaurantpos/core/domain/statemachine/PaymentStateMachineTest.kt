package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.PaymentStatus
import org.junit.Assert.*
import org.junit.Test

class PaymentStateMachineTest {

    // ── Legal transitions ──────────────────────────────────────────────

    @Test fun `UNPAID + startProcessing = PROCESSING`() =
        assertEquals(PaymentStatus.PROCESSING, PaymentStateMachine.onStartProcessing(PaymentStatus.UNPAID))

    // Partial payment path: PROCESSING → PARTIAL
    @Test fun `PROCESSING + partialSuccess = PARTIAL`() =
        assertEquals(PaymentStatus.PARTIAL, PaymentStateMachine.onPartialSuccess(PaymentStatus.PROCESSING))

    // Full payment in one shot: PROCESSING → PAID
    @Test fun `PROCESSING + fullSuccess = PAID`() =
        assertEquals(PaymentStatus.PAID, PaymentStateMachine.onFullSuccess(PaymentStatus.PROCESSING))

    // Remaining balance paid: PARTIAL → PAID
    @Test fun `PARTIAL + fullSuccess = PAID`() =
        assertEquals(PaymentStatus.PAID, PaymentStateMachine.onFullSuccess(PaymentStatus.PARTIAL))

    // Refund from PAID
    @Test fun `PAID + refund = REFUNDED`() =
        assertEquals(PaymentStatus.REFUNDED, PaymentStateMachine.onRefund(PaymentStatus.PAID))

    // Refund from PARTIAL
    @Test fun `PARTIAL + refund = REFUNDED`() =
        assertEquals(PaymentStatus.REFUNDED, PaymentStateMachine.onRefund(PaymentStatus.PARTIAL))

    // ── Illegal transitions — must use block body so return type is Unit ──

    // onStartProcessing: only UNPAID allowed
    @Test(expected = IllegalArgumentException::class)
    fun `PROCESSING + startProcessing throws`() { PaymentStateMachine.onStartProcessing(PaymentStatus.PROCESSING) }

    @Test(expected = IllegalArgumentException::class)
    fun `PARTIAL + startProcessing throws`() { PaymentStateMachine.onStartProcessing(PaymentStatus.PARTIAL) }

    @Test(expected = IllegalArgumentException::class)
    fun `PAID + startProcessing throws`() { PaymentStateMachine.onStartProcessing(PaymentStatus.PAID) }

    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + startProcessing throws`() { PaymentStateMachine.onStartProcessing(PaymentStatus.REFUNDED) }

    // onPartialSuccess: only PROCESSING allowed
    @Test(expected = IllegalArgumentException::class)
    fun `UNPAID + partialSuccess throws`() { PaymentStateMachine.onPartialSuccess(PaymentStatus.UNPAID) }

    @Test(expected = IllegalArgumentException::class)
    fun `PARTIAL + partialSuccess throws`() { PaymentStateMachine.onPartialSuccess(PaymentStatus.PARTIAL) }

    @Test(expected = IllegalArgumentException::class)
    fun `PAID + partialSuccess throws`() { PaymentStateMachine.onPartialSuccess(PaymentStatus.PAID) }

    // onFullSuccess: only PROCESSING or PARTIAL allowed
    @Test(expected = IllegalArgumentException::class)
    fun `UNPAID + fullSuccess throws`() { PaymentStateMachine.onFullSuccess(PaymentStatus.UNPAID) }

    @Test(expected = IllegalArgumentException::class)
    fun `PAID + fullSuccess throws`() { PaymentStateMachine.onFullSuccess(PaymentStatus.PAID) }

    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + fullSuccess throws`() { PaymentStateMachine.onFullSuccess(PaymentStatus.REFUNDED) }

    // onRefund: only PAID or PARTIAL allowed
    @Test(expected = IllegalArgumentException::class)
    fun `UNPAID + refund throws`() { PaymentStateMachine.onRefund(PaymentStatus.UNPAID) }

    @Test(expected = IllegalArgumentException::class)
    fun `PROCESSING + refund throws`() { PaymentStateMachine.onRefund(PaymentStatus.PROCESSING) }

    @Test(expected = IllegalArgumentException::class)
    fun `REFUNDED + refund throws`() { PaymentStateMachine.onRefund(PaymentStatus.REFUNDED) }
}
