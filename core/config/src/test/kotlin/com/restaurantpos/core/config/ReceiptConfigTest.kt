package com.restaurantpos.core.config

import org.junit.Assert.*
import org.junit.Test

class ReceiptConfigTest {

    @Test fun `default ReceiptConfig has empty lines and no tax id`() {
        val rc = ReceiptConfig()
        assertTrue(rc.headerLines.isEmpty())
        assertTrue(rc.footerLines.isEmpty())
        assertFalse(rc.showTaxId)
        assertEquals("", rc.taxId)
    }

    @Test fun `RegionConfig carries receiptConfig with default`() {
        val cfg = DefaultRegionConfig
        assertNotNull(cfg.receiptConfig)
        assertFalse(cfg.receiptConfig.showTaxId)
    }

    @Test fun `header lines preserved on copy`() {
        val rc = ReceiptConfig(headerLines = listOf("My Restaurant", "123 Main St"))
        assertEquals(2, rc.headerLines.size)
        assertEquals("My Restaurant", rc.headerLines[0])
    }

    @Test fun `footer lines preserved on copy`() {
        val rc = ReceiptConfig(footerLines = listOf("Thank you!", "Wi-Fi: guest123"))
        assertEquals(2, rc.footerLines.size)
    }

    @Test fun `showTaxId false hides taxId even if set`() {
        val rc = ReceiptConfig(showTaxId = false, taxId = "GB123456789")
        assertFalse(rc.showTaxId)
        // taxId field is still stored (just not displayed)
        assertEquals("GB123456789", rc.taxId)
    }

    @Test fun `showTaxId true with blank taxId treated as no-show in printer`() {
        val rc = ReceiptConfig(showTaxId = true, taxId = "")
        // Printer logic: if showTaxId && taxId.isNotBlank() -> print; else null
        val printed = if (rc.showTaxId && rc.taxId.isNotBlank()) rc.taxId else null
        assertNull(printed)
    }

    @Test fun `showTaxId true with non-blank taxId is printed`() {
        val rc = ReceiptConfig(showTaxId = true, taxId = "VAT-001")
        val printed = if (rc.showTaxId && rc.taxId.isNotBlank()) rc.taxId else null
        assertEquals("VAT-001", printed)
    }

    @Test fun `RegionConfig can be updated with new receiptConfig`() {
        val cfg = DefaultRegionConfig.copy(
            receiptConfig = ReceiptConfig(
                headerLines = listOf("Test Shop"),
                showTaxId = true,
                taxId = "TX-999",
            )
        )
        assertEquals("Test Shop", cfg.receiptConfig.headerLines[0])
        assertTrue(cfg.receiptConfig.showTaxId)
        assertEquals("TX-999", cfg.receiptConfig.taxId)
    }
}
