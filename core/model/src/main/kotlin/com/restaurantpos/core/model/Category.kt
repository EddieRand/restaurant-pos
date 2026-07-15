package com.restaurantpos.core.model

enum class VisibleOn { CASHIER, HANDHELD, KIOSK, KDS }

data class Category(
    val id: String,
    val names: Map<String, String>,
    val sortOrder: Int = 0,
    val visibleOn: Set<VisibleOn> = setOf(VisibleOn.CASHIER, VisibleOn.HANDHELD, VisibleOn.KIOSK),
)
