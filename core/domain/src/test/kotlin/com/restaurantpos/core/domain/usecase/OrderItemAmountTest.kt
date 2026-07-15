package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderItemStatus
import com.restaurantpos.core.model.SelectedModifier
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderItemAmountTest {

    private fun makeItem(
        unitPrice: Long,
        qty: Int,
        modifiers: List<SelectedModifier> = emptyList(),
    ) = OrderItem(
        id = "item-test",
        orderId = "order-test",
        menuItemId = "menu-test",
        menuItemNameSnapshot = mapOf("en" to "Test Item"),
        quantity = qty,
        unitPriceMinorUnit = unitPrice,
        taxRateId = null,
        status = OrderItemStatus.PENDING,
        selectedModifiers = modifiers,
    )

    private fun modifier(groupId: String, modId: String, adjustment: Long) = SelectedModifier(
        groupId = groupId,
        modifierId = modId,
        nameSnapshot = mapOf("en" to modId),
        priceAdjustmentMinorUnit = adjustment,
    )

    @Test
    fun `no modifiers lineTotalMinorUnit equals unitPrice times qty`() {
        val item = makeItem(unitPrice = 1200L, qty = 3)
        assertEquals(3600L, item.lineTotalMinorUnit)
    }

    @Test
    fun `single modifier surcharge is added to unit price`() {
        val item = makeItem(
            unitPrice = 1200L,
            qty = 2,
            modifiers = listOf(modifier("g1", "m1", 150L)),
        )
        // effectiveUnitPrice = 1200 + 150 = 1350; lineTotal = 1350 * 2 = 2700
        assertEquals(1350L, item.effectiveUnitPrice)
        assertEquals(2700L, item.lineTotalMinorUnit)
    }

    @Test
    fun `multiple modifiers accumulate correctly`() {
        val item = makeItem(
            unitPrice = 1200L,
            qty = 1,
            modifiers = listOf(
                modifier("g1", "m-cheese", 150L),
                modifier("g1", "m-bacon", 200L),
                modifier("g1", "m-avocado", 180L),
            ),
        )
        // effectiveUnitPrice = 1200 + 150 + 200 + 180 = 1730
        assertEquals(1730L, item.effectiveUnitPrice)
        assertEquals(1730L, item.lineTotalMinorUnit)
    }

    @Test
    fun `negative modifier discount reduces unit price`() {
        val item = makeItem(
            unitPrice = 1200L,
            qty = 2,
            modifiers = listOf(modifier("g-disc", "m-disc", -200L)),
        )
        // effectiveUnitPrice = 1200 - 200 = 1000; lineTotal = 1000 * 2 = 2000
        assertEquals(1000L, item.effectiveUnitPrice)
        assertEquals(2000L, item.lineTotalMinorUnit)
    }

    @Test
    fun `modifier adjustment per unit is sum of all adjustments`() {
        val item = makeItem(
            unitPrice = 500L,
            qty = 3,
            modifiers = listOf(
                modifier("g1", "m1", 100L),
                modifier("g2", "m2", -50L),
            ),
        )
        assertEquals(50L, item.modifierAdjustmentPerUnit)
        assertEquals(550L, item.effectiveUnitPrice)
        assertEquals(1650L, item.lineTotalMinorUnit)
    }
}
