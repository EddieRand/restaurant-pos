package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Modifier

@Entity(
    tableName = "modifiers",
    indices = [Index(value = ["groupId"])],
)
data class ModifierEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val names: Map<String, String>,
    val priceAdjustmentMinorUnit: Long,
    val sortOrder: Int = 0,
) {
    fun toDomain() = Modifier(
        id = id,
        names = names,
        priceAdjustmentMinorUnit = priceAdjustmentMinorUnit,
    )

    companion object {
        fun fromDomain(groupId: String, m: Modifier, sortOrder: Int = 0) = ModifierEntity(
            id = m.id,
            groupId = groupId,
            names = m.names,
            priceAdjustmentMinorUnit = m.priceAdjustmentMinorUnit,
            sortOrder = sortOrder,
        )
    }
}
