package com.restaurantpos.core.model

data class Order(
    val id: String,
    val type: OrderType = OrderType.DINE_IN,
    val tableId: String? = null,
    val guestCount: Int = 1,
    val sourceTerminalId: String,
    val operatorId: String = "",
    val subtotalMinorUnit: Long = 0L,
    val taxTotalMinorUnit: Long = 0L,
    val serviceChargeMinorUnit: Long = 0L,
    /** Discretionary tip added at checkout (not included in tax base). */
    val tipMinorUnit: Long = 0L,
    /** Order-level discount in minor units (positive = reduction). */
    val discountMinorUnit: Long = 0L,
    val status: OrderStatus = OrderStatus.DRAFT,
    val createdAt: Long,
    val updatedAt: Long,
    /** Free-text note for the whole order (e.g. allergy info, birthday, rush). */
    val orderNotes: String = "",
    /** If this order was split from another, the original order id. */
    val splitFromOrderId: String? = null,
    /** If this order was merged into another, the target order id. */
    val mergedIntoOrderId: String? = null,
    /** Secondary table ids whose orders were merged into this order. */
    val mergedTableIds: List<String> = emptyList(),
    /** Daily-cycling pickup number (e.g. "1".."99") shown to kiosk customers; null for non-kiosk orders. */
    val pickupCode: String? = null,
    /** Kitchen/pickup readiness, independent of [status] (payment state machine). */
    val fulfillmentStatus: OrderFulfillmentStatus = OrderFulfillmentStatus.NOT_READY,
) {
    val totalMinorUnit: Long
        get() {
            val withTax = Math.addExact(subtotalMinorUnit, taxTotalMinorUnit)
            val withService = Math.addExact(withTax, serviceChargeMinorUnit)
            val withTip = Math.addExact(withService, tipMinorUnit)
            return Math.subtractExact(withTip, discountMinorUnit)
        }
}
