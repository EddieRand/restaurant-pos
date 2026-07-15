package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.MenuProfile

@Entity(tableName = "menu_profiles")
data class MenuProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val startTime: String?,
    val endTime: String?,
    /** Comma-separated day numbers, e.g. "1,3,5"; null = every day */
    val daysOfWeek: String?,
    /** Pipe-separated channel strings, e.g. "DINE_IN|TAKEAWAY"; "" = all channels */
    val channels: String,
) {
    fun toDomain() = MenuProfile(
        id = id,
        name = name,
        enabled = enabled,
        startTime = startTime,
        endTime = endTime,
        daysOfWeek = daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() },
        channels = channels.split("|").filter { it.isNotBlank() },
    )

    companion object {
        fun fromDomain(p: MenuProfile) = MenuProfileEntity(
            id = p.id,
            name = p.name,
            enabled = p.enabled,
            startTime = p.startTime,
            endTime = p.endTime,
            daysOfWeek = p.daysOfWeek?.joinToString(",")?.ifEmpty { null },
            channels = p.channels.joinToString("|"),
        )
    }
}
