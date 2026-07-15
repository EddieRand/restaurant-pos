package com.restaurantpos.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.ComboRepository
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.usecase.AddComboUseCase
import com.restaurantpos.core.domain.usecase.ApplyDiscountUseCase
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.model.CdsPhase
import com.restaurantpos.core.domain.usecase.CheckPermissionUseCase
import com.restaurantpos.core.domain.usecase.FireKitchenTicketsUseCase
import com.restaurantpos.core.domain.usecase.PlaceOrderUseCase
import com.restaurantpos.core.domain.usecase.SettlePaymentUseCase
import com.restaurantpos.core.hardware.PrintKitchenTicketUseCase
import com.restaurantpos.core.domain.usecase.SplitOrderUseCase
import com.restaurantpos.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class QsrPaymentStep { SELECT_METHOD, CASH_INPUT, PROCESSING }

data class OrderUiState(
    val orderId: String = "",
    val regionConfig: RegionConfig = DefaultRegionConfig,
    val orderType: OrderType = OrderType.DINE_IN,
    val items: List<OrderItem> = emptyList(),
    val menuItems: List<MenuItem> = emptyList(),
    val combos: List<com.restaurantpos.core.model.Combo> = emptyList(),
    val subtotalMinorUnit: Long = 0L,
    val taxTotalMinorUnit: Long = 0L,
    /** Order-level discount applied via the Discount dialog — subtracted from the displayed total. */
    val discountMinorUnit: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val orderPlaced: Boolean = false,
    /** True once an order has been parked via Hold — distinct from [orderPlaced] so the
     *  screen navigates "away to take the next order" instead of into Checkout. */
    val orderHeld: Boolean = false,
    val orderStatus: OrderStatus = OrderStatus.DRAFT,
    /** Non-null when the user tapped a menu item that has modifierGroups. */
    val pendingModifierItem: MenuItem? = null,
    /** Modifier groups for the pending item, loaded from DB. */
    val pendingModifierGroups: List<ModifierGroup> = emptyList(),
    /** Currently selected modifier ids keyed by groupId. */
    val modifierSelections: Map<String, Set<String>> = emptyMap(),
    /** Non-null while the split-order dialog is showing. */
    val splitDialogVisible: Boolean = false,
    val splitSelectedItemIds: Set<String> = emptySet(),
    val permissionDeniedAction: String? = null,
    /** Item being edited for a note (long-press flow). */
    val noteEditingItemId: String? = null,
    /** Order-level note (allergy, birthday, rush, etc.). */
    val orderNotes: String = "",
    /** True while order-notes dialog is open. */
    val orderNotesDialogVisible: Boolean = false,
    // ── QSR mode ────────────────────────────────────────────────────────────
    val isQsrMode: Boolean = true,
    val pickupNumber: String = "",
    val paymentModalVisible: Boolean = false,
    val paymentStep: QsrPaymentStep = QsrPaymentStep.SELECT_METHOD,
    val activePaymentMethod: PaymentMethod? = null,
    val cashReceivedInput: String = "",
    val cashReceivedMinorUnit: Long = 0L,
    val paymentSuccessVisible: Boolean = false,
    val completedOrderNumber: String = "",
    // ── Discount / Hold / Search ────────────────────────────────────────────
    val discountDialogVisible: Boolean = false,
    val searchQuery: String = "",
    val walkinCustomerDialogVisible: Boolean = false,
    val customers: List<com.restaurantpos.core.model.Customer> = emptyList(),
    val customerSearchQuery: String = "",
    val selectedCustomerName: String? = null,
) {
    /** Service charge preview, derived from RegionConfig — same formula the cart UI shows. */
    val serviceChargeMinorUnit: Long
        get() {
            val rate = regionConfig.serviceChargeRatePermille
            return if (rate > 0) subtotalMinorUnit * rate / 1000 else 0L
        }

    /**
     * The real amount due — subtotal + tax + service charge − discount. This MUST be the one
     * and only place this formula lives: it drives both the cart's displayed total and the
     * amount actually charged/settled in the payment modal. They previously diverged (this
     * field omitted service charge entirely and never subtracted the discount), so a discount
     * applied in the cart was invisible to the cashier when collecting payment — found via
     * real-device regression testing.
     */
    val totalMinorUnit: Long
        get() = (subtotalMinorUnit + taxTotalMinorUnit + serviceChargeMinorUnit - discountMinorUnit).coerceAtLeast(0L)

    val isPlaced: Boolean get() = orderStatus == OrderStatus.PLACED || orderStatus == OrderStatus.READY
}

