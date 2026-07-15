package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.OrderStatus

/**
 * Enforces valid order state transitions per PRD §5.1.
 * Any illegal transition throws IllegalStateException immediately.
 */
object OrderStateMachine {

    fun onFirstItemAdded(current: OrderStatus): OrderStatus {
        require(current == OrderStatus.DRAFT) { "Cannot add first item: order is $current" }
        return OrderStatus.IN_PROGRESS
    }

    fun onFire(current: OrderStatus): OrderStatus {
        require(current == OrderStatus.IN_PROGRESS) { "Cannot fire order: order is $current" }
        return OrderStatus.PLACED
    }

    /** Re-firing extra items on an already-fired order returns it to PLACED (kitchen has new work). */
    fun onAdditionalItemsFired(current: OrderStatus): OrderStatus {
        require(current == OrderStatus.PLACED || current == OrderStatus.READY) {
            "Cannot fire additional items: order is $current"
        }
        return OrderStatus.PLACED
    }

    fun onAllServed(current: OrderStatus): OrderStatus {
        require(current == OrderStatus.PLACED) { "Cannot mark all served: order is $current" }
        return OrderStatus.READY
    }

    fun onPaymentComplete(current: OrderStatus): OrderStatus {
        require(current == OrderStatus.READY) { "Cannot complete payment: order is $current" }
        return OrderStatus.CLOSED
    }

    /** Requires elevated permission — caller must enforce auth before calling. */
    fun onVoid(current: OrderStatus): OrderStatus {
        require(current != OrderStatus.CLOSED && current != OrderStatus.VOIDED) {
            "Cannot void order: order is already $current"
        }
        return OrderStatus.VOIDED
    }
}
