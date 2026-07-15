package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.statemachine.KitchenTicketStateMachine

class RecallTicketUseCase(private val repo: KitchenTicketRepository) {
    suspend operator fun invoke(ticketId: String) {
        val ticket = repo.getById(ticketId) ?: error("Ticket $ticketId not found")
        val recalled = KitchenTicketStateMachine.onRecall(ticket.status)
        repo.updateStatus(ticketId, recalled)
        val preparing = KitchenTicketStateMachine.onRestartFromRecall(recalled)
        repo.updateStatus(ticketId, preparing)
    }
}
