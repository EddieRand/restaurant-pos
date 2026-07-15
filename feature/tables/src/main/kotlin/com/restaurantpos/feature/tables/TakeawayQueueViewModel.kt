package com.restaurantpos.feature.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TakeawayQueueUiState(
    val orders: List<Order> = emptyList(),
    val regionConfig: RegionConfig = DefaultRegionConfig,
    val isLoading: Boolean = true,
)

@HiltViewModel
class TakeawayQueueViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    val uiState: StateFlow<TakeawayQueueUiState> = combine(
        orderRepo.observeActive(),
        configRepo.config,
    ) { orders, cfg ->
        TakeawayQueueUiState(
            orders = orders
                .filter { it.type == OrderType.TAKEAWAY || it.type == OrderType.DELIVERY }
                .filter { it.status != OrderStatus.CLOSED && it.status != OrderStatus.VOIDED },
            regionConfig = cfg,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TakeawayQueueUiState())
}
