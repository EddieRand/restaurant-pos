package com.restaurantpos.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class OrdersTab { ALL, OPEN, COMPLETED, VOIDED }

data class OrderHistoryUiState(
    val query: String = "",
    val selectedStatus: OrderStatus? = null,
    val tab: OrdersTab = OrdersTab.ALL,
    val fromEpoch: Long? = null,
    val toEpoch: Long? = null,
    val results: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val regionConfig: RegionConfig = DefaultRegionConfig,
    val selectedOrderId: String? = null,
    val selectedItems: List<OrderItem> = emptyList(),
) {
    /** Results filtered by the active tab (Open spans draft/in-progress/placed/ready). */
    val filtered: List<Order>
        get() = when (tab) {
            OrdersTab.ALL -> results
            OrdersTab.OPEN -> results.filter {
                it.status in setOf(OrderStatus.DRAFT, OrderStatus.IN_PROGRESS, OrderStatus.PLACED, OrderStatus.READY)
            }
            OrdersTab.COMPLETED -> results.filter { it.status == OrderStatus.CLOSED }
            OrdersTab.VOIDED -> results.filter { it.status == OrderStatus.VOIDED }
        }

    val selectedOrder: Order? get() = results.firstOrNull { it.id == selectedOrderId }
}

@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(defaultState())
    val uiState: StateFlow<OrderHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepo.config.collect { cfg -> _uiState.update { it.copy(regionConfig = cfg) } }
        }
        // Populate today's orders immediately on entry (default query — no business-logic change).
        search()
    }

    private fun defaultState(): OrderHistoryUiState {
        val zone = runCatching { ZoneId.of(configRepo.current().timeZone) }.getOrElse { ZoneId.of("UTC") }
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return OrderHistoryUiState(fromEpoch = from, toEpoch = to)
    }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }
    fun setStatus(status: OrderStatus?) = _uiState.update { it.copy(selectedStatus = status) }
    fun setTab(tab: OrdersTab) {
        _uiState.update { it.copy(tab = tab) }
        // Keep selection valid within the new tab; otherwise select the first visible order.
        val s = _uiState.value
        if (s.filtered.none { it.id == s.selectedOrderId }) {
            s.filtered.firstOrNull()?.let { select(it.id) } ?: _uiState.update { it.copy(selectedOrderId = null, selectedItems = emptyList()) }
        }
    }
    fun setFrom(epoch: Long) = _uiState.update { it.copy(fromEpoch = epoch) }
    fun setTo(epoch: Long) = _uiState.update { it.copy(toEpoch = epoch) }

    fun search() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = orderRepo.searchOrders(
                    query = s.query,
                    fromEpoch = s.fromEpoch,
                    toEpoch = s.toEpoch,
                    status = s.selectedStatus,
                )
                _uiState.update { it.copy(isLoading = false, results = results, hasSearched = true) }
                // Auto-select the first order in the active tab for the detail panel.
                val first = _uiState.value.filtered.firstOrNull()
                if (first != null && _uiState.value.filtered.none { it.id == _uiState.value.selectedOrderId }) {
                    select(first.id)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun select(orderId: String) {
        _uiState.update { it.copy(selectedOrderId = orderId) }
        viewModelScope.launch {
            val items = runCatching { orderRepo.getItemsByOrder(orderId) }.getOrDefault(emptyList())
            _uiState.update { if (it.selectedOrderId == orderId) it.copy(selectedItems = items) else it }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
