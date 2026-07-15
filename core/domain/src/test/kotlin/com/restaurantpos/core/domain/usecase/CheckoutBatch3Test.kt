package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Batch 3 checkout use-case tests:
 * - SplitBillUseCase: even split conservation, by-item split conservation
 * - ApplyDiscountUseCase: order-level, item-level
 * - ApplyServiceChargeUseCase: sets charge
 * - RefundUseCase: partial refund, full refund reopens order, over-refund blocked
 * - ShiftReportUseCase: gross/net/method breakdown
 */
class CheckoutBatch3Test {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var paymentRepo: FakePaymentRepository
    private lateinit var reportRepo: ShiftReportTest.FakeReportRepository

    @Before
    fun setup() {
        orderRepo = FakeOrderRepository()
        paymentRepo = FakePaymentRepository()
        reportRepo = ShiftReportTest.FakeReportRepository()
    }

    // ── SplitBill ────────────────────────────────────────────────────────────

    @Test
    fun `even split - shares sum to total`() = runBlocking {
        val order = makeOrder(subtotal = 3000L, total = 3000L)
        orderRepo.orders[order.id] = order

        val result = SplitBillUseCase(orderRepo)("order-1", SplitBillUseCase.Strategy.Even(3))
        assertTrue(result is SplitBillUseCase.Result.Success)
        val shares = (result as SplitBillUseCase.Result.Success).shares
        assertEquals(3000L, shares.sumOf { it.amountMinorUnit })
        assertEquals(3, shares.size)
    }

    @Test
    fun `even split - rounding remainder goes to last party`() = runBlocking {
        val order = makeOrder(subtotal = 1000L, total = 1000L)
        orderRepo.orders[order.id] = order

        val result = SplitBillUseCase(orderRepo)("order-1", SplitBillUseCase.Strategy.Even(3))
        val shares = (result as SplitBillUseCase.Result.Success).shares
        // 1000 / 3 = 333, 333, 334
        assertEquals(333L, shares[0].amountMinorUnit)
        assertEquals(333L, shares[1].amountMinorUnit)
        assertEquals(334L, shares[2].amountMinorUnit)
        assertEquals(1000L, shares.sumOf { it.amountMinorUnit })
    }

    @Test
    fun `by-item split - shares sum to total`() = runBlocking {
        val item1 = makeItem("order-1", 600L)
        val item2 = makeItem("order-1", 400L)
        orderRepo.items[item1.id] = item1
        orderRepo.items[item2.id] = item2
        val order = makeOrder(subtotal = 1000L, total = 1000L)
        orderRepo.orders[order.id] = order

        val result = SplitBillUseCase(orderRepo)(
            "order-1",
            SplitBillUseCase.Strategy.ByItem(listOf(setOf(item1.id), setOf(item2.id))),
        )
        assertTrue(result is SplitBillUseCase.Result.Success)
        val shares = (result as SplitBillUseCase.Result.Success).shares
        assertEquals(1000L, shares.sumOf { it.amountMinorUnit })
    }

    // ── Discount ─────────────────────────────────────────────────────────────

    @Test
    fun `order-level discount reduces totalMinorUnit`() = runBlocking {
        val order = makeOrder(subtotal = 1000L, total = 1000L)
        orderRepo.orders[order.id] = order

        val result = ApplyDiscountUseCase(orderRepo)("order-1", ApplyDiscountUseCase.Target.OrderLevel(100L))
        assertTrue(result is ApplyDiscountUseCase.Result.Success)
        val updated = (result as ApplyDiscountUseCase.Result.Success).order
        assertEquals(100L, updated.discountMinorUnit)
        assertEquals(900L, updated.totalMinorUnit)
    }

    @Test
    fun `discount exceeding subtotal is rejected`() = runBlocking {
        val order = makeOrder(subtotal = 500L, total = 500L)
        orderRepo.orders[order.id] = order

        val result = ApplyDiscountUseCase(orderRepo)("order-1", ApplyDiscountUseCase.Target.OrderLevel(600L))
        assertTrue(result is ApplyDiscountUseCase.Result.Error)
    }

    // ── ServiceCharge ─────────────────────────────────────────────────────────

    @Test
    fun `service charge is added to order total`() = runBlocking {
        val order = makeOrder(subtotal = 1000L, total = 1000L)
        orderRepo.orders[order.id] = order

        val result = ApplyServiceChargeUseCase(orderRepo)("order-1", 100L)
        assertTrue(result is ApplyServiceChargeUseCase.Result.Success)
        val updated = (result as ApplyServiceChargeUseCase.Result.Success).order
        assertEquals(100L, updated.serviceChargeMinorUnit)
        assertEquals(1100L, updated.totalMinorUnit)
    }

