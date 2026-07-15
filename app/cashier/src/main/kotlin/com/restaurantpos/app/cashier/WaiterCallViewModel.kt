package com.restaurantpos.app.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.domain.repository.WaiterCallRepository
import com.restaurantpos.core.model.WaiterCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WaiterCallViewModel @Inject constructor(
    private val repo: WaiterCallRepository,
) : ViewModel() {

    val pendingCalls = repo.observePending()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun acknowledge(call: WaiterCall) = viewModelScope.launch { repo.acknowledge(call.id) }
    fun acknowledgeAll(tableId: String) = viewModelScope.launch { repo.acknowledgeAll(tableId) }
}
