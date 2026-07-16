package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

// ── Channels ──────────────────────────────────────────────────────────────────

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val sortOrder: Int = 10,
    val enabled: Boolean = true,
    val color: String = "gray",
)

@Serializable
data class CreateChannelRequest(
    val id: String,
    val name: String,
    val sortOrder: Int = 10,
    val color: String = "gray",
)

@Serializable
data class UpdateChannelRequest(
    val name: String? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    val color: String? = null,
)

// ── Menu Categories ───────────────────────────────────────────────────────────

@Serializable
data class MenuCategoryDto(
    val id: String,
    val name: String,
    val sortOrder: Int = 10,
    val activeFrom: String? = null,     // "HH:MM" or null = all day
    val activeTo: String? = null,       // "HH:MM" or null = all day
    val daysOfWeek: List<Int>? = null,  // null/empty = every day; 0=Sun…6=Sat
)

@Serializable
data class CreateMenuCategoryRequest(
    val name: String,
    val sortOrder: Int = 10,
    val activeFrom: String? = null,
    val activeTo: String? = null,
    val daysOfWeek: List<Int>? = null,
)

@Serializable
data class UpdateMenuCategoryRequest(
    val name: String? = null,
    val sortOrder: Int? = null,
    val activeFrom: String? = null,
    val activeTo: String? = null,
    val daysOfWeek: List<Int>? = null,
)

// ── Payment Methods ─────────────────────────────────────────────────────────

