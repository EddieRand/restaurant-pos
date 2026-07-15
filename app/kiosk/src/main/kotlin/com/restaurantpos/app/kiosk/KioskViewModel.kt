package com.restaurantpos.app.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class KioskUiState(
    val orderId: String? = null,
    val confirmedOrderId: String? = null,
    val isLoading: Boolean = true,
    /** Countdown seconds before auto-returning to start screen (null = not counting). */
    val autoReturnCountdown: Int? = null,
    /** Daily pickup number assigned once the order is placed. */
    val pickupCode: String? = null,
)

@HiltViewModel
class KioskViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    /** 缓存一份 KioskConfig，避免每次都读 current() */
    private val kioskCfg = configRepo.current().kioskConfig

    private val _uiState = MutableStateFlow(KioskUiState())
    val uiState: StateFlow<KioskUiState> = _uiState.asStateFlow()

    init {
        createFreshOrder()
    }

    private fun createFreshOrder() {
        viewModelScope.launch {
            val orderId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val order = Order(
                id = orderId,
                type = OrderType.TAKEAWAY,
                tableId = null,
                sourceTerminalId = configRepo.current().terminalId,
                status = OrderStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )
            orderRepo.save(order)
            _uiState.value = KioskUiState(orderId = orderId, isLoading = false)
        }
    }

    fun onOrderPlaced(orderId: String) {
        _uiState.value = _uiState.value.copy(confirmedOrderId = orderId, autoReturnCountdown = kioskCfg.autoReturnSeconds)
        viewModelScope.launch {
            val pickupCode = orderRepo.getById(orderId)?.pickupCode
            _uiState.value = _uiState.value.copy(pickupCode = pickupCode)
        }
        viewModelScope.launch {
            for (remaining in kioskCfg.autoReturnSeconds - 1 downTo 0) {
                delay(1_000L)
                if (_uiState.value.confirmedOrderId == null) return@launch // cancelled by user
                _uiState.value = _uiState.value.copy(autoReturnCountdown = remaining)
            }
            startNewOrder()
        }
    }

    /** Start a new order after the customer dismisses the confirmation screen. */
    fun startNewOrder() {
        _uiState.value = KioskUiState(isLoading = true)
        createFreshOrder()
    }
}
