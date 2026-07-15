package com.restaurantpos.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurantpos.core.model.DailySnapshot

/**
 * Room entity for daily aggregated report snapshots.
 * Mirrors [DailySnapshot] (pure :core:model) — mapping happens in the repository layer.
 */
@Entity(tableName = "daily_snapshots")
data class DailySnapshotEntity(
    @PrimaryKey val date: String,
    val netRevenueMinorUnit: Long = 0L,
    val grossRevenueMinorUnit: Long = 0L,
    val orderCount: Int = 0,
    val guestCount: Int = 0,
    val avgCheckMinorUnit: Long = 0L,
    val avgPerGuestMinorUnit: Long = 0L,
    val discountTotalMinorUnit: Long = 0L,
    val taxTotalMinorUnit: Long = 0L,
    val serviceChargeTotalMinorUnit: Long = 0L,
    val tipTotalMinorUnit: Long = 0L,
    val paymentBreakdownJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

fun DailySnapshotEntity.toDomain() = DailySnapshot(
    date = date,
    netRevenueMinorUnit = netRevenueMinorUnit,
    grossRevenueMinorUnit = grossRevenueMinorUnit,
    orderCount = orderCount,
    guestCount = guestCount,
    avgCheckMinorUnit = avgCheckMinorUnit,
    avgPerGuestMinorUnit = avgPerGuestMinorUnit,
    discountTotalMinorUnit = discountTotalMinorUnit,
    taxTotalMinorUnit = taxTotalMinorUnit,
    serviceChargeTotalMinorUnit = serviceChargeTotalMinorUnit,
    tipTotalMinorUnit = tipTotalMinorUnit,
    paymentBreakdownJson = paymentBreakdownJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DailySnapshot.toEntity() = DailySnapshotEntity(
    date = date,
    netRevenueMinorUnit = netRevenueMinorUnit,
    grossRevenueMinorUnit = grossRevenueMinorUnit,
    orderCount = orderCount,
    guestCount = guestCount,
    avgCheckMinorUnit = avgCheckMinorUnit,
    avgPerGuestMinorUnit = avgPerGuestMinorUnit,
    discountTotalMinorUnit = discountTotalMinorUnit,
    taxTotalMinorUnit = taxTotalMinorUnit,
    serviceChargeTotalMinorUnit = serviceChargeTotalMinorUnit,
    tipTotalMinorUnit = tipTotalMinorUnit,
    paymentBreakdownJson = paymentBreakdownJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
