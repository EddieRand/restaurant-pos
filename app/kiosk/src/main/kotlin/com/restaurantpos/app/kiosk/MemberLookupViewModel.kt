package com.restaurantpos.app.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.domain.repository.CustomerRepository
import com.restaurantpos.core.model.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberLookupUiState(
    val phone: String = "",
    val isSearching: Boolean = false,
    val customer: Customer? = null,
    val notFound: Boolean = false,
)

/**
 * Display-only member lookup for the kiosk: shows points/tier for a phone number.
 * Does NOT bind the looked-up customer to the current order (deferred to a future round).
 */
@HiltViewModel
class MemberLookupViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberLookupUiState())
    val uiState: StateFlow<MemberLookupUiState> = _uiState.asStateFlow()

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phone = phone, customer = null, notFound = false) }
    }

    fun search() {
        val phone = _uiState.value.phone.trim()
        if (phone.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, customer = null, notFound = false) }
            val customer = customerRepo.getByPhone(phone)
            _uiState.update {
                it.copy(isSearching = false, customer = customer, notFound = customer == null)
            }
        }
    }
}
