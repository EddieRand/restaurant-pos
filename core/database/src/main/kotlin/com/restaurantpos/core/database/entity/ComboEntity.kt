package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Combo
import com.restaurantpos.core.model.ComboComponent

@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey val id: String,
    val names: Map<String, String>,
    val comboPriceMinorUnit: Long,
    val taxRateId: String? = null,
    val isActive: Int = 1,
)

@Entity(tableName = "combo_components")
data class ComboComponentEntity(
    @PrimaryKey val id: String,
    val comboId: String,
    val menuItemId: String,
    val quantity: Int = 1,
    val sortOrder: Int = 0,
) {
    fun toDomain() = ComboComponent(menuItemId = menuItemId, quantity = quantity)
}

fun ComboEntity.toDomain(components: List<ComboComponentEntity>) = Combo(
    id = id,
    names = names,
    components = components.sortedBy { it.sortOrder }.map { it.toDomain() },
    comboPriceMinorUnit = comboPriceMinorUnit,
    taxRateId = taxRateId,
)
