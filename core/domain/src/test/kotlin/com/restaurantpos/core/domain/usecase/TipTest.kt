package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TipTest {

    private lateinit var orderRepo: FakeOrderRepository

    private fun order(id: String, subtotal: Long) = Order(
        id = id,
        sourceTerminalId = "t1",
        subtotalMinorUnit = subtotal,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Before fun setup() {
        orderRepo = FakeOrderRepository()
    }

    @Test fun `tipMinorUnit defaults to zero`() {
        val o = order("o1", 5000L)
        assertEquals(0L, o.tipMinorUnit)
    }

    @Test fun `totalMinorUnit includes tip`() {
        val o = order("o1", 5000L).copy(
            taxTotalMinorUnit = 500L,
            tipMinorUnit = 750L,
        )
        assertEquals(6250L, o.totalMinorUnit)
    }

    @Test fun `totalMinorUnit tip plus service charge`() {
        val o = order("o1", 10000L).copy(
            serviceChargeMinorUnit = 1000L,
            tipMinorUnit = 1500L,
        )
        assertEquals(12500L, o.totalMinorUnit)
    }

    @Test fun `totalMinorUnit tip does not affect discount calculation`() {
        val o = order("o1", 10000L).copy(
            discountMinorUnit = 1000L,
            tipMinorUnit = 500L,
        )
        // 10000 - 1000 + 500 = 9500
        assertEquals(9500L, o.totalMinorUnit)
    }

    @Test fun `setTip persists to repository`() = runBlocking {
        orderRepo.orders["o1"] = order("o1", 5000L)
        orderRepo.setTip("o1", 750L)
        assertEquals(750L, orderRepo.orders["o1"]!!.tipMinorUnit)
    }

    @Test fun `setTip can be cleared by setting to zero`() = runBlocking {
        orderRepo.orders["o1"] = order("o1", 5000L).copy(tipMinorUnit = 1000L)
        orderRepo.setTip("o1", 0L)
        assertEquals(0L, orderRepo.orders["o1"]!!.tipMinorUnit)
    }

    @Test fun `10 percent tip calculation`() {
        val subtotal = 8000L
        val tip = subtotal * 10 / 100
        assertEquals(800L, tip)
        val o = order("o1", subtotal).copy(tipMinorUnit = tip)
        assertEquals(8800L, o.totalMinorUnit)
    }
}
