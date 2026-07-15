package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.TableStatus

/**
 * Enforces valid table state transitions per PRD §5.3.
 */
object TableStateMachine {

    fun onSeated(current: TableStatus): TableStatus {
        require(current == TableStatus.AVAILABLE) { "Cannot seat: table is $current" }
        return TableStatus.OCCUPIED
    }

    fun onFirstOrderFired(current: TableStatus): TableStatus {
        require(current == TableStatus.OCCUPIED) { "Cannot advance to ORDERED: table is $current" }
        return TableStatus.ORDERED
    }

    fun onCheckoutStarted(current: TableStatus): TableStatus {
        require(current == TableStatus.ORDERED) { "Cannot start checkout: table is $current" }
        return TableStatus.CHECKOUT
    }

    fun onPaymentComplete(current: TableStatus): TableStatus {
        require(current == TableStatus.CHECKOUT) { "Cannot complete payment: table is $current" }
        return TableStatus.DIRTY
    }

    fun onCleared(current: TableStatus): TableStatus {
        require(current == TableStatus.DIRTY) { "Cannot clear: table is $current" }
        return TableStatus.AVAILABLE
    }

    fun onReserved(current: TableStatus): TableStatus {
        require(current == TableStatus.AVAILABLE) { "Cannot reserve: table is $current" }
        return TableStatus.RESERVED
    }

    fun onReservationReleased(current: TableStatus): TableStatus {
        require(current == TableStatus.RESERVED) { "Cannot release reservation: table is $current" }
        return TableStatus.AVAILABLE
    }
}
