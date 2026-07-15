package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.OrderItemStatus

/**
 * Enforces valid order item state transitions per PRD §5.2.
 *
 * Main flow: PENDING → PLACED → PREPARING → SERVED
 * Branches:  REFUNDED / COMPED can be reached from main-flow states.
 *
 * Any illegal transition throws IllegalStateException immediately.
 */
object OrderItemStateMachine {

    /**
     * Item is fired to kitchen.
     * PENDING → PLACED
     */
    fun onFire(current: OrderItemStatus): OrderItemStatus {
        require(current == OrderItemStatus.PENDING) {
            "Cannot fire item: item is $current, expected PENDING"
        }
        return OrderItemStatus.PLACED
    }

    /**
     * Kitchen acknowledges the order item and starts prepping.
     * PLACED → PREPARING
     */
    fun onKitchenAcknowledge(current: OrderItemStatus): OrderItemStatus {
        require(current == OrderItemStatus.PLACED) {
            "Cannot acknowledge: item is $current, expected PLACED"
        }
        return OrderItemStatus.PREPARING
    }

    /**
     * Item is served to the table.
     * PREPARING → SERVED
     */
    fun onServe(current: OrderItemStatus): OrderItemStatus {
        require(current == OrderItemStatus.PREPARING) {
            "Cannot serve: item is $current, expected PREPARING"
        }
        return OrderItemStatus.SERVED
    }

    /**
     * Item is refunded (returned by customer).
     * Allowed from: PLACED, PREPARING, SERVED
     * (Void before kitchen starts, or refund after serving.)
     */
    fun onRefund(current: OrderItemStatus): OrderItemStatus {
        require(
            current == OrderItemStatus.PLACED ||
            current == OrderItemStatus.PREPARING ||
            current == OrderItemStatus.SERVED
        ) {
            "Cannot refund item: item is $current, must be PLACED/PREPARING/SERVED"
        }
        return OrderItemStatus.REFUNDED
    }

    /**
     * Item is comped (given for free, manager decision).
     * Allowed from any non-final state.
     */
    fun onComp(current: OrderItemStatus): OrderItemStatus {
        require(current != OrderItemStatus.REFUNDED && current != OrderItemStatus.COMPED) {
            "Cannot comp item: item is already $current"
        }
        return OrderItemStatus.COMPED
    }
}
