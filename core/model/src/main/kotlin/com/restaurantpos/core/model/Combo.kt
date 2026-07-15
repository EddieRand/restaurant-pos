package com.restaurantpos.core.model

data class ComboComponent(
    val menuItemId: String,
    val quantity: Int = 1,
    /** IDs of items that can substitute this component. */
    val substitutableWith: List<String> = emptyList(),
)

data class Combo(
    val id: String,
    val names: Map<String, String>,
    val components: List<ComboComponent>,
    val comboPriceMinorUnit: Long,   // total combo price (usually discounted vs. à la carte)
    val taxRateId: String? = null,
)
