package com.restaurantpos.server.model

import kotlinx.serialization.Serializable

@Serializable
data class QrOrderingConfigDto(
    val enabled: Boolean = true,
    val supportedOrderTypes: List<String> = listOf("DINE_IN", "TAKEAWAY", "DELIVERY"),
    val menuOnlyMode: Boolean = false,
    val paymentTiming: String = "PAY_AT_END",
    val firePolicy: String = "BY_ORDER_TYPE",
    val customerIdentityPolicy: String = "BY_ORDER_TYPE",
    val appearance: QrAppearanceDto = QrAppearanceDto(),
)

@Serializable
data class QrAppearanceDto(
    val themeColor: String = "#FF5C00",
    val bannerImageUrl: String = "",
    val menuCardStyle: String = "grid",
)

@Serializable
data class QrCodeDto(
    val code: String,
    val scope: String,
    val tableId: String? = null,
    val enabled: Boolean,
    val expiresAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TableQrBindingResponse(
    val sections: List<TableQrSectionDto>,
    val unassignedCodes: List<QrCodeDto> = emptyList(),
)

@Serializable
data class TableQrSectionDto(
    val id: String,
    val name: String,
    val tables: List<TableQrBindingDto>,
)

@Serializable
data class TableQrBindingDto(
    val tableId: String,
    val tableName: String,
    val sectionId: String,
    val sectionName: String,
    val capacity: Int,
    val status: String,
    val currentQr: QrCodeDto? = null,
    val activeCodeCount: Int = 0,
    val disabledCodeCount: Int = 0,
    val customerUrl: String? = null,
)

@Serializable
data class CreateQrCodeRequest(
    val code: String,
    val scope: String,
    val tableId: String? = null,
    val enabled: Boolean = true,
    val expiresAt: Long? = null,
)

@Serializable
data class UpdateQrCodeRequest(
    val scope: String? = null,
    val tableId: String? = null,
    val enabled: Boolean? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class RebindQrCodeRequest(
    val tableId: String,
)

@Serializable
data class PublicQrContextDto(
    val code: String,
    val scope: String,
    val tableId: String? = null,
    val orderTypes: List<String>,
    val menuOnly: Boolean,
    val paymentTiming: String,
    val firePolicy: String,
    val tipEnabled: Boolean = false,
    val tipPresets: List<Int> = emptyList(),
    val tipCalcBase: String = "SUBTOTAL",
)

@Serializable
data class PublicMenuResponse(
    val items: List<MenuItemDto>,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val currencyMinorDigits: Int = 2,
)

@Serializable
data class CustomerInfoDto(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

@Serializable
data class CreateQrSessionRequest(
    val code: String,
    val orderType: String? = null,
    val customerInfo: CustomerInfoDto = CustomerInfoDto(),
)

@Serializable
data class QrSessionDto(
    val id: String,
    val code: String,
    val orderType: String,
    val tableId: String? = null,
    val status: String,
    val customerInfo: CustomerInfoDto,
    val cart: QrCartDto,
    val orderId: String? = null,
    val sessionToken: String? = null,
)

@Serializable
data class QrCartDto(
    val items: List<QrCartItemDto> = emptyList(),
)

@Serializable
data class QrCartItemDto(
    val menuItemId: String,
    val quantity: Int,
    val notes: String = "",
)

@Serializable
data class UpdateQrCartRequest(
    val items: List<QrCartItemDto>,
)

@Serializable
data class SubmitQrOrderRequest(
    /** Optional tip chosen by customer. Only applied when tipConfig.enabledOnQr = true on the server. */
    val tipMinorUnit: Long = 0L,
)

@Serializable
data class SubmitQrOrderResponse(
    val orderId: String,
    val status: String,
    val totalMinorUnit: Long,
    val requiresPayment: Boolean,
    val requiresStaffConfirmation: Boolean,
)

@Serializable
data class CreateMockPaymentIntentRequest(
    val sessionId: String? = null,
    val orderId: String? = null,
)

@Serializable
data class MockPaymentIntentDto(
    val id: String,
    val sessionId: String? = null,
    val orderId: String? = null,
    val amountMinorUnit: Long,
    val method: String,
    val status: String,
    val provider: String,
)
