package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.ModifierGroup
import com.restaurantpos.core.model.ModifierGroupType

@Entity(
    tableName = "modifier_groups",
    indices = [Index(value = ["menuItemId"])],
)
data class ModifierGroupEntity(
    @PrimaryKey val id: String,
    val menuItemId: String,
    val names: Map<String, String>,
    val type: String,
    val required: Boolean,
    val minSelect: Int,
    val maxSelect: Int,
    val sortOrder: Int = 0,
) {
    fun toDomain(modifiers: List<ModifierEntity>) = ModifierGroup(
        id = id,
        names = names,
        type = ModifierGroupType.valueOf(type),
        required = required,
        minSelect = minSelect,
        maxSelect = maxSelect,
        modifiers = modifiers.sortedBy { it.sortOrder }.map { it.toDomain() },
    )

    companion object {
        fun fromDomain(menuItemId: String, g: ModifierGroup, sortOrder: Int = 0) = ModifierGroupEntity(
            id = g.id,
            menuItemId = menuItemId,
            names = g.names,
            type = g.type.name,
            required = g.required,
            minSelect = g.minSelect,
            maxSelect = g.maxSelect,
            sortOrder = sortOrder,
        )
    }
}