@Serializable
data class PaymentMethodDto(
    val id: String,
    val code: String,
    val baseType: String,
    val displayName: String,
    val color: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Serializable
data class CreatePaymentMethodRequest(
    val code: String,
    val baseType: String = "OTHER",
    val displayName: String,
    val color: String = "gray",
    val sortOrder: Int = 10,
    val isActive: Boolean = true,
)

@Serializable
data class UpdatePaymentMethodRequest(
    val displayName: String? = null,
    val color: String? = null,
    val sortOrder: Int? = null,
    val isActive: Boolean? = null,
)

// ── Menu ──────────────────────────────────────────────────────────────────────

@Serializable
data class MenuItemDto(
    val id: String,
    val names: String,                        // JSON object string: {"en":"...","zh":"..."}
    val priceMinorUnit: Long,
    val taxRateId: String? = null,
    val categoryId: String,
    val course: Int = 1,
    val isSoldOut: Boolean = false,
    val imageUrl: String? = null,
    val allergens: String = "",               // pipe-separated
    val availableChannels: List<String> = emptyList(), // empty = all channels
    val stockCount: Long? = null,             // null = unlimited
    val updatedAt: Long,
)

@Serializable
data class CreateMenuItemRequest(
    val id: String,
    val names: String,
    val priceMinorUnit: Long,
    val taxRateId: String? = null,
    val categoryId: String,
    val course: Int = 1,
    val imageUrl: String? = null,
    val allergens: String = "",
    val availableChannels: List<String> = emptyList(),
    val stockCount: Long? = null,
)

@Serializable
data class UpdateMenuItemRequest(
    val names: String? = null,
    val priceMinorUnit: Long? = null,
    val taxRateId: String? = null,
    val categoryId: String? = null,
    val course: Int? = null,
    val isSoldOut: Boolean? = null,
    val imageUrl: String? = null,
    val allergens: String? = null,
    val availableChannels: List<String>? = null, // null = don't change; empty = all channels
    val stockCount: Long? = null,
)

// ── Modifier Groups ───────────────────────────────────────────────────────────

@Serializable
data class ModifierOptionDto(
    val id: String,
    val name: String,
    val priceAdjustMinorUnit: Long = 0,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class ModifierGroupDto(
    val id: String,
    val name: String,
    val selectionType: String = "SINGLE",
    val required: Boolean = false,
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val options: List<ModifierOptionDto> = emptyList(),
)

@Serializable
data class CreateModifierGroupRequest(
    val id: String,
    val name: String,
    val selectionType: String = "SINGLE",
    val required: Boolean = false,
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val options: List<ModifierOptionDto> = emptyList(),
)

@Serializable
data class UpdateModifierGroupRequest(
    val name: String? = null,
    val selectionType: String? = null,
    val required: Boolean? = null,
    val minSelect: Int? = null,
    val maxSelect: Int? = null,
    val options: List<ModifierOptionDto>? = null,
)

// ── Menu Profiles (daypart / time-based scheduling) ───────────────────────────

@Serializable
data class MenuProfileDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val startTime: String? = null,
    val endTime: String? = null,
    val daysOfWeek: List<Int>? = null,  // null/empty = every day; 0=Sun…6=Sat
    val updatedAt: Long,
)

@Serializable
data class CreateMenuProfileRequest(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val startTime: String? = null,
    val endTime: String? = null,
    val daysOfWeek: List<Int>? = null,
)

@Serializable
data class UpdateMenuProfileRequest(
    val name: String? = null,
    val enabled: Boolean? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val daysOfWeek: List<Int>? = null,
)

@Serializable
data class SetProfileItemsRequest(
    val menuItemIds: List<String>,
)

@Serializable
data class BulkAvailabilityRequest(
    val ids: List<String>,
    val isSoldOut: Boolean,
)

// ── Orders ────────────────────────────────────────────────────────────────────

@Serializable
data class OrderSummaryDto(
    val id: String,
    val type: String,
    val tableId: String? = null,
    val sourceTerminalId: String = "",
    val guestCount: Int,
    val status: String,
    val subtotalMinorUnit: Long,
    val taxTotalMinorUnit: Long,
    val serviceChargeMinorUnit: Long,
    val tipMinorUnit: Long,
    val discountMinorUnit: Long,
    val orderNotes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val operatorId: String = "",
    val operatorName: String = "",
)

@Serializable
data class RefundRequest(
    val amountMinorUnit: Long,
)

@Serializable
data class OrderItemDto(
    val id: String,
    val orderId: String,
    val menuItemId: String,
    val menuItemNameSnapshot: String,
    val quantity: Int,
    val unitPriceMinorUnit: Long,
    val taxRateId: String? = null,
    val course: Int,
    val status: String,
    val notes: String,
    val categoryId: String? = null,
    val allergenSnapshot: String,
    val comboId: String? = null,
)

@Serializable
data class PaymentDto(
    val id: String,
    val orderId: String,
    val amountMinorUnit: Long,
    val method: String,
    val status: String,
    val operatorId: String,
    val refundedPaymentId: String? = null,
    val createdAt: Long,
)

@Serializable
data class OrderDetailDto(
    val order: OrderSummaryDto,
    val items: List<OrderItemDto>,
    val payments: List<PaymentDto>,
)

@Serializable
data class OrderListResponse(
    val orders: List<OrderSummaryDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

// ── Shift Report ──────────────────────────────────────────────────────────────

@Serializable
data class ShiftReportDto(
    val fromMs: Long,
    val toMs: Long,
    val orderCount: Int,
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val totalDiscountMinorUnit: Long,
    val totalTipMinorUnit: Long,
    val totalServiceChargeMinorUnit: Long,
    val totalTaxMinorUnit: Long,
    val totalGuestCount: Int,
    val totalRefundsMinorUnit: Long = 0L,
    val averageOrderValueMinorUnit: Long,
    val averageSpendPerGuestMinorUnit: Long,
    val paymentMethodBreakdown: Map<String, Long>,
)

@Serializable
data class TopItemDto(
    val menuItemId: String,
    val names: String,
    val quantity: Int,
    val revenueMinorUnit: Long,
)

// ── Extended Report DTOs ──────────────────────────────────────────────────────

@Serializable
data class CategorySalesDto(
    val categoryId: String,
    val categoryName: String,
    val quantity: Long,
    val revenueMinorUnit: Long,
    val orderCount: Int,
    val sharePermille: Int = 0,
)

@Serializable
data class ItemSalesDto(
    val menuItemId: String,
    val names: String,
    val categoryId: String,
    val categoryName: String,
    val quantity: Long,
    val orderCount: Int,
    val revenueMinorUnit: Long,
)

@Serializable
data class OrderTypeSalesDto(
    val type: String,
    val orderCount: Int,
    val revenueMinorUnit: Long,
    val guestCount: Int,
    val sharePermille: Int = 0,
)

@Serializable
data class StaffReportDto(
    val operatorId: String,
    val operatorName: String,
    val orderCount: Int,
    val revenueMinorUnit: Long,
    val avgOrderValueMinorUnit: Long,
    val tipMinorUnit: Long,
    val discountMinorUnit: Long,
    val refundMinorUnit: Long = 0L,
)

@Serializable
data class HourlySalesDto(
    val hour: Int,
    val orderCount: Int,
    val revenueMinorUnit: Long,
    val avgOrderValueMinorUnit: Long,
)

@Serializable
data class TaxReportSummaryDto(
    val taxableSalesMinorUnit: Long,
    val nonTaxableSalesMinorUnit: Long,
    val totalNetSalesMinorUnit: Long,
    val lines: List<TaxReportLineDto>,
)

@Serializable
data class TaxReportLineDto(
    val taxRateId: String,
    val taxRateName: String,
    val ratePermille: Int,
    val taxableSalesMinorUnit: Long,
    val taxAmountMinorUnit: Long,
)

@Serializable
data class ModifierSalesDto(
    val optionId: String,
    val optionName: String,         // JSON map: {"zh":"加蛋","en":"Add Egg"}
    val groupId: String,
    val groupName: String,          // JSON map
    val quantitySold: Long,
    val revenueMinorUnit: Long,
)

@Serializable
data class CashierShiftDto(
    val id: String,
    val shiftNumber: Int,
    val terminalId: String,
    val terminalName: String,
    val storeName: String,
    val openedByOperatorId: String,
    val openedByName: String,
    val closedByOperatorId: String?,
    val closedByName: String,
    val openedAt: Long,
    val closedAt: Long?,
    val openingCashMinorUnit: Long,
    val actualClosingCashMinorUnit: Long?,
    // Computed fields filled by detail endpoint
    val cashPaymentsMinorUnit: Long = 0L,
    val cashRefundsMinorUnit: Long = 0L,
    val moneyInMinorUnit: Long = 0L,
    val moneyOutMinorUnit: Long = 0L,
    val expectedCashMinorUnit: Long = 0L,
    val differencMinorUnit: Long = 0L,
    // Sales summary (detail only)
    val grossSalesMinorUnit: Long = 0L,
    val refundsMinorUnit: Long = 0L,
    val discountsMinorUnit: Long = 0L,
    val netSalesMinorUnit: Long = 0L,
    val taxMinorUnit: Long = 0L,
    val movements: List<CashMovementDto> = emptyList(),
)

@Serializable
data class CashMovementDto(
    val id: String,
    val shiftId: String,
    val type: String,
    val amountMinorUnit: Long,
    val description: String,
    val operatorId: String,
    val operatorName: String,
    val createdAt: Long,
)

@Serializable
data class CreateCashierShiftRequest(
    val id: String,
    val terminalId: String,
    val terminalName: String = "",
    val storeName: String = "",
    val openedByOperatorId: String,
    val openedByName: String = "",
    val openedAt: Long,
    val openingCashMinorUnit: Long = 0L,
)

@Serializable
data class CloseShiftRequest(
    val closedByOperatorId: String,
    val closedByName: String = "",
    val closedAt: Long,
    val actualClosingCashMinorUnit: Long,
)

@Serializable
data class CreateCashMovementRequest(
    val id: String,
    val type: String,
    val amountMinorUnit: Long,
    val description: String = "",
    val operatorId: String,
    val operatorName: String = "",
    val createdAt: Long,
)

@Serializable
data class PaymentMethodReportDto(
    val method: String,
    val paymentCount: Int,
    val paymentAmountMinorUnit: Long,
    val refundCount: Int,
    val refundAmountMinorUnit: Long,
    val netAmountMinorUnit: Long,
)

// ── Users ─────────────────────────────────────────────────────────────────────

@Serializable
data class PosUserDto(
    val id: String,
    val displayName: String,
    val role: String,
    val roleDisplayName: String = role,
    val isActive: Boolean,
    val createdAt: Long,
    val hourlyWageMinorUnit: Long = 0,
)

@Serializable
data class CreatePosUserRequest(
    val id: String,
    val displayName: String,
    val role: String,
    val pin: String,
    val hourlyWageMinorUnit: Long = 0,
)

@Serializable
data class UpdatePosUserRequest(
    val displayName: String? = null,
    val role: String? = null,
    val pin: String? = null,
    val isActive: Boolean? = null,
    val hourlyWageMinorUnit: Long? = null,
)

// ── Shift Schedules (排班) ──────────────────────────────────────────────────

@Serializable
data class ShiftScheduleDto(
    val id: String,
    val operatorId: String,
    val operatorName: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val notes: String = "",
)

@Serializable
data class CreateShiftScheduleRequest(
    val operatorId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val notes: String = "",
)

@Serializable
data class UpdateShiftScheduleRequest(
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String? = null,
)

@Serializable
data class LaborCostSummaryDto(
    val operatorId: String,
    val operatorName: String,
    val hourlyWageMinorUnit: Long,
    val scheduledMinutes: Int,
    val actualMinutes: Int,
    val scheduledCostMinorUnit: Long,
    val actualCostMinorUnit: Long,
)

@Serializable
data class LaborCostReportResponse(
    val summaries: List<LaborCostSummaryDto>,
    val totalScheduledCostMinorUnit: Long,
    val totalActualCostMinorUnit: Long,
)

// ── Settings ──────────────────────────────────────────────────────────────────

@Serializable
data class SettingDto(
    val key: String,
    val value: String,
)

// ── Floor plan: sections & tables ─────────────────────────────────────────────

@Serializable
data class SectionDto(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

@Serializable
data class CreateSectionRequest(
    val name: String,
    val sortOrder: Int = 0,
)

@Serializable
data class UpdateSectionRequest(
    val name: String? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class TableConfigDto(
    val id: String,
    val name: String,
    val sectionId: String,
    val capacity: Int,
    val shape: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

@Serializable
data class CreateTableConfigRequest(
    val name: String,
    val sectionId: String,
    val capacity: Int = 4,
    val shape: String = "square",
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
)

@Serializable
data class UpdateTableConfigRequest(
    val name: String? = null,
    val sectionId: String? = null,
    val capacity: Int? = null,
    val shape: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val w: Int? = null,
    val h: Int? = null,
)

// ── Coupons ───────────────────────────────────────────────────────────────────

@Serializable
data class CouponDto(
    val id: String,
    val code: String,
    val type: String,
    val value: Long,
    val expiresAt: Long,
    val isActive: Boolean,
)

@Serializable
data class CreateCouponRequest(
    val id: String,
    val code: String,
    val type: String,
    val value: Long,
    val expiresAt: Long,
)

@Serializable
data class UpdateCouponRequest(
    val code: String? = null,
    val type: String? = null,
    val value: Long? = null,
    val expiresAt: Long? = null,
    val isActive: Boolean? = null,
)

// ── Combos ────────────────────────────────────────────────────────────────────

@Serializable
data class ComboComponentDto(
    val id: String,
    val comboId: String,
    val menuItemId: String,
    val quantity: Int,
    val sortOrder: Int,
)

@Serializable
data class ComboDto(
    val id: String,
    val names: String,
    val comboPriceMinorUnit: Long,
    val taxRateId: String? = null,
    val isActive: Boolean,
    val components: List<ComboComponentDto> = emptyList(),
)

@Serializable
data class ComboComponentRequest(
    val id: String,
    val menuItemId: String,
    val quantity: Int,
    val sortOrder: Int = 0,
)

@Serializable
data class CreateComboRequest(
    val id: String,
    val names: String,
    val comboPriceMinorUnit: Long,
    val taxRateId: String? = null,
    val components: List<ComboComponentRequest> = emptyList(),
)

@Serializable
data class UpdateComboRequest(
    val names: String? = null,
    val comboPriceMinorUnit: Long? = null,
    val taxRateId: String? = null,
    val isActive: Boolean? = null,
    val components: List<ComboComponentRequest>? = null,
)

// ── CRM ───────────────────────────────────────────────────────────────────────

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val totalSpendMinorUnit: Long = 0L,
    val loyaltyPoints: Long = 0L,
    val membershipTierId: String? = null,
    val totalVisits: Int = 0,
    val lastVisitAt: Long = 0L,
    val registeredAt: Long,
)

@Serializable
data class CreateCustomerRequest(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class UpdateCustomerRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val tags: List<String>? = null,
    val notes: String? = null,
    val membershipTierId: String? = null,
)

@Serializable
data class LoyaltyTransactionDto(
    val id: String,
    val customerId: String,
    val orderId: String? = null,
    val type: String,
    val points: Long,
    val description: String,
    val createdAt: Long,
)

@Serializable
data class AdjustPointsRequest(
    val delta: Long,
    val description: String,
)

@Serializable
data class MembershipTierDto(
    val id: String,
    val name: String,
    val minPoints: Long,
    val benefits: List<String> = emptyList(),
    val color: String = "gray",
    val discountPermille: Long = 0L,
    val pointsMultiplier: Long = 100L,
    val sortOrder: Int = 0,
)

@Serializable
data class CreateMembershipTierRequest(
    val id: String,
    val name: String,
    val minPoints: Long = 0L,
    val benefits: List<String> = emptyList(),
    val color: String = "gray",
    val discountPermille: Long = 0L,
    val pointsMultiplier: Long = 100L,
    val sortOrder: Int = 0,
)

@Serializable
data class UpdateMembershipTierRequest(
    val name: String? = null,
    val minPoints: Long? = null,
    val benefits: List<String>? = null,
    val color: String? = null,
    val discountPermille: Long? = null,
    val pointsMultiplier: Long? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class CampaignDto(
    val id: String,
    val name: String,
    val type: String,
    val targetSegment: String,
    val targetTierId: String? = null,
    val couponId: String? = null,
    val message: String,
    val scheduledAt: Long? = null,
    val status: String,
    val sentCount: Int,
    val createdAt: Long,
)

@Serializable
data class CreateCampaignRequest(
    val id: String,
    val name: String,
    val type: String,
    val targetSegment: String = "ALL",
    val targetTierId: String? = null,
    val couponId: String? = null,
    val message: String,
    val scheduledAt: Long? = null,
)

@Serializable
data class UpdateCampaignRequest(
    val name: String? = null,
    val targetSegment: String? = null,
    val targetTierId: String? = null,
    val couponId: String? = null,
    val message: String? = null,
    val scheduledAt: Long? = null,
    val status: String? = null,
)

// ── Daily Snapshots (Trend Reports) ──────────────────────────────

@Serializable
data class TrendDataPointDto(
    val date: String,                    // "YYYY-MM-DD"
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val orderCount: Int,
    val guestCount: Int,
    val averageOrderValueMinorUnit: Long,
    val averageSpendPerGuestMinorUnit: Long,
    val refundMinorUnit: Long = 0L,
    val discountMinorUnit: Long = 0L,
    val taxMinorUnit: Long = 0L,
)

@Serializable
data class TrendSummaryDto(
    val totalGrossRevenue: Long,
    val totalNetRevenue: Long,
    val totalOrderCount: Int,
    val totalGuestCount: Int,
    val avgOrderValue: Long,
    val avgSpendPerGuest: Long,
    val growthFromPrevious: Double?,   // null = no previous data; e.g. 0.12 = +12%
)

@Serializable
data class TrendReportDto(
    val dataPoints: List<TrendDataPointDto>,
    val summary: TrendSummaryDto,
)

@Serializable
data class DailySnapshotDto(
    val date: String,
    val grossRevenueMinorUnit: Long,
    val netRevenueMinorUnit: Long,
    val orderCount: Int,
    val guestCount: Int,
    val totalDiscountMinorUnit: Long,
    val totalTipMinorUnit: Long,
    val totalServiceChargeMinorUnit: Long,
    val totalTaxMinorUnit: Long,
    val paymentMethodBreakdown: Map<String, Long>,
    val computedAt: Long,
)

@Serializable
data class SnapshotRegenerateRequest(
    val fromDate: String,   // "YYYY-MM-DD"
    val toDate: String,     // "YYYY-MM-DD"
)

@Serializable
data class PeakHourCellDto(
    val dayOfWeek: Int,     // 1=Mon … 7=Sun (ISO-8601)
    val hour: Int,          // 0-23
    val orderCount: Int,
)

@Serializable
data class PeakReportDto(
    val cells: List<PeakHourCellDto>,
    val maxOrderCount: Int,
    val totalOrders: Int,
)

// ── Employee Timecards ─────────────────────────────────────────────────────

@Serializable
data class TimecardDto(
    val id: String,
    val operatorId: String,
    val operatorName: String,
    val terminalId: String,
    val clockInAt: Long,
    val clockOutAt: Long?,
    val durationMinutes: Int?,   // null when open
    val clockInNote: String,
    val clockOutNote: String,
    val createdAt: Long,
)

@Serializable
data class CreateTimecardRequest(
    val operatorId: String,
    val operatorName: String = "",
    val clockInAt: Long,
    val clockOutAt: Long? = null,
    val clockInNote: String = "",
    val clockOutNote: String = "",
)

@Serializable
data class UpdateTimecardRequest(
    val clockInAt: Long? = null,
    val clockOutAt: Long? = null,
    val clockInNote: String? = null,
    val clockOutNote: String? = null,
)

@Serializable
data class ClockInRequest(
    val operatorId: String,
    val operatorName: String = "",
    val terminalId: String = "",
    val note: String = "",
)

@Serializable
data class ClockOutRequest(
    val note: String = "",
)

@Serializable
data class TimecardSummaryDto(
    val operatorId: String,
    val operatorName: String,
    val totalMinutes: Int,
    val shiftsCount: Int,
)

@Serializable
data class TimecardListResponse(
    val timecards: List<TimecardDto>,
    val total: Int,
)

@Serializable
data class HoursReportResponse(
    val summaries: List<TimecardSummaryDto>,
    val totalMinutes: Int,
)

// ── Gift Cards (礼品卡/储值卡) ────────────────────────────────────────────────

@Serializable
data class GiftCardDto(
    val id: String,
    val code: String,
    val balanceMinorUnit: Long,
    val customerId: String? = null,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CreateGiftCardRequest(
    val code: String,
    val initialBalanceMinorUnit: Long = 0,
    val customerId: String? = null,
)

@Serializable
data class GiftCardTopUpRequest(
    val amountMinorUnit: Long,
    val note: String = "",
)

@Serializable
data class GiftCardRedeemRequest(
    val code: String,
    val amountMinorUnit: Long,
    val orderId: String? = null,
    val operatorId: String = "",
    val note: String = "",
)

@Serializable
data class GiftCardTransactionDto(
    val id: String,
    val giftCardId: String,
    val type: String,
    val amountMinorUnit: Long,
    val orderId: String? = null,
    val operatorId: String,
    val note: String,
    val createdAt: Long,
)

// ── Douyin / Meituan group-buying voucher redemption ────────────────────────

@Serializable
data class GroupBuyingVoucherValidateRequest(val provider: String, val code: String)

@Serializable
data class GroupBuyingVoucherDto(
    val provider: String,
    val maskedCode: String,
    val title: String,
    val faceValueMinorUnit: Long,
    val expiresAt: Long,
    val status: String,
    val demo: Boolean,
)

@Serializable
data class GroupBuyingVoucherRedeemRequest(
    val provider: String,
    val code: String,
    val orderId: String,
    val operatorId: String = "",
    val requestedAmountMinorUnit: Long,
    val idempotencyKey: String,
)

@Serializable
data class GroupBuyingVoucherRedeemResponse(
    val redemptionId: String,
    val redeemedAmountMinorUnit: Long,
    val alreadyRedeemed: Boolean = false,
)

@Serializable
data class GroupBuyingRedemptionDto(
    val id: String,
    val provider: String,
    val maskedCode: String,
    val title: String,
    val orderId: String,
    val operatorId: String,
    val redeemedAmountMinorUnit: Long,
    val providerReference: String,
    val status: String,
    val demo: Boolean,
    val createdAt: Long,
)

@Serializable
data class GroupBuyingVoucherErrorResponse(val code: String, val message: String)
