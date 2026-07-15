package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

/**
 * KDS（Kitchen Display System）配置项。
 *
 * 对标 Toast KDS 设置：
 *   https://doc.toasttab.com/doc/platformguide/adminKdsConfigQuickRef.html
 *
 * 由 Web 管理后台写入 settings 表 (key = "kds_config")，
 * Android :app:kds 端启动时从 ConfigRepository 读取。
 */
@Serializable
data class KdsConfig(
    // ── 工站列表 ──────────────────────────────────────────────
    // 决定 KDS 左侧/顶部工站标签页顺序，对应 Toast 的"备餐站(Prep Stations)"。
    val stations: List<String> = listOf("kitchen"),

    // ── 时效阈值（对标 Toast：订单超时变色）────────────────────
    /** 出餐后超过此秒数变为"紧急"状态（UI 变黄）*/
    val urgentAfterSeconds: Long = 300L,
    /** 出餐后超过此秒数变为"超时"状态（UI 变红）*/
    val criticalAfterSeconds: Long = 600L,

    // ── 完成模式（对标 Toast：Fulfill Items 开关）─────────────
    // Toast: 关闭 = 整单完成；开启 = 单品完成
    val fulfillMode: String = FULFILL_MODE_FULL_ORDER,

    // ── All Day 汇总视图（对标 Toast：All Day Display）──────────
    // Toast 支持两种：按商品汇总、按商品+配料汇总
    val showAllDayView: Boolean = false,
    val allDayGroupByModifiers: Boolean = false,

    // ── 其他工站进度（对标 Toast：Other Stations）─────────────
    val showOtherStationsProgress: Boolean = false,

    // ── 订单头显示（对标 Toast：KDS Ticket Headers）───────────
    // Toast 可选：Check number / Table number
    val ticketHeaderMode: String = TICKET_HEADER_TABLE,

    // ── 商品显示（对标 Toast：Consolidate menu items）──────────
    // true = 相同商品合并为一行显示数量；false = 每个商品单独一行
    val consolidateItems: Boolean = true,

    // ── 配料显示模式（对标 Toast：Modifier display mode）───────
    // Toast 可选：Vertical / Horizontal
    val modifierDisplayMode: String = MODIFIER_DISPLAY_VERTICAL,

    // ── 商品颜色编码（对标 Toast：KDS Color）─────────────────
    // true = 在 KDS 上显示商品/配料的 KDS Color（颜色在菜单管理里配置）
    val itemColorCodingEnabled: Boolean = true,

    // ── 出餐核对打印机（对标 Toast：Expediter Printer）────────
    // 配置后，KDS 完成备餐时自动在此打印机出票（用于出餐核对）
    val expediterPrinterId: String? = null,

    // ── 打印模式（对标 Toast：Print On Demand / Auto-print）────
    val printMode: String = PRINT_MODE_AUTO,
    /** KDS 完成备餐时是否自动打印出票（对标 Toast：Auto-print Fulfilled Tickets）*/
    val autoPrintFulfilled: Boolean = false,

    // ── 声音提醒（对标 Toast：New Ticket Sound）────────────────
    val soundAlertEnabled: Boolean = true,

    // ── 菜单分类 → 工站路由 ───────────────────────────────────
    val categoryToStation: Map<String, String> = emptyMap(),
    /** 订单类型 → 工站路由（对标 Square 订单类型路由）*/
    val orderTypeToStation: Map<String, String> = emptyMap(),
    val defaultStationId: String = "kitchen",
) {
    companion object {
        // fulfillMode
        const val FULFILL_MODE_FULL_ORDER = "FULL_ORDER"
        const val FULFILL_MODE_INDIVIDUAL_ITEMS = "INDIVIDUAL_ITEMS"

        // ticketHeaderMode
        const val TICKET_HEADER_CHECK = "CHECK_NUMBER"
        const val TICKET_HEADER_TABLE = "TABLE_NUMBER"
        const val TICKET_HEADER_CUSTOMER = "CUSTOMER_NAME"
        const val TICKET_HEADER_PICKUP_NUMBER = "PICKUP_NUMBER"

        // modifierDisplayMode
        const val MODIFIER_DISPLAY_VERTICAL = "VERTICAL"
        const val MODIFIER_DISPLAY_HORIZONTAL = "HORIZONTAL"

        // printMode
        const val PRINT_MODE_AUTO = "AUTO"
        const val PRINT_MODE_ON_DEMAND = "ON_DEMAND"
    }
}

val DefaultKdsConfig = KdsConfig()
