package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.statemachine.KitchenTicketStateMachine
import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.model.OrderFulfillmentStatus

class BumpTicketUseCase(
    private val repo: KitchenTicketRepository,
    private val orderRepo: OrderRepository,
) {
    suspend operator fun invoke(ticketId: String) {
        val ticket = repo.getById(ticketId) ?: error("Ticket $ticketId not found")
        val newStatus = KitchenTicketStateMachine.onBump(ticket.status)
        repo.updateStatus(ticketId, newStatus, bumpedAt = System.currentTimeMillis())

        if (newStatus == KitchenTicketStatus.DONE) {
            val allDone = repo.getByOrder(ticket.orderId).all { it.status == KitchenTicketStatus.DONE }
            if (allDone) {
                orderRepo.updateFulfillmentStatus(ticket.orderId, OrderFulfillmentStatus.READY_FOR_PICKUP)
            }
        }
    }
}
