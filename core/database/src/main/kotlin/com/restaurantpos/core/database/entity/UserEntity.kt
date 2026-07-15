package com.restaurantpos.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.User

@Entity(
    tableName = "users",
    indices = [Index(value = ["pinHash"], unique = true)],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    @ColumnInfo(name = "role") val roleId: String,  // DB 列名仍为 "role"，值现为小写 roleId
    val pinHash: String,
    val isActive: Boolean,
    val createdAt: Long,
) {
    fun toDomain() = User(
        id = id,
        displayName = displayName,
        roleId = roleId,
        pinHash = pinHash,
        isActive = isActive,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(u: User) = UserEntity(
            id = u.id,
            displayName = u.displayName,
            roleId = u.roleId,
            pinHash = u.pinHash,
            isActive = u.isActive,
            createdAt = u.createdAt,
        )
    }
}
