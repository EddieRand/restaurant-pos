package com.restaurantpos.core.hardware

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.model.OrderItemStatus

/**
 * Routes new/pending order items to kitchen stations and prints one ticket per station.
 * Uses [KitchenRouter] from core:domain (now the single source of routing truth).
 * [FireKitchenTicketsUseCase] also uses the same router to create digital tickets.
 */
class PrintKitchenTicketUseCase(
    private val orderRepo: OrderRepository,
    private val printer: PrinterPort,
    private val router: KitchenRouter,
    private val categoryNameProvider: (String) -> String = { it },
) {
    data class StationResult(val stationId: String, val result: PrintResult)

    sealed class Result {
        data class Success(val stationResults: List<StationResult>) : Result()
        data class Error(val reason: String) : Result()
    }

    suspend operator fun invoke(orderId: String, locale: String = "en"): Result {
        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order $orderId not found")

        val allItems = orderRepo.getItemsByOrder(orderId)
        val pendingItems = allItems.filter { it.status == OrderItemStatus.PENDING }

        if (pendingItems.isEmpty()) return Result.Error("No pending items to send to kitchen")

        val grouped = router.groupByStation(pendingItems) { it.categoryId ?: router.defaultStationId }

        val stationResults = mutableListOf<StationResult>()
        grouped.forEach { (stationId, items) ->
            val kitchenLines = items.map { item ->
                val name = item.menuItemNameSnapshot[locale]
                    ?: item.menuItemNameSnapshot.values.firstOrNull() ?: "Item"
                val modifierNotes = item.selectedModifiers
                    .mapNotNull { m -> m.nameSnapshot[locale] }
                    .joinToString(", ")
                val notes = buildString {
                    if (item.notes.isNotBlank()) append(item.notes)
                    if (modifierNotes.isNotBlank()) { if (isNotEmpty()) append("; "); append(modifierNotes) }
                }
                KitchenItemLine(name = name, quantity = item.quantity, notes = notes)
            }
            val ticketData = KitchenTicketData(
                orderId = orderId,
                tableId = order.tableId,
                items = kitchenLines,
                course = items.minOf { it.course },
                stationId = stationId,
            )
            stationResults.add(StationResult(stationId, printer.printKitchenTicket(ticketData)))
        }

        return Result.Success(stationResults)
    }
}
