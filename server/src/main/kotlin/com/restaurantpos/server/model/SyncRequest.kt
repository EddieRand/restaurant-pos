package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val updatedAt: Long,
    val retryCount: Int = 0,
)

@Serializable
data class SyncPushResponse(
    val status: String,           // "accepted" | "conflict"
    val serverPayload: String? = null,
)

@Serializable
data class SyncPullResponse(
    val serverTime: Long,
    val menuItems: List<MenuItemDto> = emptyList(),
    val kitchenTickets: List<KitchenTicketDto> = emptyList(),
    val orders: List<OrderPullDto> = emptyList(),
    val orderItems: List<OrderItemPullDto> = emptyList(),
)

@Serializable
data class OrderPullDto(
    val id: String,
    val type: String,
    val tableId: String? = null,
    val guestCount: Int = 1,
    val sourceTerminalId: String,
    val operatorId: String = "",
    val subtotalMinorUnit: Long = 0,
    val taxTotalMinorUnit: Long = 0,
    val serviceChargeMinorUnit: Long = 0,
    val tipMinorUnit: Long = 0,
    val discountMinorUnit: Long = 0,
    val status: String,
    val orderNotes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val pickupCode: String? = null,
    val fulfillmentStatus: String = "NOT_READY",
)

@Serializable
data class OrderItemPullDto(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val menuItemNameSnapshot: String, // JSON map, passed through as stored
    val quantity: Int,
    val unitPriceMinorUnit: Long,
    val taxRateId: String? = null,
    val course: Int = 1,
    val status: String,
    val notes: String = "",
    val categoryId: String? = null,
    val allergenSnapshot: String = "",
    val comboId: String? = null,
)

@Serializable
data class KitchenTicketDto(
    val id: String,
    val orderId: String,
    val orderItemIds: List<String>,
    val stationId: String,
    val course: Int = 1,
    val status: String = "NEW",
    val createdAt: Long,
    val bumpedAt: Long? = null,
    val updatedAt: Long,
)

@Serializable
data class PinLoginRequest(
    val terminalId: String,
    val pin: String,
)

@Serializable
data class PasswordLoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val role: String,
    val displayName: String,
)

@Serializable
data class PermissionSyncPullResponse(
    val serverTime: Long,
    val roles: List<PermissionSyncRoleDto> = emptyList(),
    val rolePermissions: List<PermissionSyncMappingDto> = emptyList(),
)

@Serializable
data class PermissionSyncRoleDto(
    val id: String,
    val displayName: String,
    val isBuiltin: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class PermissionSyncMappingDto(
    val roleId: String,
    val permissionKey: String,
)

@Serializable
data class UserSyncPullResponse(
    val serverTime: Long,
    val users: List<UserSyncDto> = emptyList(),
)

@Serializable
data class UserSyncDto(
    val id: String,
    val displayName: String,
    val roleId: String,
    val pinHash: String,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
)

@Serializable
data class TableSyncPullResponse(
    val serverTime: Long,
    val tables: List<TableSyncDto> = emptyList(),
)

@Serializable
data class TableSyncDto(
    val id: String,
    val name: String,
    val sectionId: String,
    val capacity: Int = 4,
    val currentOrderId: String? = null,
    val status: String = "AVAILABLE",
    val updatedAt: Long = 0,
)

@Serializable
data class CustomerSyncPullResponse(
    val serverTime: Long,
    val customers: List<CustomerSyncDto> = emptyList(),
)

@Serializable
data class CustomerSyncDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: String = "",
    val notes: String? = null,
    val totalSpendMinorUnit: Long = 0,
    val loyaltyPoints: Long = 0,
    val membershipTierId: String? = null,
    val totalVisits: Int = 0,
    val lastVisitAt: Long = 0,
    val registeredAt: Long = 0,
    val updatedAt: Long = 0,
)

// ── Customer Display (CDS) public state ──────────────────────────────────────
// Money fields are whole-currency (minor units / 100) so the web CDS renders directly.

@Serializable
data class CdsStateResponse(
    val phase: String,                       // WELCOME / ORDER / TIP / PROCESSING / SUCCESS / RECEIPT
    val store: CdsStoreDto,
    val currencySymbol: String = "$",        // from the admin region config
    val minorDigits: Int = 2,                // currency minor digits from the region config
    val config: CdsDisplayConfigDto = CdsDisplayConfigDto(),
    val order: CdsOrderDto? = null,          // null in the idle WELCOME state
    val payment: CdsPaymentDto? = null,      // present once paid
)

@Serializable
data class CdsStoreDto(val name: String, val logoUrl: String? = null)

/** Display copy + toggles from the admin cdsConfig; drives the CDS screens. */
@Serializable
data class CdsDisplayConfigDto(
    val welcomeTitle: String = "Welcome!",
    val welcomeSubtitle: String = "Please review your order here.",
    val completionTitle: String = "Payment successful",
    val completionSubtitle: String = "Thank you. Your payment has been completed.",
    val showOrderItems: Boolean = true,
    val showRunningTotal: Boolean = true,
    val showModifiers: Boolean = true,
)

@Serializable
data class CdsOrderDto(
    val number: String,
    val type: String,                        // "Dine In" / "Takeaway" / "Delivery"
    val tableLabel: String? = null,
    val items: List<CdsOrderItemDto> = emptyList(),
    val totals: CdsTotalsDto,
)

@Serializable
data class CdsOrderItemDto(
    val qty: Int,
    val name: String,
    val modifiers: String? = null,
    val amount: Double,
)

@Serializable
data class CdsTotalsDto(
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val serviceCharge: Double,
    val tip: Double,
    val total: Double,
)

@Serializable
data class CdsPaymentDto(val totalPaid: Double, val change: Double)

@Serializable
data class ErrorResponse(val message: String)

@Serializable
data class TerminalLoginRequest(val terminalId: String)
