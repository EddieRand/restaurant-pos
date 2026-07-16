package com.restaurantpos.feature.checkout

import androidx.lifecycle.SavedStateHandle
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.CouponRepository
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.domain.repository.GiftCardRepository
import com.restaurantpos.core.domain.repository.GroupBuyingVoucherRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.RolePermissionRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.usecase.ApplyCouponUseCase
import com.restaurantpos.core.domain.usecase.ApplyDiscountUseCase
import com.restaurantpos.core.domain.usecase.ApplyServiceChargeUseCase
import com.restaurantpos.core.domain.usecase.CheckPermissionUseCase
import com.restaurantpos.core.domain.usecase.RefundUseCase
import com.restaurantpos.core.domain.usecase.SettlePaymentUseCase
import com.restaurantpos.core.domain.usecase.SplitBillUseCase
import com.restaurantpos.core.hardware.PrintReceiptUseCase
import com.restaurantpos.core.hardware.PrintResult
import com.restaurantpos.core.hardware.PrinterPort
import com.restaurantpos.core.hardware.ReceiptData
import com.restaurantpos.core.hardware.KitchenTicketData
import com.restaurantpos.core.model.*
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.sync.InMemorySyncOutbox
import com.restaurantpos.core.sync.NetworkMonitor
import com.restaurantpos.core.sync.SyncWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CheckoutViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val orderId = "order-1"

    private fun baseOrder() = Order(
        id = orderId,
        sourceTerminalId = "term-1",
        subtotalMinorUnit = 1000L,
        taxTotalMinorUnit = 0L,
        status = OrderStatus.PLACED,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private class FakeOrderRepo(initial: Order) : OrderRepository {
        val store = mutableMapOf(initial.id to initial)
        override fun observeActive(): Flow<List<Order>> = flowOf(store.values.toList())
        override suspend fun getById(id: String) = store[id]
        override suspend fun getActiveByTable(tableId: String): Order? = null
        override suspend fun save(order: Order) { store[order.id] = order }
        override suspend fun saveItems(items: List<OrderItem>) {}
        override suspend fun getItemsByOrder(orderId: String): List<OrderItem> = emptyList()
        override suspend fun updateStatus(id: String, status: OrderStatus) {
            store[id]?.let { store[id] = it.copy(status = status) }
        }
        override suspend fun updateTotals(id: String, subtotal: Long, taxTotal: Long) {}
        override suspend fun setTip(id: String, tipMinorUnit: Long) {}
        override suspend fun getClosedInRange(fromEpoch: Long, toEpoch: Long): List<Order> = emptyList()
        override suspend fun searchOrders(query: String, fromEpoch: Long?, toEpoch: Long?, status: OrderStatus?): List<Order> = emptyList()
        override fun observeReadyForPickup(): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun updateFulfillmentStatus(id: String, status: OrderFulfillmentStatus) {
            store[id]?.let { store[id] = it.copy(fulfillmentStatus = status) }
        }
        override suspend fun countPickupCodesSince(sinceEpoch: Long): Int = 0
        override suspend fun applyRemote(orders: List<Order>, items: List<OrderItem>) {}
        override suspend fun setPickupCode(id: String, pickupCode: String) {
            store[id]?.let { store[id] = it.copy(pickupCode = pickupCode) }
        }
    }

    private class FakePaymentRepo : PaymentRepository {
        val store = mutableMapOf<String, Payment>()
        override fun observeByOrder(orderId: String): Flow<List<Payment>> = flowOf(store.values.toList())
        override suspend fun getByOrder(orderId: String): List<Payment> = store.values.filter { it.orderId == orderId }
        override suspend fun getById(id: String) = store[id]
        override suspend fun save(payment: Payment) { store[payment.id] = payment }
        override suspend fun updateStatus(id: String, status: PaymentStatus) {
            store[id]?.let { store[id] = it.copy(status = status) }
        }
    }

    private class FakeTableRepo : TableRepository {
        override fun observeAll(): Flow<List<Table>> = flowOf(emptyList())
        override suspend fun getById(id: String): Table? = null
        override suspend fun save(table: Table) {}
        override suspend fun updateStatusAndOrder(id: String, status: TableStatus, orderId: String?) {}
        override suspend fun applyRemote(tables: List<Table>) {}
    }

    private class FakeSessionRepo : SessionRepository {
        override val currentUser: Flow<User?> = flowOf(null)
        override fun current(): User? = null
        override fun login(user: User) {}
        override fun logout() {}
    }

    private class FakeConfigRepo : ConfigRepository {
        override val config: Flow<RegionConfig> = flowOf(DefaultRegionConfig)
        override fun current() = DefaultRegionConfig
        override suspend fun update(config: RegionConfig) {}
    }

    private class FakeRolePermissionRepo : RolePermissionRepository {
        override suspend fun getAllRoles() = emptyList<Role>()
        override suspend fun getPermissionKeys(roleId: String) = emptyList<String>()
        override suspend fun hasPermission(roleId: String, permissionKey: PermissionKey) = true
        override suspend fun replacePermissions(roleId: String, permissionKeys: List<String>) {}
        override suspend fun getAllMappings() = emptyList<RolePermission>()
    }

    private class FakeCouponRepo : CouponRepository {
        override suspend fun getByCode(code: String): Coupon? = null
        override suspend fun save(coupon: Coupon) {}
    }

    private class FakeCustomerRepo : CustomerRepository {
        override fun observeAll(): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun search(query: String) = emptyList<Customer>()
        override suspend fun getById(id: String): Customer? = null
        override suspend fun getByPhone(phone: String): Customer? = null
        override suspend fun save(customer: Customer) {}
        override suspend fun addPoints(customerId: String, points: Long, orderId: String?, description: String) {}
        override suspend fun getTransactions(customerId: String) = emptyList<LoyaltyTransaction>()
        override suspend fun applyRemote(customers: List<Customer>) {}
    }

    private class FakePrinterPort : PrinterPort {
        override suspend fun printReceipt(data: ReceiptData): PrintResult = PrintResult.Success
        override suspend fun printKitchenTicket(data: KitchenTicketData): PrintResult = PrintResult.Success
        override suspend fun isConnected(): Boolean = true
    }

    private class FakeGiftCardRepo : GiftCardRepository {
        var redeemCalled = false
        override suspend fun redeem(code: String, amountMinorUnit: Long, orderId: String?, operatorId: String): GiftCardRepository.RedeemResult {
            redeemCalled = true
            return GiftCardRepository.RedeemResult.Success(0L)
        }
    }

    private class FakeGroupBuyingVoucherRepo : GroupBuyingVoucherRepository {
        var validateCalled = false
        var redeemCalled = false
        override suspend fun validate(
            provider: GroupBuyingVoucherRepository.Provider,
            code: String,
        ): GroupBuyingVoucherRepository.ValidateResult {
            validateCalled = true
            return GroupBuyingVoucherRepository.ValidateResult.Success(
                GroupBuyingVoucherRepository.Voucher(provider, "****1001", "测试团购券", 800L, Long.MAX_VALUE, "AVAILABLE", true)
            )
        }

        override suspend fun redeem(
            provider: GroupBuyingVoucherRepository.Provider,
            code: String,
            orderId: String,
            operatorId: String,
            requestedAmountMinorUnit: Long,
            idempotencyKey: String,
        ): GroupBuyingVoucherRepository.RedeemResult {
            redeemCalled = true
            return GroupBuyingVoucherRepository.RedeemResult.Success("redeem-1", requestedAmountMinorUnit, false)
        }
    }

    private class FakeNetworkMonitor(initial: Boolean) : NetworkMonitor {
        private val flow = MutableStateFlow(initial)
        override val isOnline: Flow<Boolean> = flow
        fun set(value: Boolean) { flow.value = value }
    }

    private fun buildViewModel(
        order: Order = baseOrder(),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(true),
        giftCardRepo: FakeGiftCardRepo = FakeGiftCardRepo(),
        groupBuyingVoucherRepo: FakeGroupBuyingVoucherRepo = FakeGroupBuyingVoucherRepo(),
    ): Triple<CheckoutViewModel, FakePaymentRepo, FakeGiftCardRepo> {
        val orderRepo = FakeOrderRepo(order)
        val paymentRepo = FakePaymentRepo()
        val tableRepo = FakeTableRepo()
        val configRepo = FakeConfigRepo()
        val sessionRepo = FakeSessionRepo()
        val checkPermission = CheckPermissionUseCase(FakeRolePermissionRepo())
        val vm = CheckoutViewModel(
            savedStateHandle = SavedStateHandle(mapOf("orderId" to order.id)),
            orderRepo = orderRepo,
            paymentRepo = paymentRepo,
            settlePaymentUseCase = SettlePaymentUseCase(orderRepo, paymentRepo, tableRepo),
            applyDiscountUseCase = ApplyDiscountUseCase(orderRepo),
            applyServiceChargeUseCase = ApplyServiceChargeUseCase(orderRepo),
            splitBillUseCase = SplitBillUseCase(orderRepo),
            refundUseCase = RefundUseCase(orderRepo, paymentRepo),
            configRepo = configRepo,
            sessionRepo = sessionRepo,
            checkPermission = checkPermission,
            applyCouponUseCase = ApplyCouponUseCase(FakeCouponRepo(), orderRepo),
            customerRepo = FakeCustomerRepo(),
            printReceiptUseCase = PrintReceiptUseCase(orderRepo, FakePrinterPort(), DefaultRegionConfig),
            giftCardRepo = giftCardRepo,
            groupBuyingVoucherRepo = groupBuyingVoucherRepo,
            networkMonitor = networkMonitor,
            cdsPhaseBroadcaster = CdsPhaseBroadcaster(SyncWriter(InMemorySyncOutbox())),
        )
        return Triple(vm, paymentRepo, giftCardRepo)
    }

    @Test
    fun `payWithGiftCard does nothing and sets error when offline`() = runTest {
        val (vm, _, giftCardRepo) = buildViewModel(networkMonitor = FakeNetworkMonitor(false))
        vm.setGiftCardCode("GC-1")

        vm.payWithGiftCard(500L)

        assertFalse(giftCardRepo.redeemCalled)
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSettled)
    }

    @Test
    fun `isOnline reflects NetworkMonitor updates`() = runTest {
        val monitor = FakeNetworkMonitor(true)
        val (vm, _, _) = buildViewModel(networkMonitor = monitor)

        assertTrue(vm.uiState.value.isOnline)

        monitor.set(false)
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun `cash payment succeeds while offline`() = runTest {
        val (vm, paymentRepo, _) = buildViewModel(
            order = baseOrder().copy(subtotalMinorUnit = 500L),
            networkMonitor = FakeNetworkMonitor(false),
        )

        vm.payWithMethod(PaymentMethod.CASH)

        assertTrue(vm.uiState.value.isSettled)
        assertEquals(1, paymentRepo.store.values.count { it.status == PaymentStatus.PAID })
    }

    @Test
    fun `group buying voucher must validate before redeem and records voucher payment`() = runTest {
        val voucherRepo = FakeGroupBuyingVoucherRepo()
        val (vm, paymentRepo, _) = buildViewModel(
            order = baseOrder().copy(subtotalMinorUnit = 800L),
            groupBuyingVoucherRepo = voucherRepo,
        )
        vm.setGroupBuyingCode("DY-DEMO-1001")

        vm.validateGroupBuyingVoucher()
        assertTrue(voucherRepo.validateCalled)
        assertNotNull(vm.uiState.value.validatedGroupBuyingVoucher)

        vm.redeemGroupBuyingVoucher()
        assertTrue(voucherRepo.redeemCalled)
        assertTrue(vm.uiState.value.isSettled)
        assertEquals(PaymentMethod.VOUCHER, paymentRepo.store.values.single().method)
        assertEquals("", vm.uiState.value.groupBuyingCode)
        assertNull(vm.uiState.value.validatedGroupBuyingVoucher)
        assertEquals("", vm.uiState.value.groupBuyingIdempotencyKey)
    }

    @Test
    fun `checkout steps advance tip - payment - settle - receipt - finish`() = runTest {
        val (vm, _, _) = buildViewModel(order = baseOrder().copy(subtotalMinorUnit = 500L))

        // Tipping enabled + fresh order opens on the tip step.
        assertEquals(CheckoutStep.TIP, vm.uiState.value.step)

        vm.proceedToPayment()
        assertEquals(CheckoutStep.PAYMENT, vm.uiState.value.step)

        vm.payWithMethod(PaymentMethod.CASH)
        assertTrue(vm.uiState.value.isSettled)
        assertFalse(vm.uiState.value.finished)

        vm.showReceiptStep()
        assertEquals(CheckoutStep.RECEIPT, vm.uiState.value.step)

        vm.finishCheckout()
        assertTrue(vm.uiState.value.finished)
    }
}
