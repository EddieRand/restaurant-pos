package com.restaurantpos.feature.order

import androidx.lifecycle.SavedStateHandle
import com.restaurantpos.core.config.InMemoryConfigRepository
import com.restaurantpos.core.domain.routing.KitchenRouter
import com.restaurantpos.core.domain.usecase.AddComboUseCase
import com.restaurantpos.core.domain.usecase.ApplyDiscountUseCase
import com.restaurantpos.core.domain.usecase.CheckPermissionUseCase
import com.restaurantpos.core.domain.usecase.FireKitchenTicketsUseCase
import com.restaurantpos.core.domain.usecase.PlaceOrderUseCase
import com.restaurantpos.core.domain.usecase.SettlePaymentUseCase
import com.restaurantpos.core.domain.usecase.SplitOrderUseCase
import com.restaurantpos.core.hardware.PrintKitchenTicketUseCase
import com.restaurantpos.core.model.*
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.sync.InMemorySyncOutbox
import com.restaurantpos.core.sync.SyncEntityType
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OrderViewModelTest {

    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var paymentRepo: FakePaymentRepository
    private lateinit var tableRepo: FakeTableRepository
    private lateinit var menuItemRepo: FakeMenuItemRepository
    private lateinit var comboRepo: FakeComboRepository
    private lateinit var ticketRepo: FakeKitchenTicketRepository
    private lateinit var customerRepo: FakeCustomerRepository
    private lateinit var configRepo: InMemoryConfigRepository
    private lateinit var sessionRepo: FakeSessionRepository
    private lateinit var printer: FakePrinterPort
    private lateinit var outbox: InMemorySyncOutbox
    private lateinit var cdsPhaseBroadcaster: CdsPhaseBroadcaster

    private val testOrderId = "order-1"
    private val testUser = User(id = "op-1", displayName = "Cashier", roleId = "cashier", pinHash = "x", createdAt = 0L)

    private fun buildMenuItem(id: String, price: Long = 1000L) = MenuItem(
        id = id,
        names = mapOf("en" to "Item $id"),
        priceMinorUnit = price,
        taxRateId = null,
        categoryId = "cat-1",
    )

    private fun buildViewModel(): OrderViewModel {
        val router = KitchenRouter()
        val savedStateHandle = SavedStateHandle(mapOf("orderId" to testOrderId))
        return OrderViewModel(
            savedStateHandle = savedStateHandle,
            orderRepo = orderRepo,
            menuItemRepo = menuItemRepo,
            placeOrderUseCase = PlaceOrderUseCase(orderRepo, tableRepo, configRepo.current()),
            splitOrderUseCase = SplitOrderUseCase(orderRepo),
            fireKitchenTickets = FireKitchenTicketsUseCase(orderRepo, ticketRepo, router),
            printKitchenTicketUseCase = PrintKitchenTicketUseCase(orderRepo, printer, router),
            configRepo = configRepo,
            sessionRepo = sessionRepo,
            checkPermission = CheckPermissionUseCase(FakeRolePermissionRepository()),
            comboRepo = comboRepo,
            addComboUseCase = AddComboUseCase(comboRepo, menuItemRepo, orderRepo),
            paymentRepo = paymentRepo,
            settlePaymentUseCase = SettlePaymentUseCase(orderRepo, paymentRepo, tableRepo),
            applyDiscountUseCase = ApplyDiscountUseCase(orderRepo),
            cdsPhaseBroadcaster = cdsPhaseBroadcaster,
            customerRepo = customerRepo,
        )
    }

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        orderRepo = FakeOrderRepository()
        paymentRepo = FakePaymentRepository()
        tableRepo = FakeTableRepository()
        menuItemRepo = FakeMenuItemRepository()
        comboRepo = FakeComboRepository()
        ticketRepo = FakeKitchenTicketRepository()
        customerRepo = FakeCustomerRepository()
        configRepo = InMemoryConfigRepository()
        sessionRepo = FakeSessionRepository(testUser)
        printer = FakePrinterPort()
        outbox = InMemorySyncOutbox()
        cdsPhaseBroadcaster = CdsPhaseBroadcaster(SyncWriter(outbox), scope = CoroutineScope(testDispatcher))

        // A standalone QSR order (no table) — DRAFT, no items yet.
        orderRepo.orders[testOrderId] = Order(
            id = testOrderId,
            type = OrderType.DINE_IN,
            sourceTerminalId = "pos-1",
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── QSR vs FSR mode resolution ───────────────────────────────────────────

    @Test
    fun `an order with no tableId is QSR mode (inline payment)`() = runTest {
        // orderRepo's default fixture order has tableId == null (walk-in/counter order).
        val vm = buildViewModel()
        assertTrue(vm.uiState.value.isQsrMode)
    }

    @Test
    fun `an order bound to a table is FSR mode, not QSR`() = runTest {
        // Regression: isQsrMode was declared on OrderUiState but never actually set, so it
        // stayed at its default `true` for every order — including dine-in orders seated via
        // Tables. That meant the Checkout button always opened the QSR inline-payment modal
        // even for table orders, silently bypassing the dedicated FSR Checkout screen and
        // handheld's "no on-device checkout" design. Found via real-device testing.
        orderRepo.orders[testOrderId] = orderRepo.orders[testOrderId]!!.copy(tableId = "table-1")
        val vm = buildViewModel()
        assertFalse(vm.uiState.value.isQsrMode)
    }

    // ── Add item ─────────────────────────────────────────────────────────────

    @Test
    fun `tapping a menu item with no modifiers adds it directly to the cart`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 1200L)
        menuItemRepo.itemsMap[item.id] = item

        vm.onMenuItemTapped(item)

        val state = vm.uiState.value
        assertEquals(1, state.items.size)
        assertEquals(1, state.items.first().quantity)
        assertEquals(1200L, state.subtotalMinorUnit)
    }

    @Test
    fun `tapping the same item twice increments quantity instead of duplicating the line`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 500L)
        menuItemRepo.itemsMap[item.id] = item

        vm.onMenuItemTapped(item)
        vm.onMenuItemTapped(item)

        val state = vm.uiState.value
        assertEquals(1, state.items.size)
        assertEquals(2, state.items.first().quantity)
        assertEquals(1000L, state.subtotalMinorUnit)
    }

    @Test
    fun `first item added broadcasts ORDER phase to the customer display`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1")
        menuItemRepo.itemsMap[item.id] = item

        vm.onMenuItemTapped(item)

        val records = outbox.getPending()
        val cdsRecord = records.firstOrNull { it.entityType == SyncEntityType.CDS_STATE }
        assertTrue("expected a CDS_STATE record to be enqueued", cdsRecord != null)
        assertTrue(cdsRecord!!.payload.contains("\"ORDER\""))
    }

    // ── Quantity / remove ────────────────────────────────────────────────────

    @Test
    fun `decrementing a quantity-1 item removes it from the cart`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 800L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)

        val pendingItem = vm.uiState.value.items.first()
        vm.decrementItem(pendingItem)

        val state = vm.uiState.value
        assertTrue(state.items.none { it.id == pendingItem.id })
        assertEquals(0L, state.subtotalMinorUnit)
    }

    @Test
    fun `incrementing updates both quantity and subtotal`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 300L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)

        val pendingItem = vm.uiState.value.items.first()
        vm.incrementItem(pendingItem)

        val state = vm.uiState.value
        assertEquals(2, state.items.first().quantity)
        assertEquals(600L, state.subtotalMinorUnit)
    }

    // ── Discount ─────────────────────────────────────────────────────────────

    @Test
    fun `applying an order discount reduces the order total and closes the dialog`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 2000L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.showDiscountDialog()
        assertTrue(vm.uiState.value.discountDialogVisible)

        vm.applyOrderDiscount(500L)

        assertFalse(vm.uiState.value.discountDialogVisible)
        assertEquals(500L, orderRepo.orders[testOrderId]!!.discountMinorUnit)
        // Regression: the discount must also be reflected in UiState — the cart's displayed
        // total is computed from UiState fields, NOT re-read from the persisted Order. A
        // discount that only lands in the DB but never reaches UiState.discountMinorUnit is
        // invisible to the cashier (found via real-device regression testing).
        assertEquals(500L, vm.uiState.value.discountMinorUnit)
    }

    @Test
    fun `discount exceeding the subtotal is rejected and surfaced as an error`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 1000L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)

        vm.applyOrderDiscount(5000L)

        assertEquals(0L, orderRepo.orders[testOrderId]!!.discountMinorUnit)
        assertTrue(vm.uiState.value.errorMessage != null)
    }

    // ── Hold order (regression: must NOT trigger Checkout navigation) ─────────

    @Test
    fun `holding an order parks it, clears the cart, and signals orderHeld (not orderPlaced)`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1")
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        assertEquals(1, vm.uiState.value.items.size)

        vm.holdOrder()

        val state = vm.uiState.value
        assertTrue("Hold must set orderHeld", state.orderHeld)
        assertFalse("Hold must NOT set orderPlaced — that would navigate into Checkout", state.orderPlaced)
        assertTrue(orderRepo.orders[testOrderId]!!.orderNotes.contains("[HELD]"))
        assertTrue(state.items.none { it.status == OrderItemStatus.PENDING })
    }

    // ── QSR payment flow ─────────────────────────────────────────────────────

    @Test
    fun `cash payment below the total does not process the payment`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 1000L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.openPaymentModal()
        vm.onCashReceivedInputChanged("5.00") // less than $10.00 total

        vm.confirmCashPayment()

        assertTrue(paymentRepo.payments.isEmpty())
        assertFalse(vm.uiState.value.paymentSuccessVisible)
    }

    @Test
    fun `sufficient cash payment settles the order, fires the kitchen ticket, and shows success`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 1000L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.openPaymentModal()
        vm.setQuickCashAmount(2000L) // overpay -> change

        vm.confirmCashPayment()

        val state = vm.uiState.value
        assertTrue(state.paymentSuccessVisible)
        // Regression: payment success must NOT set orderPlaced, or OrderScreen would
        // immediately navigate to Checkout and the success overlay would never be seen.
        assertFalse(state.orderPlaced)
        assertEquals(1, paymentRepo.payments.size)
        assertEquals(PaymentStatus.PAID, paymentRepo.payments.values.first().status)
        assertEquals(OrderStatus.CLOSED, orderRepo.orders[testOrderId]!!.status)
        assertTrue("expected a kitchen ticket to be fired", ticketRepo.tickets.isNotEmpty())
    }

    @Test
    fun `adding an item to an already-CLOSED order and trying to pay again is rejected, not silently accepted`() = runTest {
        // Regression: after a successful QSR payment the order becomes CLOSED. The screen-level
        // fix is that OrderScreen must navigate to a brand-new order once the success overlay is
        // dismissed (onStartNewOrder) — found via real-device testing, where the cashier got
        // stuck on the same CLOSED order, added another item, and a second checkout attempt
        // surfaced a confusing "Cannot place order in status CLOSED" error instead of either
        // working or being prevented earlier. This test locks in the use case's half of that
        // contract: PlaceOrderUseCase must hard-reject re-firing a CLOSED order rather than
        // silently letting a second payment through.
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 1000L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.openPaymentModal()
        vm.setQuickCashAmount(2000L)
        vm.confirmCashPayment()
        assertEquals(OrderStatus.CLOSED, orderRepo.orders[testOrderId]!!.status)

        // Same screen instance, same (now-CLOSED) orderId — simulates the UI bug where the
        // cashier never navigated away and kept ordering on the stale order. Note the cart
        // total now includes the already-PLACED first item too (recomputeSubtotal sums all
        // non-REFUNDED items), so tender the live total rather than just item2's price.
        val item2 = buildMenuItem("mi-2", price = 500L)
        menuItemRepo.itemsMap[item2.id] = item2
        vm.onMenuItemTapped(item2)
        vm.openPaymentModal()
        vm.setQuickCashAmount(vm.uiState.value.totalMinorUnit)
        vm.confirmCashPayment()

        // Must surface an error — NOT a second silent success.
        assertEquals("a stale CLOSED order must not accept a second payment", 1, paymentRepo.payments.size)
        val error = vm.uiState.value.errorMessage
        assertTrue("expected an error mentioning CLOSED, got: $error", error?.contains("CLOSED") == true)
    }

    @Test
    fun `the amount actually charged includes service charge and subtracts the discount`() = runTest {
        // Regression: totalMinorUnit previously omitted service charge entirely and never
        // subtracted the discount, so the payment modal charged the wrong amount even though
        // the cart UI displayed the (correct) discounted total — found via real-device testing.
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 2000L) // $20.00, 5% service charge by default
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.applyOrderDiscount(220L) // $2.20 discount

        // $20.00 + 5% service charge ($1.00) - $2.20 discount = $18.80
        assertEquals(1880L, vm.uiState.value.totalMinorUnit)

        vm.openPaymentModal()
        vm.setQuickCashAmount(1880L) // exact amount due — must be accepted, not rejected
        vm.confirmCashPayment()

        assertTrue(vm.uiState.value.paymentSuccessVisible)
        assertEquals(1880L, paymentRepo.payments.values.first().amountMinorUnit)
    }

    @Test
    fun `payment success broadcasts SUCCESS to the customer display`() = runTest {
        val vm = buildViewModel()
        val item = buildMenuItem("mi-1", price = 500L)
        menuItemRepo.itemsMap[item.id] = item
        vm.onMenuItemTapped(item)
        vm.openPaymentModal()
        // Deliberately overpay — the total includes service charge on top of the $5.00
        // subtotal, so tendering exactly $5.00 would (correctly) be rejected as insufficient.
        vm.setQuickCashAmount(2000L)

        vm.confirmCashPayment()

        val successRecord = outbox.getPending()
            .filter { it.entityType == SyncEntityType.CDS_STATE }
            .lastOrNull { it.payload.contains("\"SUCCESS\"") }
        assertTrue("expected a SUCCESS CDS broadcast", successRecord != null)
    }

    // ── Walk-in customer selection ───────────────────────────────────────────

    @Test
    fun `selecting a customer updates the chip and closes the dialog`() = runTest {
        val vm = buildViewModel()
        val customer = Customer(id = "c-1", name = "Jane Doe", phone = "555-0100", registeredAt = 0L)
        customerRepo.customers[customer.id] = customer
        vm.showWalkinCustomerDialog()

        vm.selectCustomer(customer)

        val state = vm.uiState.value
        assertEquals("Jane Doe", state.selectedCustomerName)
        assertFalse(state.walkinCustomerDialogVisible)
    }

    @Test
    fun `clearing the selected customer resets the chip to walk-in`() = runTest {
        val vm = buildViewModel()
        val customer = Customer(id = "c-1", name = "Jane Doe", phone = "555-0100", registeredAt = 0L)
        customerRepo.customers[customer.id] = customer
        vm.selectCustomer(customer)
        assertEquals("Jane Doe", vm.uiState.value.selectedCustomerName)

        vm.clearSelectedCustomer()

        assertNull(vm.uiState.value.selectedCustomerName)
    }
}
