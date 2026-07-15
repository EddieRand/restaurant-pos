package com.restaurantpos.core.sync

/**
 * Currency & number-format + tax/tip/service-charge fields the merchant configures once
 * in Web Admin and that must render identically on every terminal (cashier / handheld /
 * kds / kiosk / pad / pickup-display) and the web surfaces.
 */
data class RegionFormat(
    val currencyCode: String? = null,
    val currencySymbol: String? = null,
    val currencyMinorDigits: Int? = null,
    val thousandsSeparator: Char? = null,
    val decimalSeparator: Char? = null,
    // Extended fields from admin regionConfig blob
    val availableTaxRatesJson: String? = null,
    val tipPresetsJson: String? = null,
    val serviceChargeRatePermille: Int? = null,
    val locale: String? = null,
    val timeZone: String? = null,
)

/**
 * Abstraction over the remote API for pulling the merchant's currency/number-format
 * settings (the server-authoritative subset of `RegionConfig`).
 *
 * Real implementation: HTTP GET /admin/settings/regionConfig with JWT auth.
 * Test implementation: a fake in test sources.
 */
interface RegionFormatPullPort {
    /** Returns the merchant's currency/format settings, or null if unset / unreachable. */
    suspend fun pullFormat(): RegionFormat?
}
