package com.restaurantpos.core.config

import com.restaurantpos.core.model.TicketType
import kotlinx.serialization.Serializable

/**
 * Per-ticket-type configuration: what to display and where to print.
 */
@Serializable
data class TicketTemplateConfig(
    val ticketType: TicketType,
    /** Whether this ticket type is printed at all */
    val enabled: Boolean = ticketType.defaultEnabled,
    /** Number of copies to print */
    val copies: Int = ticketType.defaultCopies,
    /** Station ID to route this ticket to. Empty = default/main printer. */
    val printerStationId: String = "",
    /**
     * For KITCHEN_TICKET and BAR_TICKET: category ID → station ID routing.
     * Items whose category is not in this map fall back to [printerStationId].
     */
    val categoryRoutes: Map<String, String> = emptyMap(),

    // ── Content toggles ──────────────────────────────────────────────────
    val showMerchantName: Boolean = true,
    val showMerchantAddress: Boolean = true,
    val showMerchantPhone: Boolean = false,
    val showOrderId: Boolean = true,
    val showOrderType: Boolean = true,
    val showTableNumber: Boolean = true,
    val showServerName: Boolean = false,
    val showTimestamp: Boolean = true,
    val showItemPrices: Boolean = ticketType.showPrices,
    val showSubtotal: Boolean = ticketType.showPrices,
    val showTax: Boolean = ticketType.showPrices,
    val showDiscount: Boolean = ticketType.showPrices,
    val showServiceCharge: Boolean = ticketType.showPrices,
    val showTotal: Boolean = ticketType.showPrices,
    val showPaymentMethod: Boolean = false,
    val showChangeAmount: Boolean = false,
    val showCustomerName: Boolean = false,
    val showNotes: Boolean = true,
    val showTaxId: Boolean = false,
    val taxId: String = "",
    /** Show a QR code at bottom; content is a template string, e.g. "{{order_id}}" */
    val showQrCode: Boolean = false,
    val qrCodeTemplate: String = "",

    // ── Text customisation ───────────────────────────────────────────────
    val headerLines: List<String> = emptyList(),
    val footerLines: List<String> = emptyList(),
    /** Custom title line override, e.g. "KITCHEN" / "BAR" / "RECEIPT" */
    val titleOverride: String = "",
)

/** Builds a sensible default template map covering all ticket types. */
fun defaultTicketTemplates(): Map<TicketType, TicketTemplateConfig> =
    TicketType.entries.associateWith { type ->
        when (type) {
            TicketType.KITCHEN_TICKET -> TicketTemplateConfig(
                ticketType = type,
                printerStationId = "kitchen",
                showMerchantAddress = false,
                showMerchantPhone = false,
                showTotal = false,
                showTax = false,
                showNotes = true,
            )
            TicketType.BAR_TICKET -> TicketTemplateConfig(
                ticketType = type,
                printerStationId = "kitchen",
                showMerchantAddress = false,
                showMerchantPhone = false,
                showTotal = false,
                showTax = false,
            )
            TicketType.TAKEAWAY_LABEL -> TicketTemplateConfig(
                ticketType = type,
                showMerchantAddress = false,
                showMerchantPhone = false,
                showItemPrices = false,
                showSubtotal = false,
                showTax = false,
                showTotal = false,
                showOrderType = true,
                showCustomerName = true,
            )
            TicketType.PRE_BILL -> TicketTemplateConfig(
                ticketType = type,
                titleOverride = "PRE-BILL",
                showPaymentMethod = false,
            )
            TicketType.REFUND_RECEIPT -> TicketTemplateConfig(
                ticketType = type,
                titleOverride = "REFUND",
                showPaymentMethod = true,
            )
            TicketType.VOID_RECEIPT -> TicketTemplateConfig(
                ticketType = type,
                titleOverride = "VOID",
                showItemPrices = false,
                showTotal = false,
            )
            TicketType.SHIFT_REPORT -> TicketTemplateConfig(
                ticketType = type,
                showTableNumber = false,
                showServerName = true,
            )
            TicketType.DAILY_REPORT -> TicketTemplateConfig(
                ticketType = type,
                showTableNumber = false,
                showServerName = false,
            )
            else -> TicketTemplateConfig(ticketType = type)
        }
    }
