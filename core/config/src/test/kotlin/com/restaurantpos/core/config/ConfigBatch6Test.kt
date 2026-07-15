package com.restaurantpos.core.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigBatch6Test {

    // ── InMemoryConfigRepository ──────────────────────────────────────────────

    @Test fun `default config is DefaultRegionConfig`() {
        val repo = InMemoryConfigRepository()
        assertEquals(DefaultRegionConfig, repo.current())
    }

    @Test fun `update propagates via flow`() = runTest {
        val repo = InMemoryConfigRepository()
        val jpyConfig = DefaultRegionConfig.copy(currencyCode = "JPY", currencySymbol = "¥", currencyMinorDigits = 0)
        repo.update(jpyConfig)
        assertEquals(jpyConfig, repo.config.first())
    }

    @Test fun `current() reflects latest update`() = runTest {
        val repo = InMemoryConfigRepository()
        val eur = DefaultRegionConfig.copy(currencyCode = "EUR", currencySymbol = "€", decimalSeparator = ',', thousandsSeparator = '.')
        repo.update(eur)
        assertEquals("EUR", repo.current().currencyCode)
    }

    // ── AmountFormatter changes with RegionConfig ─────────────────────────────

    @Test fun `switching USD to JPY changes formatting`() = runTest {
        val repo = InMemoryConfigRepository()
        val usdFmt = AmountFormatter(repo.current())
        assertEquals("\$12.50", usdFmt.format(1250))

        repo.update(repo.current().copy(
            currencyCode = "JPY", currencySymbol = "¥", currencyMinorDigits = 0,
        ))
        val jpyFmt = AmountFormatter(repo.current())
        assertEquals("¥1,250", jpyFmt.format(1250))
    }

    @Test fun `switching to EUR with comma decimal changes formatting`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.update(
            DefaultRegionConfig.copy(
                currencyCode = "EUR",
                currencySymbol = "€",
                currencyMinorDigits = 2,
                thousandsSeparator = '.',
                decimalSeparator = ',',
            )
        )
        val fmt = AmountFormatter(repo.current())
        assertEquals("€1.234,56", fmt.format(123456))
    }

    @Test fun `zero minor digits skips decimal separator`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.update(repo.current().copy(currencyMinorDigits = 0, currencySymbol = "Rp"))
        val fmt = AmountFormatter(repo.current())
        assertEquals("Rp15,000", fmt.format(15000))
    }

    // ── TaxRate calculations remain correct across configs ────────────────────

    @Test fun `exclusive tax 8pct on 10000 = 800`() {
        val tax = TaxRate(id = "t1", name = "GST 8%", ratePermille = 80L, mode = TaxMode.EXCLUSIVE)
        assertEquals(800L, tax.taxOn(10000L))
    }

    @Test fun `inclusive tax 10pct on 11000 = 1000`() {
        val tax = TaxRate(id = "t2", name = "VAT 10%", ratePermille = 100L, mode = TaxMode.INCLUSIVE)
        assertEquals(1000L, tax.taxOn(11000L))
    }

    @Test fun `tax rate lookup by id works`() {
        val rate = TaxRate(id = "r1", name = "Standard", ratePermille = 100L)
        val config = DefaultRegionConfig.copy(availableTaxRates = listOf(rate))
        assertEquals(rate, config.taxRateById("r1"))
        assertEquals(null, config.taxRateById("missing"))
    }

    @Test fun `multiple sequential updates are all visible via current()`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.update(repo.current().copy(locale = "zh-CN"))
        repo.update(repo.current().copy(currencySymbol = "¥"))
        repo.update(repo.current().copy(currencyMinorDigits = 2))
        assertEquals("zh-CN", repo.current().locale)
        assertEquals("¥", repo.current().currencySymbol)
        assertEquals(2, repo.current().currencyMinorDigits)
    }

    @Test fun `formatAmount omits currency symbol`() {
        val fmt = AmountFormatter(DefaultRegionConfig)
        assertEquals("12.50", fmt.formatAmount(1250))
    }

    @Test fun `formatAmount with JPY zero decimal`() {
        val cfg = DefaultRegionConfig.copy(currencyMinorDigits = 0, currencySymbol = "¥")
        val fmt = AmountFormatter(cfg)
        assertEquals("1,250", fmt.formatAmount(1250))
    }
}
