package com.restaurantpos.core.sync

/** Entity types that participate in sync. */
enum class SyncEntityType { ORDER, ORDER_ITEM, TABLE, PAYMENT, MENU_ITEM, RESERVATION, DAILY_SNAPSHOT, KITCHEN_TICKET, CUSTOMER, CDS_STATE }

/** The operation that created this outbox entry. */
enum class SyncOperation { CREATE, UPDATE, DELETE }

enum class SyncStatus { PENDING, IN_FLIGHT, DONE, FAILED }

/**
 * One entry in the outbox: a local write that hasn't been acknowledged by the server.
 *
 * Fields:
 * - [id]         : stable UUID for this outbox entry (not the entity id)
 * - [entityType] : which aggregate was mutated
 * - [entityId]   : primary key of the entity
 * - [operation]  : what happened
 * - [payload]    : JSON snapshot of the entity at write time (server receives this)
 * - [updatedAt]  : local epoch-ms timestamp; used for last-write-wins conflict resolution
 * - [retryCount] : how many times we've tried and failed to deliver
 * - [status]     : current delivery status
 */
data class SyncRecord(
    val id: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,           // JSON
    val updatedAt: Long,
    val retryCount: Int = 0,
    val status: SyncStatus = SyncStatus.PENDING,
)
