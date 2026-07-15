package com.restaurantpos.feature.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PickupDisplayUiState(
    val readyOrders: List<Order> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Customer-facing "ready for pickup" board: shows pickup numbers for orders whose
 * kitchen tickets are all done (see BumpTicketUseCase), independent of payment status.
 */
@HiltViewModel
class PickupDisplayViewModel @Inject constructor(
    orderRepo: OrderRepository,
) : ViewModel() {

    val uiState: StateFlow<PickupDisplayUiState> = orderRepo.observeReadyForPickup()
        .map { orders ->
            PickupDisplayUiState(
                readyOrders = orders.sortedBy { it.updatedAt },
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickupDisplayUiState())
}
