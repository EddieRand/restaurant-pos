package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.PaymentStatus

/**
 * Enforces valid payment state transitions per PRD §5.4.
 *
 * Main flow: UNPAID → PROCESSING → PARTIAL → PAID → REFUNDED
 *                                    ↘ PAID ──↗
 *
 * Any illegal transition throws IllegalStateException immediately.
 */
object PaymentStateMachine {

    /**
     * Payment attempt is initiated (e.g. card swiped, cash tendered).
     * UNPAID → PROCESSING
     */
    fun onStartProcessing(current: PaymentStatus): PaymentStatus {
        require(current == PaymentStatus.UNPAID) {
            "Cannot start processing: payment is $current, expected UNPAID"
        }
        return PaymentStatus.PROCESSING
    }

    /**
     * Payment partially succeeded (e.g. paid $20 of $50 total).
     * PROCESSING → PARTIAL
     */
    fun onPartialSuccess(current: PaymentStatus): PaymentStatus {
        require(current == PaymentStatus.PROCESSING) {
            "Cannot record partial payment: payment is $current, expected PROCESSING"
        }
        return PaymentStatus.PARTIAL
    }

    /**
     * Payment fully succeeded.
     * PROCESSING → PAID  (paid in one shot)
     * PARTIAL    → PAID  (remaining balance paid)
     */
    fun onFullSuccess(current: PaymentStatus): PaymentStatus {
        require(
            current == PaymentStatus.PROCESSING || current == PaymentStatus.PARTIAL
        ) {
            "Cannot complete payment: payment is $current, expected PROCESSING or PARTIAL"
        }
        return PaymentStatus.PAID
    }

    /**
     * Payment is refunded (full or partial refund).
     * Allowed from: PAID, PARTIAL
     * (Cannot refund UNPAID/PROCESSING because nothing was captured.)
     */
    fun onRefund(current: PaymentStatus): PaymentStatus {
        require(
            current == PaymentStatus.PAID || current == PaymentStatus.PARTIAL
        ) {
            "Cannot refund: payment is $current, must be PAID or PARTIAL"
        }
        return PaymentStatus.REFUNDED
    }
}
