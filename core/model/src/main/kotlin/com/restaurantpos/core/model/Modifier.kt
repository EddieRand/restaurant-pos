package com.restaurantpos.core.model

enum class ModifierGroupType { SINGLE, MULTI }

data class ModifierGroup(
    val id: String,
    val names: Map<String, String>,
    val type: ModifierGroupType = ModifierGroupType.SINGLE,
    val required: Boolean = false,
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val modifiers: List<Modifier> = emptyList(),
)

data class Modifier(
    val id: String,
    val names: Map<String, String>,
    val priceAdjustmentMinorUnit: Long = 0L,  // positive = surcharge, negative = discount
)

/** Snapshot of modifier selection captured at order time. */
data class SelectedModifier(
    val groupId: String,
    val modifierId: String,
    val nameSnapshot: Map<String, String>,
    val priceAdjustmentMinorUnit: Long,
)
