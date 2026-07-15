package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.TaxRate
import com.restaurantpos.core.config.TaxMode
import com.restaurantpos.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Tests for PlaceOrderUseCase and SettlePaymentUseCase — the critical path
 * through the entire order → payment lifecycle.
 */
class CoreFlowTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var paymentRepo: FakePaymentRepository
    private lateinit var tableRepo: FakeTableRepository
    private lateinit var placeOrder: PlaceOrderUseCase
    private lateinit var settlePayment: SettlePaymentUseCase

    @Before
    fun setUp() {
        orderRepo = FakeOrderRepository()
        paymentRepo = FakePaymentRepository()
        tableRepo = FakeTableRepository()

        val config = DefaultRegionConfig.copy(
            availableTaxRates = listOf(
                TaxRate(id = "tax10", name = "10%", ratePermille = 100L, mode = TaxMode.EXCLUSIVE),
            ),
        )
        placeOrder = PlaceOrderUseCase(orderRepo, tableRepo, config)
        settlePayment = SettlePaymentUseCase(orderRepo, paymentRepo, tableRepo)
    }

    private fun makeOrder(
        id: String = UUID.randomUUID().toString(),
        tableId: String? = null,
        status: OrderStatus = OrderStatus.DRAFT,
        subtotal: Long = 0L,
    ) = Order(
        id = id,
        tableId = tableId,
        status = status,
        sourceTerminalId = "test",
        subtotalMinorUnit = subtotal,
        taxTotalMinorUnit = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun makeItem(
        orderId: String,
        price: Long = 1000L,
        qty: Int = 1,
        taxRateId: String? = null,
        modifiers: List<SelectedModifier> = emptyList(),
    ) = OrderItem(
        id = UUID.randomUUID().toString(),
        orderId = orderId,
        menuItemId = "mi-1",
        menuItemNameSnapshot = mapOf("en" to "Burger"),
        quantity = qty,
        unitPriceMinorUnit = price,
        taxRateId = taxRateId,
        selectedModifiers = modifiers,
        status = OrderItemStatus.PENDING,
    )

    // ── PlaceOrderUseCase ──────────────────────────────────────────────────────

    @Test fun `takeaway order (no table) gets a pickup code on place`() = runTest {
        val order = makeOrder("pk1", tableId = null)
        orderRepo.orders["pk1"] = order

        placeOrder(PlaceOrderUseCase.Params("pk1", listOf(makeItem("pk1"))))

        assertEquals("1", orderRepo.orders["pk1"]!!.pickupCode)
    }

    @Test fun `dine-in order (with table) never gets a pickup code`() = runTest {
        tableRepo.tables["t9"] = Table(id = "t9", name = "T9", sectionId = "in", capacity = 4, status = TableStatus.OCCUPIED)
        val order = makeOrder("pk2", tableId = "t9")
        orderRepo.orders["pk2"] = order

        placeOrder(PlaceOrderUseCase.Params("pk2", listOf(makeItem("pk2"))))

        assertNull(orderRepo.orders["pk2"]!!.pickupCode)
    }

    @Test fun `re-placing an order keeps its existing pickup code`() = runTest {
        val order = makeOrder("pk3", tableId = null).copy(pickupCode = "7")
        orderRepo.orders["pk3"] = order

        placeOrder(PlaceOrderUseCase.Params("pk3", listOf(makeItem("pk3"))))

        assertEquals("7", orderRepo.orders["pk3"]!!.pickupCode)
    }


    @Test fun `re-firing extra items on PLACED order keeps PLACED and recalculates full totals`() = runTest {
        val order = makeOrder("rf1", tableId = "t2", status = OrderStatus.PLACED, subtotal = 250L)
        orderRepo.orders["rf1"] = order
        val existing = makeItem("rf1", price = 250L).copy(status = OrderItemStatus.PLACED)
        orderRepo.items[existing.id] = existing

        val extra = makeItem("rf1", price = 250L)
        val result = placeOrder(PlaceOrderUseCase.Params("rf1", listOf(extra)))

        assertTrue(result is PlaceOrderUseCase.Result.Success)
        val saved = orderRepo.orders["rf1"]!!
        assertEquals(OrderStatus.PLACED, saved.status)
        // Subtotal must cover ALL items, not just the newly fired ones
        assertEquals(500L, saved.subtotalMinorUnit)
        assertEquals(OrderItemStatus.PLACED, orderRepo.items[extra.id]!!.status)
    }

    @Test fun `re-firing extra items on READY order returns it to PLACED`() = runTest {
        val order = makeOrder("rf2", status = OrderStatus.READY)
        orderRepo.orders["rf2"] = order

        val result = placeOrder(PlaceOrderUseCase.Params("rf2", listOf(makeItem("rf2"))))

        assertTrue(result is PlaceOrderUseCase.Result.Success)
        assertEquals(OrderStatus.PLACED, orderRepo.orders["rf2"]!!.status)
    }

    @Test fun `re-fire excludes refunded items from recalculated totals`() = runTest {
        val order = makeOrder("rf3", status = OrderStatus.PLACED)
        orderRepo.orders["rf3"] = order
        val refunded = makeItem("rf3", price = 900L).copy(status = OrderItemStatus.REFUNDED)
        orderRepo.items[refunded.id] = refunded

        placeOrder(PlaceOrderUseCase.Params("rf3", listOf(makeItem("rf3", price = 300L))))

        assertEquals(300L, orderRepo.orders["rf3"]!!.subtotalMinorUnit)
    }

    @Test fun `placing items on CLOSED order errors`() = runTest {
        val order = makeOrder("rf4", status = OrderStatus.CLOSED)
        orderRepo.orders["rf4"] = order

        val result = placeOrder(PlaceOrderUseCase.Params("rf4", listOf(makeItem("rf4"))))

        assertTrue(result is PlaceOrderUseCase.Result.Error)
        assertEquals(OrderStatus.CLOSED, orderRepo.orders["rf4"]!!.status)
    }

    @Test fun `place order DRAFT with items → PLACED, subtotal calculated`() = runTest {
        val order = makeOrder("o1")
        orderRepo.orders["o1"] = order
        val items = listOf(makeItem("o1", price = 500L, qty = 2))

        val result = placeOrder(PlaceOrderUseCase.Params("o1", items))

        assertTrue(result is PlaceOrderUseCase.Result.Success)
        val saved = orderRepo.orders["o1"]!!
        assertEquals(OrderStatus.PLACED, saved.status)
        assertEquals(1000L, saved.subtotalMinorUnit) // 500 * 2
    }

    @Test fun `modifier price adjustments included in subtotal`() = runTest {
        val order = makeOrder("om1")
        orderRepo.orders["om1"] = order
        val cheese = SelectedModifier(
            groupId = "mg-extra", modifierId = "m-cheese",
            nameSnapshot = mapOf("en" to "Cheese"), priceAdjustmentMinorUnit = 150L,
        )
        val items = listOf(makeItem("om1", price = 1200L, qty = 2, modifiers = listOf(cheese)))
        // expected: (1200 + 150) * 2 = 2700

        val result = placeOrder(PlaceOrderUseCase.Params("om1", items))

        assertTrue(result is PlaceOrderUseCase.Result.Success)
        assertEquals(2700L, orderRepo.orders["om1"]!!.subtotalMinorUnit)
    }

    @Test fun `modifier tax calculated on effective price including modifier`() = runTest {
        val order = makeOrder("om2")
        orderRepo.orders["om2"] = order
        val extra = SelectedModifier(
            groupId = "mg", modifierId = "m1",
            nameSnapshot = mapOf("en" to "Extra"), priceAdjustmentMinorUnit = 200L,
        )
        val items = listOf(makeItem("om2", price = 1000L, qty = 1, taxRateId = "tax10", modifiers = listOf(extra)))
        // effective price = 1200, tax 10% = 120

        placeOrder(PlaceOrderUseCase.Params("om2", items))

        assertEquals(1200L, orderRepo.orders["om2"]!!.subtotalMinorUnit)
        assertEquals(120L,  orderRepo.orders["om2"]!!.taxTotalMinorUnit)
    }

    @Test fun `place order calculates tax correctly`() = runTest {
        val order = makeOrder("o2")
        orderRepo.orders["o2"] = order
        val items = listOf(makeItem("o2", price = 1000L, qty = 1, taxRateId = "tax10"))

        val result = placeOrder(PlaceOrderUseCase.Params("o2", items))

        assertTrue(result is PlaceOrderUseCase.Result.Success)
        val saved = orderRepo.orders["o2"]!!
        assertEquals(1000L, saved.subtotalMinorUnit)
        assertEquals(100L, saved.taxTotalMinorUnit)  // 10% of 1000
    }

    @Test fun `place empty order returns error`() = runTest {
        val order = makeOrder("o3")
        orderRepo.orders["o3"] = order

        val result = placeOrder(PlaceOrderUseCase.Params("o3", emptyList()))

        assertTrue(result is PlaceOrderUseCase.Result.Error)
    }

    @Test fun `place order not found returns error`() = runTest {
        val result = placeOrder(PlaceOrderUseCase.Params("nonexistent", listOf(makeItem("nonexistent"))))
        assertTrue(result is PlaceOrderUseCase.Result.Error)
    }

    @Test fun `place order advances OCCUPIED table to ORDERED`() = runTest {
        val order = makeOrder("o4", tableId = "t1")
        orderRepo.orders["o4"] = order
        tableRepo.tables["t1"] = Table(id = "t1", name = "T1", sectionId = "s1", capacity = 4, status = TableStatus.OCCUPIED, currentOrderId = "o4")
        val items = listOf(makeItem("o4", price = 800L))

        placeOrder(PlaceOrderUseCase.Params("o4", items))

        assertEquals(TableStatus.ORDERED, tableRepo.tables["t1"]!!.status)
    }

    @Test fun `place order with IN_PROGRESS status proceeds`() = runTest {
        val order = makeOrder("o5", status = OrderStatus.IN_PROGRESS)
        orderRepo.orders["o5"] = order
        val items = listOf(makeItem("o5", price = 300L))

        val result = placeOrder(PlaceOrderUseCase.Params("o5", items))
        assertTrue(result is PlaceOrderUseCase.Result.Success)
    }

    // ── SettlePaymentUseCase ───────────────────────────────────────────────────

    private fun makePayment(orderId: String, amount: Long) = Payment(
        id = UUID.randomUUID().toString(),
        orderId = orderId,
        amountMinorUnit = amount,
        method = PaymentMethod.CASH,
        status = PaymentStatus.UNPAID,
        operatorId = "op1",
        createdAt = System.currentTimeMillis(),
    )

    @Test fun `partial payment stays PLACED, order not closed`() = runTest {
        val order = makeOrder("p1", status = OrderStatus.PLACED, subtotal = 1000L)
        orderRepo.orders["p1"] = order

        val result = settlePayment(SettlePaymentUseCase.Params(makePayment("p1", 500L)))

        assertTrue(result is SettlePaymentUseCase.Result.Success)
        assertEquals(OrderStatus.PLACED, orderRepo.orders["p1"]!!.status)
        assertEquals(PaymentStatus.PAID, paymentRepo.payments.values.first().status)
    }

    @Test fun `full payment closes order and marks table DIRTY`() = runTest {
        val order = makeOrder("p2", tableId = "t2", status = OrderStatus.PLACED, subtotal = 1000L)
        orderRepo.orders["p2"] = order
        tableRepo.tables["t2"] = Table(id = "t2", name = "T2", sectionId = "s1", capacity = 4, status = TableStatus.ORDERED, currentOrderId = "p2")

        val result = settlePayment(SettlePaymentUseCase.Params(makePayment("p2", 1000L)))

        assertTrue(result is SettlePaymentUseCase.Result.Success)
        assertEquals(OrderStatus.CLOSED, orderRepo.orders["p2"]!!.status)
        assertEquals(TableStatus.DIRTY, tableRepo.tables["t2"]!!.status)
    }

    @Test fun `overpayment rejected with error`() = runTest {
        val order = makeOrder("p3", status = OrderStatus.PLACED, subtotal = 500L)
        orderRepo.orders["p3"] = order

        val result = settlePayment(SettlePaymentUseCase.Params(makePayment("p3", 600L)))

        assertTrue(result is SettlePaymentUseCase.Result.Error)
        assertTrue(paymentRepo.payments.isEmpty())
    }

    @Test fun `settle order not found returns error`() = runTest {
        val result = settlePayment(SettlePaymentUseCase.Params(makePayment("missing", 100L)))
        assertTrue(result is SettlePaymentUseCase.Result.Error)
    }

    @Test fun `two partial payments sum to full payment closes order`() = runTest {
        val order = makeOrder("p4", status = OrderStatus.PLACED, subtotal = 1000L)
        orderRepo.orders["p4"] = order

        settlePayment(SettlePaymentUseCase.Params(makePayment("p4", 400L)))
        assertEquals(OrderStatus.PLACED, orderRepo.orders["p4"]!!.status)

        settlePayment(SettlePaymentUseCase.Params(makePayment("p4", 600L)))
        assertEquals(OrderStatus.CLOSED, orderRepo.orders["p4"]!!.status)
    }

    @Test fun `settle CLOSED order returns error`() = runTest {
        val order = makeOrder("p5", status = OrderStatus.CLOSED, subtotal = 1000L)
        orderRepo.orders["p5"] = order

        val result = settlePayment(SettlePaymentUseCase.Params(makePayment("p5", 1000L)))
        assertTrue(result is SettlePaymentUseCase.Result.Error)
    }

    // ── Full end-to-end: place → settle ───────────────────────────────────────

    @Test fun `full e2e place order then settle closes all`() = runTest {
        val order = makeOrder("e2e1", tableId = "te1", status = OrderStatus.DRAFT)
        orderRepo.orders["e2e1"] = order
        tableRepo.tables["te1"] = Table(id = "te1", name = "T-E2E", sectionId = "s1", capacity = 4, status = TableStatus.OCCUPIED, currentOrderId = "e2e1")

        val items = listOf(makeItem("e2e1", price = 2000L, qty = 1))
        val placeResult = placeOrder(PlaceOrderUseCase.Params("e2e1", items))
        assertTrue(placeResult is PlaceOrderUseCase.Result.Success)
        assertEquals(TableStatus.ORDERED, tableRepo.tables["te1"]!!.status)

        val settleResult = settlePayment(SettlePaymentUseCase.Params(makePayment("e2e1", 2000L)))
        assertTrue(settleResult is SettlePaymentUseCase.Result.Success)
        assertEquals(OrderStatus.CLOSED, orderRepo.orders["e2e1"]!!.status)
        assertEquals(TableStatus.DIRTY, tableRepo.tables["te1"]!!.status)
    }
}
