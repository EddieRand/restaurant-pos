package com.restaurantpos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.config.TaxMode
import com.restaurantpos.core.config.TaxRate
import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.domain.usecase.sha256
import com.restaurantpos.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val config: RegionConfig,
    val previewAmount: String = "",
    val saved: Boolean = false,
    val users: List<User> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            userRepo.observeActive().collect { users ->
                _state.update { it.copy(users = users) }
            }
        }
    }

    private val _state = MutableStateFlow(
        SettingsUiState(config = configRepo.current()).withPreview(),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setTerminalId(value: String) = mutate { it.copy(terminalId = value.take(32)) }
    fun setTimeZone(value: String) = mutate { it.copy(timeZone = value.take(64)) }
    fun setCurrencyCode(value: String) = mutate { it.copy(currencyCode = value.uppercase().take(3)) }
    fun setCurrencySymbol(value: String) = mutate { it.copy(currencySymbol = value.take(4)) }
    fun setCurrencyMinorDigits(value: Int) = mutate { it.copy(currencyMinorDigits = value.coerceIn(0, 4)) }
    fun setLocale(value: String) = mutate { it.copy(locale = value) }
    fun setTaxMode(value: TaxMode) = mutate { it.copy(defaultTaxMode = value) }
    fun setThousandsSeparator(value: Char) = mutate { it.copy(thousandsSeparator = value) }
    fun setDecimalSeparator(value: Char) = mutate { it.copy(decimalSeparator = value) }

    // ── Receipt config ───────────────────────────────────────────────────────

    fun setReceiptHeader(lines: List<String>) = mutate { cfg ->
        cfg.copy(receiptConfig = cfg.receiptConfig.copy(headerLines = lines))
    }

    fun setReceiptFooter(lines: List<String>) = mutate { cfg ->
        cfg.copy(receiptConfig = cfg.receiptConfig.copy(footerLines = lines))
    }

    fun setShowTaxId(show: Boolean) = mutate { cfg ->
        cfg.copy(receiptConfig = cfg.receiptConfig.copy(showTaxId = show))
    }

    fun setTaxId(id: String) = mutate { cfg ->
        cfg.copy(receiptConfig = cfg.receiptConfig.copy(taxId = id.take(64)))
    }

    fun addTaxRate(rate: TaxRate) = mutate { cfg ->
        cfg.copy(availableTaxRates = cfg.availableTaxRates + rate)
    }

    fun removeTaxRate(id: String) = mutate { cfg ->
        cfg.copy(availableTaxRates = cfg.availableTaxRates.filter { it.id != id })
    }

    // ── PAD config ───────────────────────────────────────────────────────────

    fun updatePadConfig(block: (com.restaurantpos.core.config.PadConfig) -> com.restaurantpos.core.config.PadConfig) =
        mutate { cfg -> cfg.copy(padConfig = block(cfg.padConfig)) }

    // ── Ticket template config ───────────────────────────────────────────────

    fun updateTicketTemplate(template: com.restaurantpos.core.config.TicketTemplateConfig) = mutate { cfg ->
        cfg.copy(printConfig = cfg.printConfig.withTemplate(template))
    }

    fun updatePrinterStations(stations: List<com.restaurantpos.core.config.PrinterStation>) = mutate { cfg ->
        cfg.copy(printConfig = cfg.printConfig.copy(stations = stations))
    }

    fun save() {
        viewModelScope.launch {
            configRepo.update(_state.value.config)
            _state.update { it.copy(saved = true) }
        }
    }

    fun dismissSaved() = _state.update { it.copy(saved = false) }

    fun addUser(displayName: String, pin: String, roleId: String) {
        if (displayName.isBlank() || pin.length < 4) return
        viewModelScope.launch {
            userRepo.save(
                User(
                    id = UUID.randomUUID().toString(),
                    displayName = displayName.trim(),
                    roleId = roleId,
                    pinHash = pin.sha256(),
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deactivateUser(userId: String) {
        viewModelScope.launch { userRepo.setActive(userId, false) }
    }

    private fun mutate(block: (RegionConfig) -> RegionConfig) {
        _state.update { s ->
            val updated = block(s.config)
            s.copy(config = updated, saved = false).withPreview(updated)
        }
    }

    private fun SettingsUiState.withPreview(cfg: RegionConfig = this.config): SettingsUiState =
        copy(previewAmount = runCatching { AmountFormatter(cfg).format(12345L) }.getOrDefault("?"))
}
