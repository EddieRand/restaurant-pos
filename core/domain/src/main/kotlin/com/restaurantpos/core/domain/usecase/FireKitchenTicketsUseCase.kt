package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.model.OrderItemStatus
import java.util.UUID

/**
 * Called after [PlaceOrderUseCase] to route PLACED order items to kitchen stations.
 *
 * Groups items by station via [KitchenRouter], then creates one [KitchenTicket] per station.
 * Only items in PLACED (newly placed) status are included — items already PREPARING/SERVED
 * are already accounted for.
 */
class FireKitchenTicketsUseCase(
    private val orderRepo: OrderRepository,
    private val ticketRepo: KitchenTicketRepository,
    private val router: KitchenRouter,
) {
    suspend operator fun invoke(orderId: String): List<KitchenTicket> {
        // Items already on a ticket stay there — a re-fire must only ticket the new items.
        val ticketedItemIds = ticketRepo.getByOrder(orderId).flatMapTo(mutableSetOf()) { it.orderItemIds }
        val items = orderRepo.getItemsByOrder(orderId)
            .filter { it.status == OrderItemStatus.PLACED && it.id !in ticketedItemIds }

        if (items.isEmpty()) return emptyList()

        val grouped = router.groupByStation(items) { it.categoryId ?: router.defaultStationId }
        val now = System.currentTimeMillis()

        val tickets = grouped.map { (stationId, stationItems) ->
            KitchenTicket(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                orderItemIds = stationItems.map { it.id },
                stationId = stationId,
                course = stationItems.minOf { it.course },
                status = KitchenTicketStatus.NEW,
                createdAt = now,
            )
        }

        ticketRepo.saveAll(tickets)
        return tickets
    }
}
