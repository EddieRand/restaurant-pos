package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Allergen
import com.restaurantpos.core.model.MenuItem

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val names: Map<String, String>,
    val priceMinorUnit: Long,
    val taxRateId: String?,
    val categoryId: String,
    val course: Int,
    val isSoldOut: Boolean,
    val imageUrl: String?,
    /** Pipe-separated Allergen names, e.g. "GLUTEN|DAIRY" */
    val allergens: String = "",
    val stockCount: Long? = null,
    /** Server-side last-modified watermark; used as the pull-sync cursor (Batch 44). */
    val updatedAt: Long = 0L,
) {
    fun toDomain(menuProfileIds: List<String> = emptyList()) = MenuItem(
        id = id, names = names, priceMinorUnit = priceMinorUnit,
        taxRateId = taxRateId, categoryId = categoryId, course = course,
        isSoldOut = isSoldOut, imageUrl = imageUrl,
        allergens = if (allergens.isBlank()) emptySet()
                    else allergens.split("|").mapNotNull { runCatching { Allergen.valueOf(it) }.getOrNull() }.toSet(),
        stockCount = stockCount,
        menuProfileIds = menuProfileIds,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(m: MenuItem) = MenuItemEntity(
            id = m.id, names = m.names, priceMinorUnit = m.priceMinorUnit,
            taxRateId = m.taxRateId, categoryId = m.categoryId, course = m.course,
            isSoldOut = m.isSoldOut, imageUrl = m.imageUrl,
            allergens = m.allergens.joinToString("|") { it.name },
            stockCount = m.stockCount,
            updatedAt = m.updatedAt,
        )
    }
}
