package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

/**
 * Merchant-configurable receipt & kitchen-print template settings.
 * Stored as part of RegionConfig and persisted via ConfigRepository.
 *
 * `paperWidthMm` / `printerType` are used by the SUNMI / generic ESC/POS
 * driver to pick the correct column width and command set.
 */
@Serializable
data class ReceiptConfig(
    /** Lines printed above the item list (restaurant name, address, phone…). */
    val headerLines: List<String> = emptyList(),
    /** Lines printed below the total (thank-you message, Wi-Fi, website…). */
    val footerLines: List<String> = emptyList(),
    /** When true, print the tax registration number on the receipt. */
    val showTaxId: Boolean = false,
    /** Tax registration / VAT number text. */
    val taxId: String = "",

    // ---- 打印机硬件配置（后厨打印 & 前台小票） ----

    /** 打印纸宽度：58 或 80 (mm) */
    val paperWidthMm: Int = 58,
    /** 字体缩放倍率，0.5–2.0，默认 1.0 */
    val fontSizeScale: Float = 1.0f,
    /** 是否打印商家 Logo（需先上传位图）*/
    val printLogo: Boolean = false,
    /** 打印完成后是否弹开钱箱 */
    val openCashDrawer: Boolean = false,
    /** 打印浓度 0–100（ESC/POS 指令 `ESC " =" 的参数映射）*/
    val printDensity: Int = 80,
    /** 打印完成后走纸行数 */
    val feedLinesAfterPrint: Int = 3,
    /** 厨房票据是否每张单独出纸（false = 合并到同一张）*/
    val kitchenTicketPerOrder: Boolean = true,
)
