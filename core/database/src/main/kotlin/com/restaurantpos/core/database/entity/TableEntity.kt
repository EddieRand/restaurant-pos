package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus

@Entity(tableName = "tables")
data class TableEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sectionId: String,
    val capacity: Int,
    val currentOrderId: String?,
    val status: TableStatus,
    val updatedAt: Long = 0L,
) {
    fun toDomain() = Table(
        id = id, name = name, sectionId = sectionId, capacity = capacity,
        currentOrderId = currentOrderId, status = status, updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(t: Table) = TableEntity(
            id = t.id, name = t.name, sectionId = t.sectionId, capacity = t.capacity,
            currentOrderId = t.currentOrderId, status = t.status, updatedAt = t.updatedAt,
        )
    }
}
