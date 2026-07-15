package com.restaurantpos.core.model

enum class OrderStatus {
    DRAFT,       // created, no items yet
    IN_PROGRESS, // has items, not yet fired
    PLACED,      // fired to kitchen
    READY,       // all items served, awaiting payment
    CLOSED,      // payment complete
    VOIDED,      // cancelled (requires elevated permission)
}
