package com.restaurantpos.core.hardware

data class ReceiptData(
    val orderId: String,
    val lines: List<ReceiptLine>,
    val totalMinorUnit: Long,
    val currencySymbol: String,
    val currencyMinorDigits: Int,
    val headerLines: List<String> = emptyList(),
    val footerLines: List<String> = emptyList(),
    val taxId: String? = null,
)

data class KitchenTicketData(
    val orderId: String,
    val tableId: String?,
    val items: List<KitchenItemLine>,
    val course: Int,
    val stationId: String,
)

data class ReceiptLine(val label: String, val amountMinorUnit: Long)
data class KitchenItemLine(val name: String, val quantity: Int, val notes: String)
