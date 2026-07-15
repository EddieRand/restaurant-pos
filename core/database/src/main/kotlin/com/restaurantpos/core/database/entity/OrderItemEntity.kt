package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Allergen
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderItemStatus

@Entity(
    tableName = "order_items",
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("orderId")],
)
data class OrderItemEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val menuItemId: String,
    val menuItemNameSnapshot: Map<String, String>,
    val quantity: Int,
    val unitPriceMinorUnit: Long,
    val taxRateId: String?,
    val course: Int,
    val status: OrderItemStatus,
    val notes: String,
    val categoryId: String? = null,
    /** Pipe-separated Allergen names snapshotted at order time. */
    val allergenSnapshot: String = "",
    /** Non-null if this item is part of a combo; links items from the same combo. */
    val comboId: String? = null,
) {
    fun toDomain() = OrderItem(
        id = id, orderId = orderId, menuItemId = menuItemId,
        menuItemNameSnapshot = menuItemNameSnapshot, quantity = quantity,
        unitPriceMinorUnit = unitPriceMinorUnit, taxRateId = taxRateId,
        course = course, status = status, notes = notes,
        categoryId = categoryId,
        comboId = comboId,
        allergenSnapshot = if (allergenSnapshot.isBlank()) emptySet()
                           else allergenSnapshot.split("|").mapNotNull { runCatching { Allergen.valueOf(it) }.getOrNull() }.toSet(),
    )

    companion object {
        fun fromDomain(i: OrderItem) = OrderItemEntity(
            id = i.id, orderId = i.orderId, menuItemId = i.menuItemId,
            menuItemNameSnapshot = i.menuItemNameSnapshot, quantity = i.quantity,
            unitPriceMinorUnit = i.unitPriceMinorUnit, taxRateId = i.taxRateId,
            course = i.course, status = i.status, notes = i.notes,
            categoryId = i.categoryId,
            comboId = i.comboId,
            allergenSnapshot = i.allergenSnapshot.joinToString("|") { it.name },
        )
    }
}