@HiltViewModel
class OrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepo: OrderRepository,
    private val menuItemRepo: MenuItemRepository,
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val splitOrderUseCase: SplitOrderUseCase,
    private val fireKitchenTickets: FireKitchenTicketsUseCase,
    private val printKitchenTicketUseCase: PrintKitchenTicketUseCase,
    private val configRepo: ConfigRepository,
    private val sessionRepo: SessionRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val comboRepo: ComboRepository,
    private val addComboUseCase: AddComboUseCase,
    private val paymentRepo: PaymentRepository,
    private val settlePaymentUseCase: SettlePaymentUseCase,
    private val applyDiscountUseCase: ApplyDiscountUseCase,
    private val cdsPhaseBroadcaster: CdsPhaseBroadcaster,
    private val customerRepo: CustomerRepository,
) : ViewModel() {

    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private fun broadcastCds(phase: CdsPhase) {
        cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, phase, orderId)
    }

    private val _uiState = MutableStateFlow(OrderUiState(orderId = orderId))
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepo.config.collect { cfg ->
                _uiState.update { it.copy(regionConfig = cfg) }
            }
        }
        viewModelScope.launch {
            menuItemRepo.observeAvailable().collect { items ->
                _uiState.update { it.copy(menuItems = items) }
            }
        }
        viewModelScope.launch {
            comboRepo.observeActive().collect { combos ->
                _uiState.update { it.copy(combos = combos) }
            }
        }
        viewModelScope.launch {
            customerRepo.observeAll().collect { customers ->
                _uiState.update { it.copy(customers = customers) }
            }
        }
        observeOrderItems()
    }

    private fun observeOrderItems() {
        viewModelScope.launch {
            orderRepo.observeActive()
                .map { orders -> orders.find { it.id == orderId } }
                .filterNotNull()
                .collect { order ->
                    val items = orderRepo.getItemsByOrder(orderId)
                    val activeItems = items.filter { it.status != OrderItemStatus.REFUNDED }
                    val subtotal = activeItems.sumOf { it.lineTotalMinorUnit }
                    val tax = activeItems.sumOf { item ->
                        item.taxRateId?.let { _uiState.value.regionConfig.taxRateById(it) }
                            ?.taxOn(item.lineTotalMinorUnit) ?: 0L
                    }
                    _uiState.update {
                        it.copy(
                            items = items,
                            subtotalMinorUnit = subtotal,
                            taxTotalMinorUnit = tax,
                            discountMinorUnit = order.discountMinorUnit,
                            orderStatus = order.status,
                            orderType = order.type,
                            orderNotes = order.orderNotes,
                            // FSR (dine-in, bound to a table) fires to the kitchen and settles
                            // at the cashier terminal later; QSR (walk-in/counter, no table)
                            // collects payment inline on this screen. Previously every caller
                            // of OrderScreen got the QSR inline-payment Checkout button
                            // regardless of tableId, so handheld's "no on-device checkout"
                            // FSR design was silently bypassed — found via real-device testing.
                            isQsrMode = order.tableId == null,
                        )
                    }
                }
        }
    }

    // ── Order type ───────────────────────────────────────────────────────────

    fun setOrderType(type: OrderType) {
        _uiState.update { it.copy(orderType = type) }
        // Persist immediately — the takeaway queue and reports filter on Order.type
        viewModelScope.launch {
            orderRepo.getById(orderId)?.let { order ->
                if (order.type != type) {
                    orderRepo.save(order.copy(type = type, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    // ── Add item (with modifier gate) ────────────────────────────────────────

    fun onMenuItemTapped(menuItem: MenuItem) {
        viewModelScope.launch {
            val groups = menuItemRepo.getModifierGroups(menuItem.id)
            if (groups.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        pendingModifierItem = menuItem,
                        pendingModifierGroups = groups,
                        modifierSelections = emptyMap(),
                    )
                }
            } else {
                addItemDirectly(menuItem, emptyList())
            }
        }
    }

    fun onModifierToggled(group: ModifierGroup, modifier: Modifier) {
        val current = _uiState.value.modifierSelections.toMutableMap()
        val selected = current.getOrDefault(group.id, emptySet()).toMutableSet()

        when (group.type) {
            ModifierGroupType.SINGLE -> {
                selected.clear()
                selected.add(modifier.id)
            }
            ModifierGroupType.MULTI -> {
                if (modifier.id in selected) {
                    selected.remove(modifier.id)
                } else if (selected.size < group.maxSelect) {
                    selected.add(modifier.id)
                }
            }
        }
        current[group.id] = selected
        _uiState.update { it.copy(modifierSelections = current) }
    }

    fun confirmModifiers() {
        val state = _uiState.value
        val menuItem = state.pendingModifierItem ?: return
        val groups = state.pendingModifierGroups

        // Validate required groups
        for (group in groups) {
            if (group.required) {
                val selected = state.modifierSelections[group.id] ?: emptySet()
                if (selected.size < group.minSelect) return  // block confirm if required not satisfied
            }
        }

        // Build SelectedModifier snapshot
        val selectedModifiers = groups.flatMap { group ->
            val selectedIds = state.modifierSelections[group.id] ?: emptySet()
            group.modifiers
                .filter { it.id in selectedIds }
                .map { mod ->
                    SelectedModifier(
                        groupId = group.id,
                        modifierId = mod.id,
                        nameSnapshot = mod.names,
                        priceAdjustmentMinorUnit = mod.priceAdjustmentMinorUnit,
                    )
                }
        }

        addItemDirectly(menuItem, selectedModifiers)
        _uiState.update { it.copy(pendingModifierItem = null, pendingModifierGroups = emptyList(), modifierSelections = emptyMap()) }
    }

    fun dismissModifiers() {
        _uiState.update { it.copy(pendingModifierItem = null, pendingModifierGroups = emptyList(), modifierSelections = emptyMap()) }
    }

    private fun addItemDirectly(menuItem: MenuItem, selectedModifiers: List<SelectedModifier>) {
        viewModelScope.launch {
            val existing = _uiState.value.items.find {
                it.menuItemId == menuItem.id &&
                    it.status == OrderItemStatus.PENDING &&
                    it.selectedModifiers == selectedModifiers
            }
            val updated = if (existing != null) {
                existing.copy(quantity = existing.quantity + 1)
            } else {
                OrderItem(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    menuItemId = menuItem.id,
                    menuItemNameSnapshot = menuItem.names,
                    quantity = 1,
                    unitPriceMinorUnit = menuItem.priceMinorUnit,
                    taxRateId = menuItem.taxRateId,
                    categoryId = menuItem.categoryId,
                    course = menuItem.course,
                    selectedModifiers = selectedModifiers,
                    allergenSnapshot = menuItem.allergens,
                )
            }
            orderRepo.saveItems(listOf(updated))
            val newItems = _uiState.value.items.toMutableList().apply {
                val idx = indexOfFirst { it.id == updated.id }
                if (idx >= 0) set(idx, updated) else add(updated)
            }
            val activeItemCountBefore = _uiState.value.items.count { it.status != OrderItemStatus.REFUNDED }
            recomputeSubtotal(newItems)
            if (activeItemCountBefore == 0) broadcastCds(CdsPhase.ORDER) // first item — show order on customer display
        }
    }

    // ── Quantity / void ──────────────────────────────────────────────────────

    fun incrementItem(item: OrderItem) {
        if (item.status != OrderItemStatus.PENDING) return
        viewModelScope.launch {
            val updated = item.copy(quantity = item.quantity + 1)
            orderRepo.saveItems(listOf(updated))
            updateItemInState(updated)
        }
    }

    fun decrementItem(item: OrderItem) {
        if (item.status != OrderItemStatus.PENDING) return
        if (item.quantity <= 1) {
            removeItem(item)
            return
        }
        viewModelScope.launch {
            val updated = item.copy(quantity = item.quantity - 1)
            orderRepo.saveItems(listOf(updated))
            updateItemInState(updated)
        }
    }

    fun removeItem(item: OrderItem) {
        if (item.status != OrderItemStatus.PENDING) return
        viewModelScope.launch {
            val newItems = _uiState.value.items.filter { it.id != item.id }
            // Mark deleted by saving quantity 0 — actual DB delete deferred to Batch 3 DAO
            orderRepo.saveItems(listOf(item.copy(quantity = 0)))
            recomputeSubtotal(newItems)
        }
    }

    fun dismissPermissionDenied() = _uiState.update { it.copy(permissionDeniedAction = null) }

    fun startNoteEdit(itemId: String) = _uiState.update { it.copy(noteEditingItemId = itemId) }
    fun dismissNoteEdit() = _uiState.update { it.copy(noteEditingItemId = null) }

    fun saveItemNote(itemId: String, note: String) {
        viewModelScope.launch {
            val item = _uiState.value.items.find { it.id == itemId } ?: return@launch
            val updated = item.copy(notes = note)
            orderRepo.saveItems(listOf(updated))
            _uiState.update { s ->
                val items = s.items.map { if (it.id == itemId) updated else it }
                s.copy(items = items, noteEditingItemId = null)
            }
        }
    }

    // ── Order notes ──────────────────────────────────────────────────────────

    fun showOrderNotesDialog() = _uiState.update { it.copy(orderNotesDialogVisible = true) }
    fun dismissOrderNotesDialog() = _uiState.update { it.copy(orderNotesDialogVisible = false) }

    fun saveOrderNotes(notes: String) {
        viewModelScope.launch {
            val order = orderRepo.getById(orderId) ?: return@launch
            orderRepo.save(order.copy(orderNotes = notes.trim(), updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(orderNotes = notes.trim(), orderNotesDialogVisible = false) }
        }
    }

    /** Marks a PLACED/SERVED item as REFUNDED (void after order is placed). */
    fun voidItem(item: OrderItem) {
        if (item.status == OrderItemStatus.PENDING || item.status == OrderItemStatus.REFUNDED) return
        viewModelScope.launch {
            if (checkPermission(sessionRepo.current(), PermissionKey.ORDER_VOID_ITEM) != CheckPermissionUseCase.Result.Allowed) {
                _uiState.update { it.copy(permissionDeniedAction = "Void Item") }
                return@launch
            }
            val voided = item.copy(status = OrderItemStatus.REFUNDED)
            orderRepo.saveItems(listOf(voided))
            val newItems = _uiState.value.items.map { if (it.id == item.id) voided else it }
            recomputeSubtotal(newItems)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun updateItemInState(updated: OrderItem) {
        val newItems = _uiState.value.items.map { if (it.id == updated.id) updated else it }
        recomputeSubtotal(newItems)
    }

    /**
     * Recomputes subtotal/tax from the live cart and persists them to the Order row via
     * [OrderRepository.updateTotals] — not just local UI state. Without this, anything that
     * reads the Order directly (the Tables FSR detail panel, [ApplyDiscountUseCase]'s
     * subtotal-bound validation, the CDS server snapshot) sees a stale $0 until the order is
     * placed/fired, even though the cashier has already built up a real cart.
     */
    private suspend fun recomputeSubtotal(items: List<OrderItem>) {
        val activeItems = items.filter { it.status != OrderItemStatus.REFUNDED }
        val subtotal = activeItems.sumOf { it.lineTotalMinorUnit }
        val tax = activeItems.sumOf { item ->
            item.taxRateId?.let { _uiState.value.regionConfig.taxRateById(it) }?.taxOn(item.lineTotalMinorUnit) ?: 0L
        }
        _uiState.update { it.copy(items = items, subtotalMinorUnit = subtotal, taxTotalMinorUnit = tax) }
        orderRepo.updateTotals(orderId, subtotal, tax)
    }

    // ── Combo ────────────────────────────────────────────────────────────────

    fun addCombo(comboId: String) {
        viewModelScope.launch {
            when (val result = addComboUseCase(orderId, comboId)) {
                is AddComboUseCase.Result.Success -> {
                    val allItems = orderRepo.getItemsByOrder(orderId)
                    recomputeSubtotal(allItems)
                }
                is AddComboUseCase.Result.Error ->
                    _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    // ── Place order ──────────────────────────────────────────────────────────

    fun placeOrder() {
        val items = _uiState.value.items.filter { it.status == OrderItemStatus.PENDING }
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = placeOrderUseCase(
                PlaceOrderUseCase.Params(
                    orderId = orderId,
                    items = items,
                    operatorId = sessionRepo.current()?.id ?: "",
                )
            )
            when (result) {
                is PlaceOrderUseCase.Result.Success -> {
                    fireKitchenTickets(orderId)
                    val locale = configRepo.current().locale.substringBefore('-').lowercase()
                    printKitchenTicketUseCase(orderId, locale)   // best-effort; errors are silent
                    _uiState.update { it.copy(isLoading = false, orderPlaced = true) }
                }
                is PlaceOrderUseCase.Result.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    // ── QSR payment modal ─────────────────────────────────────────────────────

    fun openPaymentModal() {
        val pn = if (_uiState.value.pickupNumber.isBlank()) generatePickupNumber() else _uiState.value.pickupNumber
        broadcastCds(CdsPhase.ORDER)
        _uiState.update { it.copy(paymentModalVisible = true, paymentStep = QsrPaymentStep.SELECT_METHOD, cashReceivedInput = "", cashReceivedMinorUnit = 0L, pickupNumber = pn) }
    }

    fun dismissPaymentModal() {
        _uiState.update { it.copy(paymentModalVisible = false, paymentStep = QsrPaymentStep.SELECT_METHOD, activePaymentMethod = null, cashReceivedInput = "", cashReceivedMinorUnit = 0L) }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(activePaymentMethod = method) }
        when (method) {
            PaymentMethod.CASH -> _uiState.update { it.copy(paymentStep = QsrPaymentStep.CASH_INPUT) }
            else -> processPayment(method, _uiState.value.totalMinorUnit)
        }
    }

    fun backToMethodSelection() {
        _uiState.update { it.copy(paymentStep = QsrPaymentStep.SELECT_METHOD, activePaymentMethod = null, cashReceivedInput = "", cashReceivedMinorUnit = 0L) }
    }

    fun onCashReceivedInputChanged(input: String) {
        val sanitized = input.filter { it.isDigit() || it == '.' }
        val minorUnit = sanitized.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
        _uiState.update { it.copy(cashReceivedInput = sanitized, cashReceivedMinorUnit = minorUnit) }
    }

    fun setQuickCashAmount(minorUnit: Long) {
        val formatted = "%.2f".format(minorUnit / 100.0)
        _uiState.update { it.copy(cashReceivedInput = formatted, cashReceivedMinorUnit = minorUnit) }
    }

    fun confirmCashPayment() {
        val state = _uiState.value
        if (state.cashReceivedMinorUnit < state.totalMinorUnit) return
        processPayment(PaymentMethod.CASH, state.cashReceivedMinorUnit)
    }

    private fun processPayment(method: PaymentMethod, amountMinorUnit: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(paymentStep = QsrPaymentStep.PROCESSING, isLoading = true) }
            val operatorId = sessionRepo.current()?.id ?: ""

            // 1. Place order (PENDING → PLACED via state machine)
            val pendingItems = _uiState.value.items.filter { it.status == OrderItemStatus.PENDING }
            if (pendingItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No items to place", paymentModalVisible = false) }
                return@launch
            }

            val placeResult = placeOrderUseCase(
                PlaceOrderUseCase.Params(orderId = orderId, items = pendingItems, operatorId = operatorId)
            )
            if (placeResult is PlaceOrderUseCase.Result.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = placeResult.message, paymentModalVisible = false) }
                return@launch
            }
            val placedOrder = (placeResult as PlaceOrderUseCase.Result.Success).order

            // 2. Record payment
            val payment = Payment(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                amountMinorUnit = amountMinorUnit,
                method = method,
                status = PaymentStatus.PAID,
                operatorId = operatorId,
                createdAt = System.currentTimeMillis(),
            )
            paymentRepo.save(payment)

            // 3. Settle payment — advances order READY→CLOSED via state machine
            val settleResult = settlePaymentUseCase(SettlePaymentUseCase.Params(payment = payment))
            if (settleResult is SettlePaymentUseCase.Result.Error) {
                // SettlePaymentUseCase expects READY/PLACED; QSR orders are still PLACED right
                // after firing (never explicitly marked READY), so fall back to closing it directly.
                val readyStatus = com.restaurantpos.core.domain.statemachine.OrderStateMachine.onAllServed(placedOrder.status)
                val closedStatus = com.restaurantpos.core.domain.statemachine.OrderStateMachine.onPaymentComplete(readyStatus)
                orderRepo.save(placedOrder.copy(status = closedStatus, updatedAt = System.currentTimeMillis()))
            }

            // 4. Fire kitchen tickets + print (QSR: auto-send after payment)
            fireKitchenTickets(orderId)
            val locale = configRepo.current().locale.substringBefore('-').lowercase()
            printKitchenTicketUseCase(orderId, locale)

            // 5. Show success
            broadcastCds(CdsPhase.SUCCESS)
            val orderNumber = placedOrder.pickupCode ?: orderId.take(4).uppercase()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    paymentModalVisible = false,
                    paymentSuccessVisible = true,
                    completedOrderNumber = "#$orderNumber",
                    // NOTE: orderPlaced is intentionally NOT set here — it would trigger
                    // OrderScreen's onOrderPlaced navigation to Checkout, popping this screen
                    // (and the success overlay) before the customer ever sees "Paid!". QSR is
                    // already fully paid; the success overlay's own timer/buttons drive what's next.
                )
            }
        }
    }

    fun dismissPaymentSuccess() {
        _uiState.update {
            it.copy(
                paymentSuccessVisible = false,
                completedOrderNumber = "",
                pickupNumber = "",
            )
        }
        // Clear cart: create a fresh order
        viewModelScope.launch {
            clearAllPendingItems()
        }
    }

    fun clearCart() {
        viewModelScope.launch { clearAllPendingItems() }
    }

    private suspend fun clearAllPendingItems() {
        val pending = _uiState.value.items.filter { it.status == OrderItemStatus.PENDING }
        pending.forEach { orderRepo.saveItems(listOf(it.copy(quantity = 0))) }
        val remaining = _uiState.value.items.filter { it.status != OrderItemStatus.PENDING }
        recomputeSubtotal(remaining)
        cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, CdsPhase.WELCOME, null)
    }

    fun editPickupNumber(number: String) {
        _uiState.update { it.copy(pickupNumber = number.trim()) }
    }

    private fun generatePickupNumber(): String {
        val alphabet = ('A'..'Z').toList()
        val idx = (System.currentTimeMillis() % 26).toInt()
        val seq = ((System.currentTimeMillis() / 1000) % 999).toInt()
        return "${alphabet[idx]}${seq.toString().padStart(2, '0')}"
    }

    // ── Discount ─────────────────────────────────────────────────────────────

    fun showDiscountDialog() = _uiState.update { it.copy(discountDialogVisible = true) }
    fun dismissDiscountDialog() = _uiState.update { it.copy(discountDialogVisible = false) }

    fun applyOrderDiscount(discountMinorUnit: Long) {
        viewModelScope.launch {
            val result = applyDiscountUseCase(orderId, ApplyDiscountUseCase.Target.OrderLevel(discountMinorUnit))
            if (result is ApplyDiscountUseCase.Result.Success) {
                // Update local state immediately rather than waiting for observeOrderItems'
                // Flow to re-emit — keeps the cart total in sync with the dialog right away.
                _uiState.update { it.copy(discountDialogVisible = false, discountMinorUnit = result.order.discountMinorUnit) }
            } else if (result is ApplyDiscountUseCase.Result.Error) {
                _uiState.update { it.copy(errorMessage = result.message, discountDialogVisible = false) }
            }
        }
    }

    // ── Hold / Park order ────────────────────────────────────────────────────

    fun holdOrder() {
        viewModelScope.launch {
            val order = orderRepo.getById(orderId) ?: return@launch
            val heldNote = if (order.orderNotes.isBlank()) "[HELD]" else "${order.orderNotes} [HELD]"
            orderRepo.save(order.copy(orderNotes = heldNote, updatedAt = System.currentTimeMillis()))
            // Clear pending items from state
            clearAllPendingItems()
            cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, CdsPhase.WELCOME, null)
            _uiState.update { it.copy(orderHeld = true) } // triggers "go take the next order" navigation
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    // ── Walk-in Customer ─────────────────────────────────────────────────────

    fun showWalkinCustomerDialog() = _uiState.update { it.copy(walkinCustomerDialogVisible = true, customerSearchQuery = "") }
    fun dismissWalkinCustomerDialog() = _uiState.update { it.copy(walkinCustomerDialogVisible = false) }
    fun setCustomerSearchQuery(query: String) = _uiState.update { it.copy(customerSearchQuery = query) }

    fun selectCustomer(customer: com.restaurantpos.core.model.Customer) {
        _uiState.update {
            it.copy(selectedCustomerName = customer.name, walkinCustomerDialogVisible = false)
        }
    }

    fun clearSelectedCustomer() = _uiState.update { it.copy(selectedCustomerName = null) }

    // ── Print Again ───────────────────────────────────────────────────────────

    fun printLastReceipt() {
        viewModelScope.launch {
            val locale = configRepo.current().locale.substringBefore('-').lowercase()
            printKitchenTicketUseCase(orderId, locale)
        }
    }

    // ── Split order ──────────────────────────────────────────────────────────

    fun showSplitDialog() {
        _uiState.update { it.copy(splitDialogVisible = true, splitSelectedItemIds = emptySet()) }
    }

    fun dismissSplitDialog() {
        _uiState.update { it.copy(splitDialogVisible = false, splitSelectedItemIds = emptySet()) }
    }

    fun toggleSplitItem(itemId: String) {
        val current = _uiState.value.splitSelectedItemIds.toMutableSet()
        if (itemId in current) current.remove(itemId) else current.add(itemId)
        _uiState.update { it.copy(splitSelectedItemIds = current) }
    }

    fun confirmSplit() {
        val selected = _uiState.value.splitSelectedItemIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, splitDialogVisible = false) }
            val result = splitOrderUseCase(
                SplitOrderUseCase.Params(
                    sourceOrderId = orderId,
                    itemIdsToSplit = selected,
                    newOrderId = UUID.randomUUID().toString(),
                )
            )
            when (result) {
                is SplitOrderUseCase.Result.Success -> {
                    val remainingItems = orderRepo.getItemsByOrder(orderId)
                    val subtotal = remainingItems
                        .filter { it.status != OrderItemStatus.REFUNDED }
                        .sumOf { it.lineTotalMinorUnit }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = remainingItems,
                            subtotalMinorUnit = subtotal,
                            splitSelectedItemIds = emptySet(),
                        )
                    }
                }
                is SplitOrderUseCase.Result.Error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message, splitSelectedItemIds = emptySet())
                    }
            }
        }
    }
}
