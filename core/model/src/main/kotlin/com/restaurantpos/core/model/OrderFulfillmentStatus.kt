package com.restaurantpos.core.model

/**
 * Tracks kitchen/pickup readiness independently of [OrderStatus] (the payment state machine).
 * Needed because prepaid Kiosk orders go PLACED -> READY -> CLOSED on payment, before food is made.
 */
enum class OrderFulfillmentStatus {
    NOT_READY,
    READY_FOR_PICKUP,
    PICKED_UP,
}