    @Test
    fun `service charge negative rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ApplyServiceChargeUseCase(orderRepo)("order-1", -1L) }
        }
    }

    // ── Refund ────────────────────────────────────────────────────────────────

    @Test
    fun `partial refund - order stays CLOSED`() = runBlocking {
        val order = makeClosedOrder(total = 1000L)
        orderRepo.orders[order.id] = order
        val payment = makePaidPayment(order.id, 1000L)
        paymentRepo.payments[payment.id] = payment

        val result = RefundUseCase(orderRepo, paymentRepo)(
            RefundUseCase.Params(
                originalPaymentId = payment.id,
                refundAmountMinorUnit = 300L,
                operatorId = "cashier-1",
            )
        )
        assertTrue(result is RefundUseCase.Result.Success)
        assertEquals(OrderStatus.CLOSED, orderRepo.orders[order.id]!!.status)
        // refund payment saved
        val refund = paymentRepo.payments.values.find { it.refundedPaymentId == payment.id }
        assertNotNull(refund)
        assertEquals(300L, refund!!.amountMinorUnit)
    }

    @Test
    fun `full refund - order reopened to PLACED`() = runBlocking {
        val order = makeClosedOrder(total = 1000L)
        orderRepo.orders[order.id] = order
        val payment = makePaidPayment(order.id, 1000L)
        paymentRepo.payments[payment.id] = payment

        val result = RefundUseCase(orderRepo, paymentRepo)(
            RefundUseCase.Params(
                originalPaymentId = payment.id,
                refundAmountMinorUnit = 1000L,
                operatorId = "cashier-1",
            )
        )
        assertTrue(result is RefundUseCase.Result.Success)
        assertEquals(OrderStatus.PLACED, orderRepo.orders[order.id]!!.status)
    }

    @Test
    fun `over-refund is blocked`() = runBlocking {
        val order = makeClosedOrder(total = 500L)
        orderRepo.orders[order.id] = order
        val payment = makePaidPayment(order.id, 500L)
        paymentRepo.payments[payment.id] = payment

        val result = RefundUseCase(orderRepo, paymentRepo)(
            RefundUseCase.Params(
                originalPaymentId = payment.id,
                refundAmountMinorUnit = 600L,
                operatorId = "cashier-1",
            )
        )
        assertTrue(result is RefundUseCase.Result.Error)
    }

    // ── ShiftReport ────────────────────────────────────────────────────────────

    @Test
    fun `shift report - net revenue = gross - refunds`() = runBlocking {
        val from = 0L
        val to = 100_000L
        val order1 = makeClosedOrder(total = 1000L, updatedAt = 50_000L)
        val order2 = makeClosedOrder(total = 500L, id = "order-2", updatedAt = 60_000L)
        orderRepo.orders[order1.id] = order1
        orderRepo.orders[order2.id] = order2
        orderRepo.closedRange = listOf(order1, order2)

        val p1 = makePaidPayment(order1.id, 1000L, createdAt = 50_000L)
        val p2 = makePaidPayment(order2.id, 500L, createdAt = 60_000L, id = "p2")
        val refund = Payment(
            id = "r1", orderId = order1.id, amountMinorUnit = 200L,
            method = PaymentMethod.CASH, status = PaymentStatus.REFUNDED,
            operatorId = "c1", refundedPaymentId = p1.id, createdAt = 70_000L,
        )
        paymentRepo.payments[p1.id] = p1
        paymentRepo.payments[p2.id] = p2
        paymentRepo.payments[refund.id] = refund

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(from, to)
        assertEquals(1500L, report.grossRevenueMinorUnit)
        assertEquals(200L, report.totalRefundsMinorUnit)
        assertEquals(1300L, report.netRevenueMinorUnit)
        assertEquals(2, report.orderCount)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private var itemCounter = 0
    private fun makeItem(orderId: String, price: Long) = OrderItem(
        id = "item-${itemCounter++}", orderId = orderId, menuItemId = "menu-1",
        menuItemNameSnapshot = mapOf("en" to "Item"),
        quantity = 1, unitPriceMinorUnit = price, taxRateId = null,
    )

    private fun makeOrder(
        id: String = "order-1",
        subtotal: Long = 0L,
        total: Long = 0L,
    ) = Order(
        id = id, sourceTerminalId = "t1",
        subtotalMinorUnit = subtotal,
        taxTotalMinorUnit = 0L,
        serviceChargeMinorUnit = 0L,
        discountMinorUnit = maxOf(0L, subtotal - total),
        status = OrderStatus.PLACED,
        createdAt = 0L, updatedAt = 0L,
    )

    private fun makeClosedOrder(total: Long, id: String = "order-1", updatedAt: Long = 0L) = Order(
        id = id, sourceTerminalId = "t1",
        subtotalMinorUnit = total, taxTotalMinorUnit = 0L,
        serviceChargeMinorUnit = 0L, discountMinorUnit = 0L,
        status = OrderStatus.CLOSED, createdAt = 0L, updatedAt = updatedAt,
    )

    private fun makePaidPayment(
        orderId: String,
        amount: Long,
        id: String = "payment-1",
        createdAt: Long = 0L,
    ) = Payment(
        id = id, orderId = orderId, amountMinorUnit = amount,
        method = PaymentMethod.CASH, status = PaymentStatus.PAID,
        operatorId = "cashier-1", createdAt = createdAt,
    )
}
