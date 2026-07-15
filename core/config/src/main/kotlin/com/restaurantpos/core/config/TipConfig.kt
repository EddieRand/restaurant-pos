package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

/**
 * Tip / gratuity configuration — benchmarked against Square POS (configurable presets,
 * pre-tax vs post-tax toggle) and Toast (per-channel enable flags).
 *
 * Stored as a nested object inside RegionConfig (settings table key "regionConfig").
 * No schema migration needed; defaults cover existing behaviour.
 */
@Serializable
data class TipConfig(
    /** Master switch — false means tip UI is hidden on all channels. */
    val enabled: Boolean = true,

    /**
     * Percentage presets shown to staff/customers.
     * Empty list → only "No Tip" + custom input are shown.
     * Square default: [15, 20, 25]; Toast default: [15, 18, 20].
     */
    val presets: List<Int> = listOf(15, 18, 20, 22),

    /**
     * Calculation base for percentage tips.
     * SUBTOTAL = applied to order subtotal before tax (Square default).
     * POST_TAX  = applied to subtotal + tax.
     */
    val calcBase: TipCalcBase = TipCalcBase.SUBTOTAL,

    /** Show tip step in the customer QR-ordering web flow. */
    val enabledOnQr: Boolean = false,

    /** Show tip step in the self-service Kiosk flow. */
    val enabledOnKiosk: Boolean = false,

    /** Show tip step on the handheld PAD ordering app. */
    val enabledOnPad: Boolean = false,
)

@Serializable
enum class TipCalcBase {
    /** Tip % applied to subtotal (before tax). */
    SUBTOTAL,
    /** Tip % applied to subtotal + tax total. */
    POST_TAX,
}
