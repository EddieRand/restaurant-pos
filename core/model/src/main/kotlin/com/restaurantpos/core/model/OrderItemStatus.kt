package com.restaurantpos.core.model

enum class OrderItemStatus {
    PENDING,    // in cart, not yet fired
    PLACED,     // fired to kitchen
    PREPARING,  // kitchen acknowledged
    SERVED,     // delivered to table
    REFUNDED,   // returned
    COMPED,     // complimentary (gifted)
}
