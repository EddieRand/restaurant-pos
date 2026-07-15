package com.restaurantpos.core.model

/**
 * Snapshot of a menu item at order time. Prices never change post-order.
 * unitPriceMinorUnit is the BASE price; effectiveUnitPrice adds modifier adjustments.
 */
data class OrderItem(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val menuItemNameSnapshot: Map<String, String>, // locale -> name at order time
    val quantity: Int,
    val unitPriceMinorUnit: Long,                  // base price per unit (no modifiers)
    val taxRateId: String?,
    val course: Int = 1,
    val status: OrderItemStatus = OrderItemStatus.PENDING,
    val notes: String = "",
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val categoryId: String? = null,                // snapshot of MenuItem.categoryId for kitchen routing
    val comboId: String? = null,                   // non-null if part of a combo order
    val allergenSnapshot: Set<Allergen> = emptySet(), // snapshot of allergens at order time
) {
    val modifierAdjustmentPerUnit: Long
        get() = selectedModifiers.sumOf { it.priceAdjustmentMinorUnit }

    val effectiveUnitPrice: Long
        get() = unitPriceMinorUnit + modifierAdjustmentPerUnit

    val lineTotalMinorUnit: Long
        get() = effectiveUnitPrice * quantity
}
