package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.OrderItem

/**
 * Calculates how to split an order's total across N parties.
 *
 * Three strategies:
 * - EvenSplit: total / N, last party absorbs rounding remainder.
 * - ByPerson: each party's itemIds list → sum their lines.
 * - ByItem: alias for ByPerson — each party specifies which items they pay for.
 *
 * Does NOT create new orders or save anything — returns a pure split plan.
 * The caller drives payment collection using SettlePaymentUseCase for each party's share.
 */
class SplitBillUseCase(
    private val orderRepo: OrderRepository,
) {
    sealed class Strategy {
        data class Even(val partyCount: Int) : Strategy()
        /** Each inner list is the set of item IDs belonging to one party. */
        data class ByItem(val parties: List<Set<String>>) : Strategy()
    }

    data class PartyShare(
        val index: Int,
        val amountMinorUnit: Long,
        val itemIds: Set<String>,
    )

    sealed class Result {
        data class Success(val shares: List<PartyShare>, val total: Long) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(orderId: String, strategy: Strategy): Result {
        val order = orderRepo.getById(orderId)
            ?: return Result.Error("Order $orderId not found")

        val items = orderRepo.getItemsByOrder(orderId)
        val total = order.totalMinorUnit

        return when (strategy) {
            is Strategy.Even -> evenSplit(strategy.partyCount, items, total)
            is Strategy.ByItem -> byItemSplit(strategy.parties, items, total)
        }
    }

    private fun evenSplit(n: Int, items: List<OrderItem>, total: Long): Result {
        require(n >= 2) { "Must split between at least 2 parties" }
        val base = total / n
        val remainder = total % n
        val shares = List(n) { i ->
            PartyShare(
                index = i,
                amountMinorUnit = if (i == n - 1) base + remainder else base,
                itemIds = emptySet(), // even split doesn't associate items to parties
            )
        }
        return Result.Success(shares, total)
    }

    private fun byItemSplit(parties: List<Set<String>>, items: List<OrderItem>, total: Long): Result {
        require(parties.size >= 2) { "Must have at least 2 parties" }

        val allAssignedIds = parties.flatten().toSet()
        val unassigned = items.filter { it.id !in allAssignedIds }

        // Unassigned items split evenly across parties (rounding last party)
        val unassignedTotal = unassigned.sumOf { it.lineTotalMinorUnit }
        val unassignedPerParty = unassignedTotal / parties.size
        val unassignedRemainder = unassignedTotal % parties.size

        val shares = parties.mapIndexed { i, partyItemIds ->
            val partyItemsTotal = items
                .filter { it.id in partyItemIds }
                .sumOf { it.lineTotalMinorUnit }
            val unassignedShare = if (i == parties.size - 1)
                unassignedPerParty + unassignedRemainder
            else unassignedPerParty

            // Distribute order-level adjustments (service charge, discount) proportionally
            val subtotal = items.sumOf { it.lineTotalMinorUnit }
            val adjustments = total - subtotal // service charge - discount + tax
            val adjustmentShare = if (subtotal > 0L)
                (adjustments * (partyItemsTotal + unassignedShare)) / subtotal
            else adjustments / parties.size

            PartyShare(
                index = i,
                amountMinorUnit = partyItemsTotal + unassignedShare + adjustmentShare,
                itemIds = partyItemIds,
            )
        }

        return Result.Success(shares, total)
    }
}
