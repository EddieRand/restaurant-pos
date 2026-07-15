package com.restaurantpos.core.domain.statemachine

import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.model.KitchenTicketStatus.*

/**
 * KDS ticket lifecycle:
 *
 *   NEW ──► PREPARING ──► DONE
 *                          │
 *                          └──► RECALLED ──► PREPARING
 */
object KitchenTicketStateMachine {

    /** Chef starts working on the ticket. */
    fun onStartPreparing(current: KitchenTicketStatus): KitchenTicketStatus {
        require(current == NEW) { "Cannot start preparing from $current" }
        return PREPARING
    }

    /** Chef bumps (marks done) the ticket. */
    fun onBump(current: KitchenTicketStatus): KitchenTicketStatus {
        require(current == PREPARING || current == NEW) { "Cannot bump from $current" }
        return DONE
    }

    /** Manager recalls a previously bumped ticket (e.g., mistake, re-fire). */
    fun onRecall(current: KitchenTicketStatus): KitchenTicketStatus {
        require(current == DONE) { "Cannot recall from $current" }
        return RECALLED
    }

    /** Recalled ticket goes back to being prepared. */
    fun onRestartFromRecall(current: KitchenTicketStatus): KitchenTicketStatus {
        require(current == RECALLED) { "Cannot restart from $current" }
        return PREPARING
    }
}
