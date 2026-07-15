package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.model.*
import com.restaurantpos.core.model.DailySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ShiftReportTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var paymentRepo: FakePaymentRepository
    private lateinit var reportRepo: FakeReportRepository

    private val T0 = 1_000_000L
    private val T1 = 2_000_000L

    @Before fun setup() {
        orderRepo = FakeOrderRepository()
        paymentRepo = FakePaymentRepository()
        reportRepo = FakeReportRepository()
    }

    private fun closedOrder(id: String, at: Long = T0 + 1) = Order(
        id = id,
        type = OrderType.DINE_IN,
        status = OrderStatus.CLOSED,
        sourceTerminalId = "t1",
        createdAt = at,
        updatedAt = at,
    )

    private fun payment(id: String, orderId: String, amount: Long, method: PaymentMethod = PaymentMethod.CASH, status: PaymentStatus = PaymentStatus.PAID, at: Long = T0 + 1) =
        Payment(id = id, orderId = orderId, amountMinorUnit = amount, method = method, status = status, operatorId = "op1", createdAt = at)

    private fun item(id: String, orderId: String, menuItemId: String, name: String, qty: Int, price: Long, status: OrderItemStatus = OrderItemStatus.SERVED) =
        OrderItem(
            id = id, orderId = orderId, menuItemId = menuItemId,
            menuItemNameSnapshot = mapOf("en" to name),
            quantity = qty, unitPriceMinorUnit = price,
            taxRateId = null, status = status,
        )

    @Test fun `gross and net calculated correctly`() = runBlocking {
        val order = closedOrder("o1")
        orderRepo.closedRange = listOf(order)
        paymentRepo.payments["p1"] = payment("p1", "o1", 3000L, PaymentMethod.CASH, PaymentStatus.PAID)
        paymentRepo.payments["p2"] = payment("p2", "o1", 500L, PaymentMethod.CASH, PaymentStatus.REFUNDED)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)

        assertEquals(3000L, report.grossRevenueMinorUnit)
        assertEquals(500L, report.totalRefundsMinorUnit)
        assertEquals(2500L, report.netRevenueMinorUnit)
        assertEquals(1, report.orderCount)
    }

    internal class FakeReportRepository : ReportRepository {
        val snapshots = mutableMapOf<String, DailySnapshot>()
        override suspend fun getDailySnapshots(startDate: String, endDate: String): List<DailySnapshot> =
            snapshots.values.filter { it.date >= startDate && it.date <= endDate }
        override suspend fun saveDailySnapshot(snapshot: DailySnapshot) { snapshots[snapshot.date] = snapshot }
        override suspend fun saveDailySnapshots(snapshots: List<DailySnapshot>) { snapshots.forEach { this.snapshots[it.date] = it } }
        override suspend fun getDailySnapshot(date: String): DailySnapshot? = snapshots[date]
        override suspend fun deleteSnapshots(startDate: String, endDate: String) {
            snapshots.keys.filter { it >= startDate && it <= endDate }.forEach { snapshots.remove(it) }
        }
    }

    @Test fun `byMethod breakdown is correct`() = runBlocking {
        val order = closedOrder("o1")
        orderRepo.closedRange = listOf(order)
        paymentRepo.payments["p1"] = payment("p1", "o1", 2000L, PaymentMethod.CASH, PaymentStatus.PAID)
        paymentRepo.payments["p2"] = payment("p2", "o1", 1000L, PaymentMethod.CARD, PaymentStatus.PAID)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)

        assertEquals(2, report.byMethod.size)
        val cash = report.byMethod.first { it.method == PaymentMethod.CASH }
        assertEquals(2000L, cash.grossMinorUnit)
        val card = report.byMethod.first { it.method == PaymentMethod.CARD }
        assertEquals(1000L, card.grossMinorUnit)
    }

    @Test fun `top items sorted by revenue descending`() = runBlocking {
        val order = closedOrder("o1")
        orderRepo.closedRange = listOf(order)
        paymentRepo.payments["p1"] = payment("p1", "o1", 5000L, PaymentMethod.CASH, PaymentStatus.PAID)

        orderRepo.items["i1"] = item("i1", "o1", "mi-burger", "Burger", qty = 2, price = 1200L)
        orderRepo.items["i2"] = item("i2", "o1", "mi-salmon", "Salmon", qty = 1, price = 3200L)
        orderRepo.items["i3"] = item("i3", "o1", "mi-cola", "Cola", qty = 3, price = 250L)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)

        assertEquals(3, report.topItems.size)
        assertEquals("Salmon", report.topItems[0].name)   // 3200 * 1 = 3200
        assertEquals("Burger", report.topItems[1].name)   // 1200 * 2 = 2400
        assertEquals("Cola", report.topItems[2].name)     // 250 * 3 = 750
    }

    @Test fun `refunded items excluded from top items revenue`() = runBlocking {
        val order = closedOrder("o1")
        orderRepo.closedRange = listOf(order)
        paymentRepo.payments["p1"] = payment("p1", "o1", 1000L, PaymentMethod.CASH, PaymentStatus.PAID)

        orderRepo.items["i1"] = item("i1", "o1", "mi-a", "ItemA", qty = 1, price = 1000L, status = OrderItemStatus.SERVED)
        orderRepo.items["i2"] = item("i2", "o1", "mi-b", "ItemB", qty = 1, price = 5000L, status = OrderItemStatus.REFUNDED)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)

        assertEquals(1, report.topItems.size)
        assertEquals("ItemA", report.topItems[0].name)
    }

    @Test fun `topItemsLimit respected`() = runBlocking {
        val order = closedOrder("o1")
        orderRepo.closedRange = listOf(order)
        paymentRepo.payments["p1"] = payment("p1", "o1", 10000L, PaymentMethod.CASH, PaymentStatus.PAID)

        (1..8).forEach { n ->
            orderRepo.items["i$n"] = item("i$n", "o1", "mi-$n", "Item$n", qty = 1, price = (n * 100).toLong())
        }

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1, topItemsLimit = 3)
        assertEquals(3, report.topItems.size)
    }

    @Test fun `require throws for reversed range`() {
        runBlocking {
            try {
                ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T1, T0)
                fail("Should have thrown")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("toEpoch"))
            }
        }
    }

    // ── Enhanced metrics ──────────────────────────────────────────────────────

    private fun closedOrderWithExtras(
        id: String,
        discount: Long = 0L,
        tip: Long = 0L,
        serviceCharge: Long = 0L,
        tax: Long = 0L,
        guests: Int = 2,
        at: Long = T0 + 1,
    ) = Order(
        id = id,
        type = OrderType.DINE_IN,
        status = OrderStatus.CLOSED,
        sourceTerminalId = "t1",
        discountMinorUnit = discount,
        tipMinorUnit = tip,
        serviceChargeMinorUnit = serviceCharge,
        taxTotalMinorUnit = tax,
        guestCount = guests,
        createdAt = at,
        updatedAt = at,
    )

    @Test fun `totalDiscountMinorUnit aggregated across orders`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", discount = 200L)
        val o2 = closedOrderWithExtras("o2", discount = 150L)
        orderRepo.closedRange = listOf(o1, o2)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(350L, report.totalDiscountMinorUnit)
    }

    @Test fun `totalTipMinorUnit aggregated across orders`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", tip = 100L)
        val o2 = closedOrderWithExtras("o2", tip = 80L)
        orderRepo.closedRange = listOf(o1, o2)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(180L, report.totalTipMinorUnit)
    }

    @Test fun `totalServiceChargeMinorUnit aggregated`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", serviceCharge = 300L)
        orderRepo.closedRange = listOf(o1)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(300L, report.totalServiceChargeMinorUnit)
    }

    @Test fun `totalTaxMinorUnit aggregated`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", tax = 120L)
        val o2 = closedOrderWithExtras("o2", tax = 80L)
        orderRepo.closedRange = listOf(o1, o2)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(200L, report.totalTaxMinorUnit)
    }

    @Test fun `totalGuestCount summed from orders`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", guests = 3)
        val o2 = closedOrderWithExtras("o2", guests = 5)
        orderRepo.closedRange = listOf(o1, o2)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(8, report.totalGuestCount)
    }

    @Test fun `averageOrderValue is net divided by orderCount`() = runBlocking {
        val o1 = closedOrderWithExtras("o1")
        val o2 = closedOrderWithExtras("o2")
        orderRepo.closedRange = listOf(o1, o2)
        paymentRepo.payments["p1"] = payment("p1", "o1", 2000L)
        paymentRepo.payments["p2"] = payment("p2", "o2", 3000L)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(2, report.orderCount)
        assertEquals(5000L, report.netRevenueMinorUnit)
        assertEquals(2500L, report.averageOrderValueMinorUnit)
    }

    @Test fun `averageSpendPerGuest is net divided by guestCount`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", guests = 4)
        orderRepo.closedRange = listOf(o1)
        paymentRepo.payments["p1"] = payment("p1", "o1", 4000L)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(4, report.totalGuestCount)
        assertEquals(1000L, report.averageSpendPerGuestMinorUnit)
    }

    @Test fun `averageOrderValue is 0 when no orders`() = runBlocking {
        orderRepo.closedRange = emptyList()

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(0L, report.averageOrderValueMinorUnit)
    }

    @Test fun `averageSpendPerGuest is 0 when no guests`() = runBlocking {
        val o1 = closedOrderWithExtras("o1", guests = 0)
        orderRepo.closedRange = listOf(o1)

        val report = ShiftReportUseCase(orderRepo, paymentRepo, reportRepo)(T0, T1)
        assertEquals(0L, report.averageSpendPerGuestMinorUnit)
    }
}
