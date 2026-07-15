package com.restaurantpos.core.config

import kotlinx.serialization.Serializable

/**
 * Tableside PAD configuration.
 *
 * Two modes (Ziosk + Jamezz reference):
 *  - Standard: guests order freely at any time (normal à la carte)
 *  - AYCE: round-based management with timers and item-per-round limits
 *
 * Fixed table binding: each PAD device is pre-configured to a specific table ID
 * in the merchant backend, so it boots directly into service for that table.
 */
@Serializable
data class PadConfig(
    // ── Device binding ────────────────────────────────────────────────────
    /** Table ID this PAD is permanently bound to. Empty = unbound (shows binding screen). */
    val boundTableId: String = "",
    /** Display name shown in the PAD header (e.g. "Table 5" or "HotPot Table A3"). */
    val tableDisplayName: String = "",

    // ── AYCE mode (Jamezz-style) ──────────────────────────────────────────
    /** When true, enables round-based ordering rules. */
    val ayceEnabled: Boolean = false,
    /** Max items guests can order per round. 0 = unlimited. */
    val maxItemsPerRound: Int = 8,
    /** Minimum minutes guests must wait before opening the next round. */
    val minMinutesBetweenRounds: Int = 15,
    /** Total rounds allowed. null = unlimited (premium package). */
    val totalRoundsLimit: Int? = null,
    /** Configurable AYCE packages shown at session start. */
    val aycePackages: List<AycePackage> = emptyList(),

    // ── Idle screen (Ziosk-style screensaver) ─────────────────────────────
    /** Seconds of inactivity before showing idle/screensaver screen. */
    val idleTimeoutSeconds: Int = 60,
    /** Promotional text lines to cycle through on idle screen. */
    val idlePromoLines: List<String> = emptyList(),

    // ── UI behaviour ──────────────────────────────────────────────────────
    /** Supported locale codes guest can switch to on the PAD (e.g. ["en", "zh", "ja"]). */
    val supportedLocales: List<String> = listOf("en"),
    /** Show item calorie/allergen info if available on menu item. */
    val showNutritionInfo: Boolean = false,
    /** Allow guests to add a note to individual items. */
    val allowItemNotes: Boolean = true,
    /** Show running subtotal in the cart while browsing. */
    val showRunningTotal: Boolean = true,
    /** Show all items previously ordered this session (order history tab). */
    val showOrderHistory: Boolean = true,
)

/**
 * A selectable AYCE dining package (e.g. "Standard – 5 rounds / ¥88").
 * Null rounds = unlimited.
 */
@Serializable
data class AycePackage(
    val id: String,
    val name: String,
    val description: String = "",
    val totalRoundsLimit: Int?,       // null = unlimited
    val maxItemsPerRound: Int = 8,
    val minMinutesBetweenRounds: Int = 15,
    val priceMinorUnit: Long = 0L,
)
