package com.restaurantpos.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TipConfigTest {

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    fun `default config has tip enabled with standard presets`() {
        val cfg = TipConfig()
        assertTrue(cfg.enabled)
        assertEquals(listOf(15, 18, 20, 22), cfg.presets)
        assertEquals(TipCalcBase.SUBTOTAL, cfg.calcBase)
        assertFalse(cfg.enabledOnQr)
        assertFalse(cfg.enabledOnKiosk)
        assertFalse(cfg.enabledOnPad)
    }

    // ── Pre-tax calculation ───────────────────────────────────────────────────

    @Test
    fun `15 percent tip on subtotal`() {
        val subtotal = 10000L // ¥100.00
        val tax = 800L        // ¥8.00
        val tip = calcTip(pct = 15, subtotal = subtotal, tax = tax, base = TipCalcBase.SUBTOTAL)
        assertEquals(1500L, tip) // 15% of 100 = ¥15.00
    }

    @Test
    fun `18 percent tip on subtotal ignores tax`() {
        val subtotal = 10000L
        val tax = 800L
        val tip = calcTip(pct = 18, subtotal = subtotal, tax = tax, base = TipCalcBase.SUBTOTAL)
        assertEquals(1800L, tip) // 18% of 100 = ¥18.00
    }

    // ── Post-tax calculation ──────────────────────────────────────────────────

    @Test
    fun `15 percent tip on post-tax total includes tax`() {
        val subtotal = 10000L
        val tax = 800L
        val tip = calcTip(pct = 15, subtotal = subtotal, tax = tax, base = TipCalcBase.POST_TAX)
        assertEquals(1620L, tip) // 15% of (100+8) = ¥16.20
    }

    @Test
    fun `20 percent tip post-tax`() {
        val subtotal = 5000L
        val tax = 500L
        val tip = calcTip(pct = 20, subtotal = subtotal, tax = tax, base = TipCalcBase.POST_TAX)
        assertEquals(1100L, tip) // 20% of 55.00 = ¥11.00
    }

    // ── Zero tip ─────────────────────────────────────────────────────────────

    @Test
    fun `zero percent tip is always zero`() {
        assertEquals(0L, calcTip(0, 10000L, 800L, TipCalcBase.SUBTOTAL))
        assertEquals(0L, calcTip(0, 10000L, 800L, TipCalcBase.POST_TAX))
    }

    // ── Disabled config ───────────────────────────────────────────────────────

    @Test
    fun `disabled tip config should not render`() {
        val cfg = TipConfig(enabled = false)
        assertFalse(cfg.enabled)
    }

    // ── Empty presets ─────────────────────────────────────────────────────────

    @Test
    fun `empty presets list is valid`() {
        val cfg = TipConfig(presets = emptyList())
        assertTrue(cfg.presets.isEmpty())
    }

    // ── RegionConfig integration ──────────────────────────────────────────────

    @Test
    fun `RegionConfig default includes TipConfig with defaults`() {
        val regionConfig = DefaultRegionConfig
        assertTrue(regionConfig.tipConfig.enabled)
        assertEquals(listOf(15, 18, 20, 22), regionConfig.tipConfig.presets)
    }

    @Test
    fun `custom tip config can override presets and calcBase`() {
        val cfg = TipConfig(
            presets = listOf(10, 15, 20),
            calcBase = TipCalcBase.POST_TAX,
            enabledOnQr = true,
        )
        assertEquals(listOf(10, 15, 20), cfg.presets)
        assertEquals(TipCalcBase.POST_TAX, cfg.calcBase)
        assertTrue(cfg.enabledOnQr)
        assertFalse(cfg.enabledOnKiosk)
    }

    // ── Helper (mirrors Android CheckoutScreen logic) ─────────────────────────
    private fun calcTip(pct: Int, subtotal: Long, tax: Long, base: TipCalcBase): Long {
        if (pct == 0) return 0L
        val calcBase = if (base == TipCalcBase.POST_TAX) subtotal + tax else subtotal
        return calcBase * pct / 100
    }
}
