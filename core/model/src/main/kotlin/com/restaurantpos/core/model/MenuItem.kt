package com.restaurantpos.core.model

/**
 * Snapshot-safe menu item. Price is always Long (cents/smallest currency unit).
 * Names are i18n maps: locale tag (e.g. "en", "zh-CN") -> display name.
 */
/** Common allergens as a stable enum so they can be stored/displayed consistently. */
enum class Allergen { GLUTEN, DAIRY, EGGS, NUTS, PEANUTS, SHELLFISH, FISH, SOY, SESAME }

data class MenuItem(
    val id: String,
    val names: Map<String, String>,      // locale -> name
    val priceMinorUnit: Long,            // e.g. 1200 = $12.00
    val taxRateId: String?,              // references TaxRate, null = tax-exempt
    val categoryId: String,
    val course: Int = 1,
    val isSoldOut: Boolean = false,
    val imageUrl: String? = null,
    val allergens: Set<Allergen> = emptySet(),
    val stockCount: Long? = null,        // null = unlimited; auto-marks soldOut when 0
    val menuProfileIds: List<String> = emptyList(), // empty = visible in all profiles
    val updatedAt: Long = 0L,             // server-side last-modified watermark (pull-sync cursor)
)
