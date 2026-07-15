package com.restaurantpos.core.config

/**
 * Converts Long minor-unit amounts to localised display strings.
 * All formatting is driven by RegionConfig — no locale constants here.
 */
class AmountFormatter(private val config: RegionConfig) {

    /**
     * Returns e.g. "$12.50", "¥1,200", "€1.234,56"
     * depending on currencyMinorDigits, separators, and symbol.
     */
    fun format(amountMinorUnit: Long): String {
        val divisor = pow10(config.currencyMinorDigits)
        val whole = amountMinorUnit / divisor
        val fraction = amountMinorUnit % divisor

        val wholeStr = insertThousands(whole, config.thousandsSeparator)

        return if (config.currencyMinorDigits == 0) {
            "${config.currencySymbol}$wholeStr"
        } else {
            val fractionStr = fraction.toString().padStart(config.currencyMinorDigits, '0')
            "${config.currencySymbol}$wholeStr${config.decimalSeparator}$fractionStr"
        }
    }

    /** Format without currency symbol, e.g. for sub-totals in a list. */
    fun formatAmount(amountMinorUnit: Long): String {
        val divisor = pow10(config.currencyMinorDigits)
        val whole = amountMinorUnit / divisor
        val fraction = amountMinorUnit % divisor
        val wholeStr = insertThousands(whole, config.thousandsSeparator)
        return if (config.currencyMinorDigits == 0) {
            wholeStr
        } else {
            val fractionStr = fraction.toString().padStart(config.currencyMinorDigits, '0')
            "$wholeStr${config.decimalSeparator}$fractionStr"
        }
    }

    private fun insertThousands(value: Long, separator: Char): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val sb = StringBuilder()
        val offset = s.length % 3
        s.forEachIndexed { index, c ->
            if (index != 0 && (index - offset) % 3 == 0) sb.append(separator)
            sb.append(c)
        }
        return sb.toString()
    }

    private fun pow10(exp: Int): Long {
        var result = 1L
        repeat(exp) { result *= 10 }
        return result
    }
}
