package com.restaurantpos.core.model

/**
 * A guest-initiated waiter call from a tableside PAD.
 * Appears as an alert on the cashier/handheld app until acknowledged.
 */
data class WaiterCall(
    val id: String,
    val tableId: String,
    val tableDisplayName: String,
    val reason: WaiterCallReason = WaiterCallReason.GENERAL,
    val createdAt: Long,
    val acknowledgedAt: Long? = null,
)

enum class WaiterCallReason {
    GENERAL,        // 呼叫服务
    REQUEST_BILL,   // 请求结账
    NEED_WATER,     // 需要加水/汤底
    NEED_UTENSILS,  // 需要餐具
}
