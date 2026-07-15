package com.restaurantpos.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatterTest {

    private val usd = AmountFormatter(DefaultRegionConfig)

    private val jpy = AmountFormatter(
        RegionConfig(
            currencyCode = "JPY", currencySymbol = "¥",
            currencyMinorDigits = 0, locale = "ja-JP",
            defaultTaxMode = TaxMode.INCLUSIVE,
            availableTaxRates = emptyList(),
            thousandsSeparator = ',', decimalSeparator = '.',
        )
    )

    private val eur = AmountFormatter(
        RegionConfig(
            currencyCode = "EUR", currencySymbol = "€",
            currencyMinorDigits = 2, locale = "de-DE",
            defaultTaxMode = TaxMode.INCLUSIVE,
            availableTaxRates = emptyList(),
            thousandsSeparator = '.', decimalSeparator = ',',
        )
    )

    @Test fun `USD 1250 formats as $12_50`() = assertEquals("\$12.50", usd.format(1250))
    @Test fun `USD 0 formats as $0_00`() = assertEquals("\$0.00", usd.format(0))
    @Test fun `USD 100000 formats as $1_000_00`() = assertEquals("\$1,000.00", usd.format(100000))
    @Test fun `USD 1234567 formats correctly with thousands`() = assertEquals("\$12,345.67", usd.format(1234567))

    @Test fun `JPY 1200 formats as ¥1_200 no decimal`() = assertEquals("¥1,200", jpy.format(1200))
    @Test fun `JPY 0 formats as ¥0`() = assertEquals("¥0", jpy.format(0))

    @Test fun `EUR uses dot as thousands and comma as decimal`() = assertEquals("€1.234,56", eur.format(123456))
}

class TaxRateTest {

    private val tax10 = TaxRate(
        id = "vat10", name = "VAT 10%",
        ratePermille = 100L,
        mode = TaxMode.EXCLUSIVE,
        rounding = RoundingMode.HALF_UP,
    )

    private val taxInclusive = TaxRate(
        id = "gst10", name = "GST 10%",
        ratePermille = 100L,
        mode = TaxMode.INCLUSIVE,
        rounding = RoundingMode.HALF_UP,
    )

    @Test fun `exclusive 10pct on 1000 = 100`() = assertEquals(100L, tax10.taxOn(1000L))
    @Test fun `exclusive 10pct on 0 = 0`() = assertEquals(0L, tax10.taxOn(0L))
    @Test fun `exclusive 10pct on 1 rounds half-up`() = assertEquals(0L, tax10.taxOn(1L))  // 1*100/1000 = 0.1 → 0
    @Test fun `exclusive 10pct on 5 rounds half-up`() = assertEquals(1L, tax10.taxOn(5L))  // 5*100/1000 = 0.5 → 1
    @Test fun `exclusive 10pct on 999 = 100`() = assertEquals(100L, tax10.taxOn(999L))     // 99.9 → 100

    @Test fun `inclusive 10pct on 1100 = 100`() = assertEquals(100L, taxInclusive.taxOn(1100L))
    @Test fun `inclusive 10pct on 0 = 0`() = assertEquals(0L, taxInclusive.taxOn(0L))
}
