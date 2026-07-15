package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrderNotesTest {

    private lateinit var orderRepo: FakeOrderRepository
    private val now = 1_700_000_000_000L

    private fun order(id: String = "o1", notes: String = "") = Order(
        id = id,
        sourceTerminalId = "t1",
        createdAt = now,
        updatedAt = now,
        orderNotes = notes,
    )

    @Before
    fun setup() {
        orderRepo = FakeOrderRepository()
    }

    @Test
    fun `orderNotes defaults to empty string`() {
        val o = order()
        assertEquals("", o.orderNotes)
    }

    @Test
    fun `orderNotes survives copy without explicit override`() {
        val o = order(notes = "no peanuts")
        val copied = o.copy(status = OrderStatus.IN_PROGRESS)
        assertEquals("no peanuts", copied.orderNotes)
    }

    @Test
    fun `saving order notes persists to repository`() = runTest {
        val o = order(notes = "")
        orderRepo.save(o)

        val updated = o.copy(orderNotes = "birthday table", updatedAt = now + 1)
        orderRepo.save(updated)

        val retrieved = orderRepo.getById("o1")
        assertEquals("birthday table", retrieved?.orderNotes)
    }

    @Test
    fun `trimming whitespace in notes`() {
        val raw = "  allergy: shellfish  "
        assertEquals("allergy: shellfish", raw.trim())
    }

    @Test
    fun `empty notes isBlank`() {
        val o = order(notes = "")
        assertTrue(o.orderNotes.isBlank())
    }

    @Test
    fun `non-empty notes isNotBlank`() {
        val o = order(notes = "VIP guest")
        assertFalse(o.orderNotes.isBlank())
    }

    @Test
    fun `notes field in totalMinorUnit is unaffected`() {
        val o = order(notes = "some note").copy(
            subtotalMinorUnit = 1000L,
            taxTotalMinorUnit = 100L,
        )
        assertEquals(1100L, o.totalMinorUnit)
    }
}
