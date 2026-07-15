package com.restaurantpos.core.config

enum class TaxMode {
    INCLUSIVE,  // price already includes tax
    EXCLUSIVE,  // tax added on top of price
}

enum class RoundingMode {
    HALF_UP,
    HALF_DOWN,
    CEILING,
    FLOOR,
}

data class TaxRate(
    val id: String,
    val name: String,             // e.g. "VAT", "GST", "消費税" — merchant-configured
    val ratePermille: Long,       // rate * 1000, e.g. 10% = 100, 7.5% = 75
    val mode: TaxMode = TaxMode.EXCLUSIVE,
    val rounding: RoundingMode = RoundingMode.HALF_UP,
) {
    /** Tax amount on a given price (minor units). */
    fun taxOn(priceMinorUnit: Long): Long {
        val raw = when (mode) {
            // Multiply by 1000 first to preserve 3 decimal places before round()
            TaxMode.EXCLUSIVE -> priceMinorUnit * ratePermille
            TaxMode.INCLUSIVE -> priceMinorUnit * ratePermille * 1000L / (1000L + ratePermille)
        }
        return round(raw, rounding)
    }

    private fun round(raw: Long, mode: RoundingMode): Long {
        val quotient = raw / 1000L
        val remainder = raw % 1000L
        return when (mode) {
            RoundingMode.HALF_UP -> if (remainder >= 500) quotient + 1 else quotient
            RoundingMode.HALF_DOWN -> if (remainder > 500) quotient + 1 else quotient
            RoundingMode.CEILING -> if (remainder > 0) quotient + 1 else quotient
            RoundingMode.FLOOR -> quotient
        }
    }
}
