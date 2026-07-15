package com.restaurantpos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.TimeclockConfig
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.repository.TimeclockRepository
import com.restaurantpos.core.domain.usecase.LoginWithPinUseCase
import com.restaurantpos.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val currentUser: User? = null,
    val pinInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Non-null when we need to show the clock-in confirmation screen. */
    val pendingClockInUser: User? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginWithPinUseCase,
    private val sessionRepo: SessionRepository,
    private val timeclockRepo: TimeclockRepository?,
    private val timeclockConfig: TimeclockConfig?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepo.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    fun onDigit(digit: Char) {
        val current = _uiState.value.pinInput
        if (current.length >= 6) return
        _uiState.value = _uiState.value.copy(pinInput = current + digit, errorMessage = null)
    }

    fun onBackspace() {
        val current = _uiState.value.pinInput
        if (current.isEmpty()) return
        _uiState.value = _uiState.value.copy(pinInput = current.dropLast(1), errorMessage = null)
    }

    fun onConfirm() {
        val pin = _uiState.value.pinInput
        if (pin.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = loginUseCase(pin)) {
                is LoginWithPinUseCase.Result.Success -> {
                    val requireClock = timeclockConfig?.enabled == true &&
                        timeclockConfig.requireClockInForOrders
                    if (requireClock && timeclockRepo != null) {
                        val activeId = timeclockRepo.activeTimecardId(result.user.id)
                        if (activeId == null) {
                            // Pause login — show clock-in confirmation
                            _uiState.value = _uiState.value.copy(
                                isLoading = false, pinInput = "", pendingClockInUser = result.user
                            )
                            return@launch
                        }
                    }
                    sessionRepo.login(result.user)
                    _uiState.value = _uiState.value.copy(isLoading = false, pinInput = "")
                }
                is LoginWithPinUseCase.Result.InvalidPin -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, pinInput = "", errorMessage = "Invalid PIN"
                    )
                }
                is LoginWithPinUseCase.Result.UserInactive -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, pinInput = "", errorMessage = "Account inactive"
                    )
                }
            }
        }
    }

    fun onClear() {
        _uiState.value = _uiState.value.copy(pinInput = "", errorMessage = null)
    }

    fun confirmClockIn(terminalId: String = "") {
        val user = _uiState.value.pendingClockInUser ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            timeclockRepo?.clockIn(user.id, user.displayName, terminalId)
            sessionRepo.login(user)
            _uiState.value = _uiState.value.copy(isLoading = false, pendingClockInUser = null)
        }
    }

    fun dismissClockIn() {
        _uiState.value = _uiState.value.copy(pendingClockInUser = null, errorMessage = null)
    }

    fun logout() {
        sessionRepo.logout()
        _uiState.value = _uiState.value.copy(pinInput = "", errorMessage = null)
    }
}
