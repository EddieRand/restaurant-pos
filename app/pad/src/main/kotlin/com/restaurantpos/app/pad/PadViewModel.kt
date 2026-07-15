package com.restaurantpos.app.pad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.AycePackage
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.PadConfig
import com.restaurantpos.core.domain.repository.MenuItemRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.repository.WaiterCallRepository
import com.restaurantpos.core.domain.usecase.PlaceOrderUseCase
import com.restaurantpos.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────

data class PadUiState(
    val padConfig: PadConfig = PadConfig(),
    val screen: PadScreen = PadScreen.LOADING,

    // Menu
    val categories: List<String> = emptyList(),
    val menuItems: List<MenuItem> = emptyList(),
    val selectedCategory: String? = null,

    // Cart (current pending items not yet submitted)
    val cartItems: List<CartEntry> = emptyList(),

    // Session order history (all submitted rounds this session)
    val submittedItems: List<OrderItem> = emptyList(),
    val submittedRounds: List<AyceRound> = emptyList(),

    // AYCE state
    val ayceSession: AyceSession? = null,
    val roundCooldownSeconds: Int = 0,     // countdown until next round is unlocked
    val selectedPackage: AycePackage? = null,

    // Waiter call
    val waiterCallSent: Boolean = false,
    val waiterCallReason: WaiterCallReason? = null,

    // Idle
    val idleCountdown: Int = 0,
    val isIdle: Boolean = false,

    // Misc
    val activeLocale: String = "en",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

data class CartEntry(
    val tempId: String = UUID.randomUUID().toString(),
    val menuItem: MenuItem,
    val quantity: Int,
    val note: String = "",
    val modifierLabels: List<String> = emptyList(),
) {
    val lineTotalMinorUnit: Long get() = menuItem.priceMinorUnit * quantity

}

data class AyceSession(
    val packageId: String?,
    val totalRoundsLimit: Int?,
    val currentRound: Int = 1,
    val maxItemsPerRound: Int,
    val minMinutesBetweenRounds: Int,
    val roundStartedAt: Long = System.currentTimeMillis(),
) {
    val isUnlimited: Boolean get() = totalRoundsLimit == null
    val hasMoreRounds: Boolean get() = totalRoundsLimit == null || currentRound < totalRoundsLimit
    val minutesElapsed: Int get() = ((System.currentTimeMillis() - roundStartedAt) / 60_000).toInt()
    val cooldownRemainingMinutes: Int get() = (minMinutesBetweenRounds - minutesElapsed).coerceAtLeast(0)
    val roundUnlocked: Boolean get() = minutesElapsed >= minMinutesBetweenRounds
}

data class AyceRound(val roundNumber: Int, val items: List<OrderItem>, val submittedAt: Long)

enum class PadScreen {
    LOADING,
    TABLE_BINDING,   // PAD not yet bound to a table
    AYCE_PACKAGE,    // AYCE: choose a package before ordering
    MENU,            // main ordering screen
    ORDER_HISTORY,   // submitted rounds / current status
    IDLE,            // screensaver
}

// ── ViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class PadViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val menuItemRepo: MenuItemRepository,
    private val orderRepo: OrderRepository,
    private val tableRepo: TableRepository,
    private val waiterCallRepo: WaiterCallRepository,
    private val placeOrderUseCase: PlaceOrderUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PadUiState())
    val state: StateFlow<PadUiState> = _state.asStateFlow()

    private var idleJob: Job? = null
    private var cooldownJob: Job? = null
    private var currentOrderId: String? = null

    init {
        viewModelScope.launch { initialise() }
    }

    private suspend fun initialise() {
        val cfg = configRepo.current()
        val padCfg = cfg.padConfig
        val timeHhmm = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        _state.update {
            it.copy(
                padConfig = padCfg,
                activeLocale = cfg.locale.substringBefore('-').lowercase(),
            )
        }

        // Determine initial screen
        if (padCfg.boundTableId.isEmpty()) {
            _state.update { it.copy(screen = PadScreen.TABLE_BINDING) }
            return
        }

        // Ensure there's an open order for this table
        ensureOpenOrder(padCfg)

        if (padCfg.ayceEnabled && padCfg.aycePackages.isNotEmpty()) {
            _state.update { it.copy(screen = PadScreen.AYCE_PACKAGE) }
        } else {
            val session = if (padCfg.ayceEnabled) buildAyceSession(null, padCfg) else null
            _state.update { it.copy(screen = PadScreen.MENU, ayceSession = session) }
        }

        // Subscribe reactively to context-filtered menu items (DINE_IN channel)
        menuItemRepo.observeAvailableForContext("DINE_IN", timeHhmm)
            .onEach { items ->
                val cats = items.mapNotNull { it.categoryId }.distinct()
                _state.update { s ->
                    s.copy(
                        menuItems = items,
                        categories = cats,
                        selectedCategory = s.selectedCategory ?: cats.firstOrNull(),
                    )
                }
            }
            .launchIn(viewModelScope)

        startIdleTimer()
    }

    private suspend fun ensureOpenOrder(padCfg: PadConfig) {
        val existing = orderRepo.getActiveByTable(padCfg.boundTableId)
        currentOrderId = existing?.id ?: run {
            val orderId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            orderRepo.save(Order(
                id = orderId,
                type = OrderType.DINE_IN,
                tableId = padCfg.boundTableId,
                sourceTerminalId = "pad-${padCfg.boundTableId}",
                status = OrderStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            ))
            orderId
        }
        // Load already-submitted items
        currentOrderId?.let { oid ->
            val submitted = orderRepo.getItemsByOrder(oid)
            _state.update { it.copy(submittedItems = submitted) }
        }
    }

    // ── Menu / Cart ───────────────────────────────────────────────────────

    fun selectCategory(categoryId: String) {
        resetIdle()
        _state.update { it.copy(selectedCategory = categoryId) }
    }

    fun addToCart(item: MenuItem, qty: Int = 1, note: String = "") {
        resetIdle()
        _state.update { s ->
            val existing = s.cartItems.indexOfFirst { it.menuItem.id == item.id && it.note == note }
            val updated = if (existing >= 0) {
                s.cartItems.toMutableList().also { it[existing] = it[existing].copy(quantity = it[existing].quantity + qty) }
            } else {
                s.cartItems + CartEntry(menuItem = item, quantity = qty, note = note)
            }
            s.copy(cartItems = updated)
        }
    }

    fun removeFromCart(tempId: String) {
        resetIdle()
        _state.update { it.copy(cartItems = it.cartItems.filter { e -> e.tempId != tempId }) }
    }

    fun updateCartQty(tempId: String, qty: Int) {
        resetIdle()
        if (qty <= 0) { removeFromCart(tempId); return }
        _state.update {
            it.copy(cartItems = it.cartItems.map { e -> if (e.tempId == tempId) e.copy(quantity = qty) else e })
        }
    }

    // ── AYCE package selection ────────────────────────────────────────────

    fun selectAycePackage(pkg: AycePackage) {
        resetIdle()
        val session = buildAyceSession(pkg, _state.value.padConfig)
        _state.update { it.copy(selectedPackage = pkg, ayceSession = session, screen = PadScreen.MENU) }
    }

    fun skipAycePackage() {
        resetIdle()
        val session = buildAyceSession(null, _state.value.padConfig)
        _state.update { it.copy(ayceSession = session, screen = PadScreen.MENU) }
    }

    private fun buildAyceSession(pkg: AycePackage?, cfg: PadConfig): AyceSession =
        AyceSession(
            packageId = pkg?.id,
            totalRoundsLimit = pkg?.totalRoundsLimit ?: cfg.totalRoundsLimit,
            maxItemsPerRound = pkg?.maxItemsPerRound ?: cfg.maxItemsPerRound,
            minMinutesBetweenRounds = pkg?.minMinutesBetweenRounds ?: cfg.minMinutesBetweenRounds,
            roundStartedAt = System.currentTimeMillis(),
        )

    // ── Submit order ──────────────────────────────────────────────────────

    fun submitOrder() {
        val s = _state.value
        if (s.cartItems.isEmpty()) return
        resetIdle()

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val orderId = currentOrderId ?: return@launch
            val cfg = configRepo.current()
            val roundNumber = s.ayceSession?.currentRound ?: 1

            // Add items to the existing table order
            val newItems = s.cartItems.map { entry ->
                OrderItem(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    menuItemId = entry.menuItem.id,
                    menuItemNameSnapshot = entry.menuItem.names,
                    quantity = entry.quantity,
                    unitPriceMinorUnit = entry.menuItem.priceMinorUnit,
                    taxRateId = null,
                    notes = entry.note,
                    status = OrderItemStatus.PLACED,
                )
            }
            orderRepo.saveItems(newItems)

            // Fire to kitchen
            placeOrderUseCase(PlaceOrderUseCase.Params(orderId = orderId, items = newItems))

            // Track AYCE round
            val newRound = AyceRound(
                roundNumber = roundNumber,
                items = s.submittedItems,
                submittedAt = System.currentTimeMillis(),
            )
            val updatedSession = s.ayceSession?.copy(
                currentRound = roundNumber + 1,
                roundStartedAt = System.currentTimeMillis(),
            )

            _state.update { it.copy(
                cartItems = emptyList(),
                ayceSession = updatedSession,
                submittedRounds = it.submittedRounds + newRound,
                isLoading = false,
                successMessage = "Order sent to kitchen!",
            ) }

            // Start AYCE cooldown timer if needed
            updatedSession?.let { startCooldownTimer(it) }

            // Auto-clear success banner
            delay(3_000)
            _state.update { it.copy(successMessage = null) }
        }
    }

    // ── AYCE cooldown timer (Jamezz-style) ────────────────────────────────

    private fun startCooldownTimer(session: AyceSession) {
        cooldownJob?.cancel()
        val totalSeconds = session.minMinutesBetweenRounds * 60
        val elapsed = ((System.currentTimeMillis() - session.roundStartedAt) / 1_000).toInt()
        var remaining = (totalSeconds - elapsed).coerceAtLeast(0)
        if (remaining == 0) return

        cooldownJob = viewModelScope.launch {
            while (remaining > 0) {
                _state.update { it.copy(roundCooldownSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(roundCooldownSeconds = 0) }
        }
    }

    // ── Waiter call (Ziosk-style) ─────────────────────────────────────────

    fun callWaiter(reason: WaiterCallReason = WaiterCallReason.GENERAL) {
        resetIdle()
        val cfg = _state.value.padConfig
        viewModelScope.launch {
            waiterCallRepo.call(WaiterCall(
                id = UUID.randomUUID().toString(),
                tableId = cfg.boundTableId,
                tableDisplayName = cfg.tableDisplayName.ifBlank { cfg.boundTableId },
                reason = reason,
                createdAt = System.currentTimeMillis(),
            ))
            _state.update { it.copy(waiterCallSent = true, waiterCallReason = reason) }
            delay(4_000)
            _state.update { it.copy(waiterCallSent = false, waiterCallReason = null) }
        }
    }

    // ── Idle / screensaver ────────────────────────────────────────────────

    private fun startIdleTimer() {
        idleJob?.cancel()
        val timeout = _state.value.padConfig.idleTimeoutSeconds
        if (timeout <= 0) return
        idleJob = viewModelScope.launch {
            delay(timeout * 1_000L)
            _state.update { it.copy(isIdle = true, screen = PadScreen.IDLE) }
        }
    }

    fun resetIdle() {
        if (_state.value.isIdle) {
            _state.update { it.copy(isIdle = false, screen = PadScreen.MENU) }
        }
        startIdleTimer()
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun showOrderHistory() { resetIdle(); _state.update { it.copy(screen = PadScreen.ORDER_HISTORY) } }
    fun showMenu()         { resetIdle(); _state.update { it.copy(screen = PadScreen.MENU) } }

    fun switchLocale(locale: String) {
        resetIdle()
        _state.update { it.copy(activeLocale = locale) }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    // ── Computed helpers ──────────────────────────────────────────────────

    val cartTotal: StateFlow<Long> = state
        .map { s -> s.cartItems.sumOf { it.lineTotalMinorUnit } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val cartItemCount: StateFlow<Int> = state
        .map { s -> s.cartItems.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Items visible in the current category. */
    val visibleMenuItems: StateFlow<List<MenuItem>> = state
        .map { s -> if (s.selectedCategory == null) s.menuItems
                    else s.menuItems.filter { it.categoryId == s.selectedCategory } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** True when the cart can be submitted (AYCE checks included). */
    val canSubmit: StateFlow<Boolean> = state.map { s ->
        if (s.cartItems.isEmpty()) return@map false
        val ayce = s.ayceSession ?: return@map true
        if (!ayce.hasMoreRounds) return@map false
        if (s.roundCooldownSeconds > 0) return@map false
        if (ayce.maxItemsPerRound > 0 && s.cartItems.sumOf { it.quantity } > ayce.maxItemsPerRound) return@map false
        true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
