package com.restaurantpos.feature.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.DefaultRegionConfig
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.model.LoyaltyTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CustomersTab { ALL, RECENT, LOYAL }

/** A customer is "loyal" if flagged VIP, on a paid tier, or with meaningful loyalty points. */
fun Customer.isLoyal(): Boolean =
    tags.any { it.equals("VIP", true) } ||
        (membershipTierId != null && membershipTierId != "tier-bronze") ||
        loyaltyPoints >= 500

data class CustomersUiState(
    val query: String = "",
    val tab: CustomersTab = CustomersTab.ALL,
    val all: List<Customer> = emptyList(),
    val selectedId: String? = null,
    val transactions: List<LoyaltyTransaction> = emptyList(),
    val regionConfig: RegionConfig = DefaultRegionConfig,
) {
    /** Query filter (name/phone) + active tab. */
    val filtered: List<Customer>
        get() {
            val byQuery = if (query.isBlank()) all
            else all.filter { it.name.contains(query, true) || it.phone.contains(query, true) }
            return when (tab) {
                CustomersTab.ALL -> byQuery
                CustomersTab.RECENT -> byQuery.sortedByDescending { it.lastVisitAt }
                CustomersTab.LOYAL -> byQuery.filter { it.isLoyal() }
            }
        }

    val selected: Customer? get() = all.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            customerRepo.observeAll().collect { list ->
                _uiState.update { state ->
                    // Auto-select the first customer once data arrives, for the detail panel.
                    val sel = state.selectedId ?: list.firstOrNull()?.id
                    state.copy(all = list, selectedId = sel)
                }
                _uiState.value.selectedId?.let { loadTransactions(it) }
            }
        }
        viewModelScope.launch {
            configRepo.config.collect { cfg -> _uiState.update { it.copy(regionConfig = cfg) } }
        }
    }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }
    fun setTab(tab: CustomersTab) = _uiState.update { it.copy(tab = tab) }

    fun select(id: String) {
        _uiState.update { it.copy(selectedId = id) }
        loadTransactions(id)
    }

    private fun loadTransactions(customerId: String) {
        viewModelScope.launch {
            val txns = runCatching { customerRepo.getTransactions(customerId) }.getOrDefault(emptyList())
            _uiState.update { if (it.selectedId == customerId) it.copy(transactions = txns) else it }
        }
    }
}
