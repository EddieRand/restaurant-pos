package com.restaurantpos.feature.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.OrderRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import com.restaurantpos.core.domain.repository.ReservationRepository
import com.restaurantpos.core.domain.repository.SessionRepository
import com.restaurantpos.core.domain.repository.TableRepository
import com.restaurantpos.core.domain.statemachine.TableStateMachine
import com.restaurantpos.core.domain.usecase.CancelReservationUseCase
import com.restaurantpos.core.domain.usecase.CreateReservationUseCase
import com.restaurantpos.core.domain.usecase.MergeTablesUseCase
import com.restaurantpos.core.domain.usecase.SeatReservationUseCase
import com.restaurantpos.core.domain.usecase.SplitTableUseCase
import com.restaurantpos.core.domain.usecase.TransferTableUseCase
import com.restaurantpos.core.model.*
import com.restaurantpos.core.sync.CdsPhaseBroadcaster
import com.restaurantpos.core.model.CdsPhase
import com.restaurantpos.core.model.ReservationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TablesUiState(
    val tables: List<Table> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val timeZone: String = "UTC",
)

data class ReservationsUiState(
    val reservations: List<Reservation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class TableDetailState(
    val selectedTableId: String? = null,
    val order: com.restaurantpos.core.model.Order? = null,
    val items: List<com.restaurantpos.core.model.OrderItem> = emptyList(),
    val regionConfig: com.restaurantpos.core.config.RegionConfig = com.restaurantpos.core.config.DefaultRegionConfig,
)

@HiltViewModel
class TablesViewModel @Inject constructor(
    private val tableRepo: TableRepository,
    private val orderRepo: OrderRepository,
    private val reservationRepo: ReservationRepository,
    private val mergeTablesUseCase: MergeTablesUseCase,
    private val splitTableUseCase: SplitTableUseCase,
    private val transferTableUseCase: TransferTableUseCase,
    private val createReservationUseCase: CreateReservationUseCase,
    private val cancelReservationUseCase: CancelReservationUseCase,
    private val seatReservationUseCase: SeatReservationUseCase,
    private val configRepo: ConfigRepository,
    private val sessionRepo: SessionRepository,
    private val cdsPhaseBroadcaster: CdsPhaseBroadcaster,
) : ViewModel() {

    val uiState: StateFlow<TablesUiState> = tableRepo.observeAll()
        .map { TablesUiState(tables = it, timeZone = configRepo.current().timeZone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TablesUiState(isLoading = true))

    private val _detail = kotlinx.coroutines.flow.MutableStateFlow(
        TableDetailState(regionConfig = configRepo.current()),
    )
    val detailState: StateFlow<TableDetailState> = _detail

    /** Selects a table for the right-hand detail panel and loads its current order + items. */
    fun selectTable(table: com.restaurantpos.core.model.Table) {
        _detail.value = TableDetailState(selectedTableId = table.id, regionConfig = configRepo.current())
        val orderId = table.currentOrderId ?: return
        viewModelScope.launch {
            val order = runCatching { orderRepo.getById(orderId) }.getOrNull()
            val items = runCatching { orderRepo.getItemsByOrder(orderId) }.getOrDefault(emptyList())
            if (_detail.value.selectedTableId == table.id) {
                _detail.value = _detail.value.copy(order = order, items = items)
            }
        }
    }

    val reservationsUiState: StateFlow<ReservationsUiState> = run {
        val zone = ZoneId.of(configRepo.current().timeZone)
        val today = ZonedDateTime.now(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        reservationRepo.observeByDate(dayStart)
            .map { list ->
                ReservationsUiState(reservations = list.sortedBy { it.scheduledAt })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationsUiState(isLoading = true))
    }

    fun seatTable(tableId: String, guestCount: Int = 1, onOrderCreated: (orderId: String) -> Unit) {
        viewModelScope.launch {
            val table = tableRepo.getById(tableId) ?: return@launch
            if (table.status != TableStatus.AVAILABLE) return@launch

            val orderId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val order = Order(
                id = orderId,
                type = OrderType.DINE_IN,
                tableId = tableId,
                guestCount = guestCount.coerceAtLeast(1),
                sourceTerminalId = configRepo.current().terminalId,
                operatorId = sessionRepo.current()?.id ?: "",
                createdAt = now,
                updatedAt = now,
            )
            orderRepo.save(order)

            val newStatus = TableStateMachine.onSeated(table.status)
            tableRepo.updateStatusAndOrder(tableId, newStatus, orderId)
            cdsPhaseBroadcaster.broadcast(configRepo.current().terminalId, CdsPhase.ORDER, orderId)
            onOrderCreated(orderId)
        }
    }

    fun mergeTables(
        primaryTableId: String,
        secondaryTableId: String,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { mergeTablesUseCase(primaryTableId, secondaryTableId) }
                .onFailure { onError(it.message ?: "Merge failed") }
        }
    }

    fun splitTable(
        sourceOrderId: String,
        targetTableId: String,
        itemIdsToMove: List<String>,
        onOrderCreated: (orderId: String) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                splitTableUseCase(sourceOrderId, targetTableId, itemIdsToMove)
            }.onSuccess { newOrderId ->
                onOrderCreated(newOrderId)
            }.onFailure {
                onError(it.message ?: "Split failed")
            }
        }
    }

    fun createReservation(
        tableId: String,
        guestName: String,
        guestCount: Int,
        scheduledAt: Long,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                createReservationUseCase(tableId, guestName, guestCount, scheduledAt)
            }.onFailure {
                onError(it.message ?: "Create reservation failed")
            }
        }
    }

    fun cancelReservation(reservationId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = cancelReservationUseCase(reservationId)) {
                is CancelReservationUseCase.Result.Error -> onError(result.message)
                is CancelReservationUseCase.Result.Success -> Unit
            }
        }
    }

    fun rescheduleReservation(reservationId: String, newScheduledAt: Long) {
        viewModelScope.launch {
            val reservation = reservationRepo.getById(reservationId) ?: return@launch
            if (reservation.status != ReservationStatus.CONFIRMED) return@launch
            reservationRepo.save(reservation.copy(scheduledAt = newScheduledAt))
        }
    }

    fun seatReservation(
        reservationId: String,
        onOrderCreated: (orderId: String) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                seatReservationUseCase(
                    reservationId,
                    configRepo.current().terminalId,
                    sessionRepo.current()?.id ?: "",
                )
            }.onSuccess { orderId ->
                onOrderCreated(orderId)
            }.onFailure {
                onError(it.message ?: "Seat reservation failed")
            }
        }
    }

    fun transferTable(
        orderId: String,
        targetTableId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            when (val result = transferTableUseCase(
                TransferTableUseCase.Params(orderId = orderId, targetTableId = targetTableId)
            )) {
                is TransferTableUseCase.Result.Success -> onSuccess()
                is TransferTableUseCase.Result.Error -> onError(result.message)
            }
        }
    }

    // ── Table lifecycle ──────────────────────────────────────────────────────

    fun clearTable(tableId: String) {
        viewModelScope.launch {
            val table = tableRepo.getById(tableId) ?: return@launch
            val newStatus = com.restaurantpos.core.domain.statemachine.TableStateMachine.onCleared(table.status)
            tableRepo.updateStatusAndOrder(tableId, newStatus, null)
        }
    }

    fun markDirty(tableId: String) {
        viewModelScope.launch {
            val table = tableRepo.getById(tableId) ?: return@launch
            if (table.status == TableStatus.DIRTY) return@launch
            tableRepo.updateStatusAndOrder(tableId, TableStatus.DIRTY, table.currentOrderId)
        }
    }

    // ── Table notes ──────────────────────────────────────────────────────────

    fun updateTableNotes(orderId: String?, notes: String) {
        if (orderId == null) return
        viewModelScope.launch {
            val order = orderRepo.getById(orderId) ?: return@launch
            orderRepo.save(order.copy(orderNotes = notes.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    // ── Add / edit tables ────────────────────────────────────────────────────

    fun addTable(name: String, sectionId: String, capacity: Int) {
        viewModelScope.launch {
            val table = com.restaurantpos.core.model.Table(
                id = UUID.randomUUID().toString(),
                name = name,
                sectionId = sectionId,
                capacity = capacity,
                status = TableStatus.AVAILABLE,
                updatedAt = System.currentTimeMillis(),
            )
            tableRepo.save(table)
        }
    }

    fun updateTableConfig(tableId: String, name: String, sectionId: String, capacity: Int) {
        viewModelScope.launch {
            val table = tableRepo.getById(tableId) ?: return@launch
            tableRepo.save(table.copy(name = name, sectionId = sectionId, capacity = capacity))
        }
    }

    // ── Waitlist (in-memory only) ────────────────────────────────────────────

    data class WaitlistEntry(
        val id: String,
        val guestName: String,
        val guestCount: Int,
        val createdAt: Long = System.currentTimeMillis(),
    )

    private val _waitlist = MutableStateFlow<List<WaitlistEntry>>(emptyList())
    val waitlist: StateFlow<List<WaitlistEntry>> = _waitlist

    fun addToWaitlist(name: String, count: Int) {
        val entry = WaitlistEntry(id = UUID.randomUUID().toString(), guestName = name, guestCount = count)
        _waitlist.value = _waitlist.value + entry
    }

    fun removeFromWaitlist(id: String) {
        _waitlist.value = _waitlist.value.filter { it.id != id }
    }

    // ── Reservation helpers ──────────────────────────────────────────────────

    fun markNoShow(reservationId: String) {
        viewModelScope.launch {
            val res = reservationRepo.getById(reservationId) ?: return@launch
            if (res.status == ReservationStatus.CONFIRMED) {
                reservationRepo.save(res.copy(status = ReservationStatus.NO_SHOW))
            }
        }
    }

    fun editReservation(reservationId: String, guestName: String, guestCount: Int, scheduledAt: Long) {
        viewModelScope.launch {
            val res = reservationRepo.getById(reservationId) ?: return@launch
            if (res.status == ReservationStatus.CONFIRMED) {
                reservationRepo.save(res.copy(guestName = guestName, guestCount = guestCount, scheduledAt = scheduledAt))
            }
        }
    }
}
