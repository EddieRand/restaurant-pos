package com.restaurantpos.core.model

/**
 * All printable ticket/receipt types in the system.
 * Each type has its own template config and printer routing.
 */
enum class TicketType(
    val defaultEnabled: Boolean,
    val defaultCopies: Int,
    val showPrices: Boolean,
) {
    /** 顾客小票 — printed at checkout, handed to customer */
    CUSTOMER_RECEIPT(defaultEnabled = true, defaultCopies = 1, showPrices = true),

    /** 厨房出票 — sent to kitchen station when order is fired */
    KITCHEN_TICKET(defaultEnabled = true, defaultCopies = 1, showPrices = false),

    /** 吧台/饮品票 — drinks routed to bar station */
    BAR_TICKET(defaultEnabled = false, defaultCopies = 1, showPrices = false),

    /** 外带标签 — compact label affixed to takeaway bags */
    TAKEAWAY_LABEL(defaultEnabled = false, defaultCopies = 1, showPrices = false),

    /** 预结单 — preview bill presented to customer before payment */
    PRE_BILL(defaultEnabled = false, defaultCopies = 1, showPrices = true),

    /** 退款凭证 — printed when a refund is processed */
    REFUND_RECEIPT(defaultEnabled = true, defaultCopies = 1, showPrices = true),

    /** 作废凭证 — printed when an order is voided */
    VOID_RECEIPT(defaultEnabled = false, defaultCopies = 1, showPrices = false),

    /** 交班报表 — printed at shift handover */
    SHIFT_REPORT(defaultEnabled = true, defaultCopies = 1, showPrices = true),

    /** 日结报表 — printed at end of business day */
    DAILY_REPORT(defaultEnabled = true, defaultCopies = 1, showPrices = true),
}
