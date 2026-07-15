package com.restaurantpos.feature.kds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.KitchenTicketRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.usecase.BumpTicketUseCase
import com.restaurantpos.core.domain.usecase.RecallTicketUseCase
import com.restaurantpos.core.model.KitchenTicket
import com.restaurantpos.core.model.OrderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicketCard(
    val ticket: KitchenTicket,
    val items: List<OrderItem>,
    val elapsedSeconds: Long,
)

data class KdsUiState(
    val ticketsByStation: Map<String, List<TicketCard>> = emptyMap(),
    val stations: List<String> = emptyList(),
    val selectedStation: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Epoch millis refreshed every second for live elapsed display. */
    val nowMillis: Long = System.currentTimeMillis(),
    /** 从 KdsConfig 读取，不再硬编码 */
    val urgentAfterSeconds: Long = 300L,
    val criticalAfterSeconds: Long = 600L,
    /** Active BCP-47 locale for resolving menu item names on the ticket. */
    val locale: String = "en-US",
)

@HiltViewModel
class KdsViewModel @Inject constructor(
    private val ticketRepo: KitchenTicketRepository,
    private val orderRepo: OrderRepository,
    private val bumpUseCase: BumpTicketUseCase,
    private val recallUseCase: RecallTicketUseCase,
    private val configRepo: ConfigRepository,
) : ViewModel() {

    /** 缓存一份 KdsConfig，避免每次 ticker 都读 current() */
    private val kdsCfg = configRepo.current().kdsConfig

    private val _state = MutableStateFlow(
        KdsUiState(
            urgentAfterSeconds = kdsCfg.urgentAfterSeconds,
            criticalAfterSeconds = kdsCfg.criticalAfterSeconds,
            locale = configRepo.current().locale,
        )
    )
    val state: StateFlow<KdsUiState> = _state.asStateFlow()

    // Snapshot of KitchenTickets (items populated once per ticket event)
    private val _ticketCards = MutableStateFlow<Map<String, List<TicketCard>>>(emptyMap())

    init {
        // Observe ticket events and rebuild cards (without elapsed — that comes from the ticker).
        // Also rebuild on order-table changes: synced order items can arrive AFTER their
        // ticket (separate pullers), and the card must pick them up once they land.
        viewModelScope.launch {
            combine(ticketRepo.observeActive(), orderRepo.observeActive()) { tickets, _ -> tickets }
                .collect { tickets ->
                val grouped = tickets.groupBy { it.stationId }
                val stations = grouped.keys.sorted()
                val cardsByStation = grouped.mapValues { (_, stationTickets) ->
                    stationTickets.map { ticket ->
                        val items = orderRepo.getItemsByOrder(ticket.orderId)
                            .filter { it.id in ticket.orderItemIds }
                        // elapsedSeconds will be overwritten by the ticker each second
                        TicketCard(ticket = ticket, items = items, elapsedSeconds = 0L)
                    }.sortedWith(compareBy({ it.ticket.course }, { it.ticket.createdAt }))
                }
                _ticketCards.value = cardsByStation
                _state.update {
                    it.copy(
                        ticketsByStation = cardsByStation,
                        stations = stations,
                        selectedStation = it.selectedStation ?: stations.firstOrNull(),
                        isLoading = false,
                    )
                }
            }
        }

        // Ticker: update nowMillis and recompute elapsedSeconds for all cards
        viewModelScope.launch {
            while (true) {
                delay(1_000L) // 1-second refresh interval
                val now = System.currentTimeMillis()
                val updatedCards = _ticketCards.value.mapValues { (_, cards) ->
                    cards.map { it.copy(elapsedSeconds = (now - it.ticket.createdAt) / 1_000L) }
                }
                _state.update { it.copy(nowMillis = now, ticketsByStation = updatedCards) }
            }
        }
    }

    fun selectStation(stationId: String) =
        _state.update { it.copy(selectedStation = stationId) }

    fun bump(ticketId: String) {
        viewModelScope.launch {
            runCatching { bumpUseCase(ticketId) }
                .onFailure { _state.update { s -> s.copy(errorMessage = it.message) } }
        }
    }

    fun recall(ticketId: String) {
        viewModelScope.launch {
            runCatching { recallUseCase(ticketId) }
                .onFailure { _state.update { s -> s.copy(errorMessage = it.message) } }
        }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }
}
