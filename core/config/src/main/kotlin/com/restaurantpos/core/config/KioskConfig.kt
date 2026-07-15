package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

/**
 * Kiosk（自助点餐端）配置项。
 *
 * 对标 Toast Kiosk / Square Kiosk 设置。
 * 由 Web 管理后台写入 settings 表 (key = "kiosk_config")，
 * Android :app:kiosk 端启动时从 ConfigRepository 读取。
 */
@Serializable
data class KioskConfig(
    // ── 超时 ──────────────────────────────────────────────
    /** 支付/确认页无操作自动返回首页的超时秒数（对标竞品 Auto-return）*/
    val autoReturnSeconds: Int = 30,

    // ── QR 码 ──────────────────────────────────────────────
    /** QR 码错误纠正级别：L(7%) / M(15%) / Q(25%) / H(30%) */
    val qrErrorCorrectionLevel: String = "M",
    /** 确认页 QR 码尺寸（dp）*/
    val confirmationQrSizeDp: Int = 200,

    // ── 页面文案（对标 Toast：欢迎页/完成页可自定义）────────
    /** 欢迎页标题（showWelcomeScreen = true 时显示）*/
    val welcomeTitle: String = "",
    /** 欢迎页副标题 */
    val welcomeSubtitle: String = "",
    /** 完成页标题 */
    val completionTitle: String = "",
    /** 完成页副标题 */
    val completionSubtitle: String = "",

    // ── 支付方式（对标 Square Kiosk：可选择显示的支付方式）───
    // 可选值：CASH, CARD, QR_PAY, MEMBERSHIP
    val enabledPaymentMethods: List<String> = listOf("CARD", "QR_PAY"),

    // ── 界面开关 ────────────────────────────────────────────
    /** 是否显示欢迎页（false = 直接进入菜单）*/
    val showWelcomeScreen: Boolean = true,
    /** 确认页是否显示"发送小票到手机"选项 */
    val showDigitalReceiptOption: Boolean = false,
    /** 是否显示促销横幅（对标 Toast Kiosk 促销横幅）*/
    val showPromotionBanner: Boolean = false,
    /** 促销横幅图片 URL（null = 不显示）*/
    val promotionBannerUrl: String? = null,
    /** 是否启用无障碍大字体模式（对标竞品 Accessibility）*/
    val accessibilityEnabled: Boolean = false,
)
