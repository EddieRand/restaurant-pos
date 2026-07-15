package com.restaurantpos.core.model

/**
 * The screen the Customer Display System (CDS) should show, driven by the cashier as it
 * moves through checkout. Broadcast to the server and polled by the customer-facing display.
 */
enum class CdsPhase {
    /** Idle — no active order. */
    WELCOME,
    /** Order being reviewed/built. */
    ORDER,
    /** Tip prompt (reserved; no cashier step triggers this today). */
    TIP,
    /** Payment in flight. */
    PROCESSING,
    /** Payment completed. */
    SUCCESS,
    /** Receipt delivery selection (reserved; no cashier step triggers this today). */
    RECEIPT,
}
