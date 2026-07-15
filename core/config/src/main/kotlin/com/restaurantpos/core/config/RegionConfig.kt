package com.restaurantpos.core.config

/**
 * Merchant-level region configuration. All currency/locale/tax behaviour is
 * driven from here — no country constants anywhere in :core or :feature.
 */
data class RegionConfig(
    val currencyCode: String,          // ISO 4217, e.g. "USD", "EUR", "JPY"
    val currencySymbol: String,        // e.g. "$", "€", "¥"
    val currencyMinorDigits: Int,      // decimal places, e.g. 2 for USD, 0 for JPY
    val locale: String,                // BCP-47 tag, e.g. "en-US", "zh-CN"
    val defaultTaxMode: TaxMode,
    val availableTaxRates: List<TaxRate>,
    val rounding: RoundingMode = RoundingMode.HALF_UP,
    val thousandsSeparator: Char = ',',
    val decimalSeparator: Char = '.',
    val terminalId: String = "pos-1",  // device/terminal identifier for order source tracking
    val timeZone: String = "UTC",       // IANA tz, e.g. "Asia/Shanghai", "America/New_York"
    val receiptConfig: ReceiptConfig = ReceiptConfig(),
    /** Per-ticket-type templates + printer station routing */
    val printConfig: PrintConfig = PrintConfig(),
    /** KDS 厨显系统配置 */
    val kdsConfig: KdsConfig = KdsConfig(),
    /** 桌边 PAD 点餐配置 */
    val padConfig: PadConfig = PadConfig(),
    /** Kiosk 自助点餐配置 */
    val kioskConfig: KioskConfig = KioskConfig(),
    /** 小费配置（对标 Square/Toast 可配置预设%、税前/税后、各端开关）*/
    val tipConfig: TipConfig = TipConfig(),
    /** 员工打卡配置 */
    val timeclockConfig: TimeclockConfig = TimeclockConfig(),
    /** 服务费千分比，0 = 禁用。Web Admin 可配置。e.g. 50 = 5% */
    val serviceChargeRatePermille: Int = 0,
) {
    fun taxRateById(id: String): TaxRate? = availableTaxRates.find { it.id == id }
}

/** Neutral default — USD, 2 decimal places, exclusive tax at 0%. */
val DefaultRegionConfig = RegionConfig(
    currencyCode = "USD",
    currencySymbol = "$",
    currencyMinorDigits = 2,
    locale = "en-US",
    defaultTaxMode = TaxMode.EXCLUSIVE,
    availableTaxRates = listOf(
        TaxRate(id = "none", name = "No Tax", ratePermille = 0L),
    ),
    serviceChargeRatePermille = 50, // 5% default for demo
)
