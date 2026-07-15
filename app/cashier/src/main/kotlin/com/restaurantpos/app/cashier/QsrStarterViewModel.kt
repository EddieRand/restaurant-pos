package com.restaurantpos.app.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.model.CdsPhase
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderType
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QsrStarterViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val configRepo: ConfigRepository,
    private val sessionRepo: SessionRepository,
    private val cdsPhaseBroadcaster: CdsPhaseBroadcaster,
) : ViewModel() {

    private val _orderCreated = MutableStateFlow<String?>(null)
    val orderCreated: StateFlow<String?> = _orderCreated

    fun startNewOrder() {
        viewModelScope.launch {
            val order = Order(
                id = UUID.randomUUID().toString(),
                type = OrderType.DINE_IN,
                sourceTerminalId = configRepo.current().terminalId,
                operatorId = sessionRepo.current()?.id ?: "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            orderRepo.save(order)
            cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, CdsPhase.ORDER, order.id)
            _orderCreated.value = order.id
        }
    }
}
