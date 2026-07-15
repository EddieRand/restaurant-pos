package com.restaurantpos.core.model

enum class TableStatus {
    AVAILABLE,
    OCCUPIED,       // seated, not yet ordered
    ORDERED,        // order fired to kitchen
    CHECKOUT,       // payment in progress
    DIRTY,          // payment done, awaiting clearance
    RESERVED,       // reservation lock
}
