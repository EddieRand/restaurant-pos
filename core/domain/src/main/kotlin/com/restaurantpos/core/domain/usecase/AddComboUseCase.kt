package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.ComboRepository
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderItemStatus
import java.util.UUID

/**
 * Expands a combo into individual OrderItems.
 *
 * Pricing strategy: the first component item carries the full combo price;
 * remaining items are priced at 0. This ensures lineTotalMinorUnit on the
 * order sums to comboPriceMinorUnit regardless of component count.
 */
class AddComboUseCase(
    private val comboRepo: ComboRepository,
    private val menuItemRepo: MenuItemRepository,
    private val orderRepo: OrderRepository,
) {
    sealed interface Result {
        data class Success(val itemIds: List<String>) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(orderId: String, comboId: String): Result {
        val combo = comboRepo.getById(comboId)
            ?: return Result.Error("Combo not found: $comboId")
        if (combo.components.isEmpty())
            return Result.Error("Combo has no components")

        val items = mutableListOf<OrderItem>()
        combo.components.forEachIndexed { idx, component ->
            val menuItem = menuItemRepo.getById(component.menuItemId)
                ?: return Result.Error("Menu item not found: ${component.menuItemId}")

            val unitPrice = if (idx == 0) combo.comboPriceMinorUnit else 0L

            repeat(component.quantity) {
                items += OrderItem(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    menuItemId = menuItem.id,
                    menuItemNameSnapshot = menuItem.names,
                    quantity = 1,
                    unitPriceMinorUnit = unitPrice.takeIf { _ -> idx == 0 && it == 0 } ?: unitPrice,
                    taxRateId = combo.taxRateId ?: menuItem.taxRateId,
                    categoryId = menuItem.categoryId,
                    course = menuItem.course,
                    status = OrderItemStatus.PENDING,
                    comboId = comboId,
                    allergenSnapshot = menuItem.allergens,
                )
            }
        }

        // Price fixup: only the very first item carries the combo price
        val priced = items.mapIndexed { idx, item ->
            item.copy(unitPriceMinorUnit = if (idx == 0) combo.comboPriceMinorUnit else 0L)
        }

        orderRepo.saveItems(priced)
        return Result.Success(priced.map { it.id })
    }
}
