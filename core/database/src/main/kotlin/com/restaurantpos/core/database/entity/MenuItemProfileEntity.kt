package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "menu_item_profiles",
    primaryKeys = ["menuItemId", "menuProfileId"],
    foreignKeys = [
        ForeignKey(
            entity = MenuItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MenuProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["menuProfileId"])],
)
data class MenuItemProfileEntity(
    val menuItemId: String,
    val menuProfileId: String,
)
