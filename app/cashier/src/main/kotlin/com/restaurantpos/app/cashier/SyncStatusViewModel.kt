package com.restaurantpos.app.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.sync.NetworkMonitor
import com.restaurantpos.core.sync.SyncOutbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SyncStatusUiState(
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
)

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val outbox: SyncOutbox,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    val uiState: StateFlow<SyncStatusUiState> = combine(
        networkMonitor.isOnline,
        outbox.observePendingCount(),
    ) { online, pending ->
        SyncStatusUiState(isOnline = online, pendingCount = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatusUiState())
}
