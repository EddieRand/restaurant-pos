package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AllergenGuestCountTest {

    // ── Allergen model ────────────────────────────────────────────────────────

    @Test fun `MenuItem allergens default to empty`() {
        val item = MenuItem("mi-1", mapOf("en" to "Burger"), 1200L, null, "cat-mains")
        assertTrue(item.allergens.isEmpty())
    }

    @Test fun `MenuItem can hold multiple allergens`() {
        val item = MenuItem("mi-1", mapOf("en" to "Salmon Pasta"), 2400L, null, "cat-mains",
            allergens = setOf(Allergen.GLUTEN, Allergen.FISH, Allergen.DAIRY))
        assertEquals(3, item.allergens.size)
        assertTrue(Allergen.FISH in item.allergens)
    }

    @Test fun `OrderItem allergenSnapshot defaults to empty`() {
        val item = OrderItem(
            id = "oi-1", orderId = "o-1", menuItemId = "mi-1",
            menuItemNameSnapshot = mapOf("en" to "Burger"),
            quantity = 1, unitPriceMinorUnit = 1200L, taxRateId = null,
        )
        assertTrue(item.allergenSnapshot.isEmpty())
    }

    @Test fun `OrderItem allergenSnapshot preserved from MenuItem`() {
        val allergens = setOf(Allergen.NUTS, Allergen.DAIRY)
        val item = OrderItem(
            id = "oi-1", orderId = "o-1", menuItemId = "mi-1",
            menuItemNameSnapshot = mapOf("en" to "Cake"),
            quantity = 1, unitPriceMinorUnit = 800L, taxRateId = null,
            allergenSnapshot = allergens,
        )
        assertEquals(allergens, item.allergenSnapshot)
    }

    @Test fun `Allergen enum covers common dietary restrictions`() {
        val all = Allergen.entries
        assertTrue(Allergen.GLUTEN in all)
        assertTrue(Allergen.DAIRY in all)
        assertTrue(Allergen.NUTS in all)
        assertTrue(Allergen.SHELLFISH in all)
    }

    // ── Guest count ───────────────────────────────────────────────────────────

    @Test fun `Order guestCount defaults to 1`() {
        val order = Order(
            id = "o-1", sourceTerminalId = "t1",
            createdAt = 1000L, updatedAt = 1000L,
        )
        assertEquals(1, order.guestCount)
    }

    @Test fun `Order guestCount can be set at seating`() {
        val order = Order(
            id = "o-1", sourceTerminalId = "t1",
            guestCount = 4,
            createdAt = 1000L, updatedAt = 1000L,
        )
        assertEquals(4, order.guestCount)
    }

    @Test fun `guestCount does not affect totalMinorUnit`() {
        val o1 = Order("o-1", sourceTerminalId = "t1", subtotalMinorUnit = 5000L,
            guestCount = 2, createdAt = 1L, updatedAt = 1L)
        val o4 = o1.copy(guestCount = 4)
        assertEquals(o1.totalMinorUnit, o4.totalMinorUnit)
    }

    @Test fun `perGuestAverage calculation`() {
        val order = Order(
            id = "o-1", sourceTerminalId = "t1",
            subtotalMinorUnit = 8400L, guestCount = 4,
            createdAt = 1L, updatedAt = 1L,
        )
        val perGuest = order.totalMinorUnit / order.guestCount
        assertEquals(2100L, perGuest)
    }
}
