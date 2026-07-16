package com.restaurantpos.feature.tables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.MenuImageTint
import com.restaurantpos.core.designsystem.PosBadgePopularBg
import com.restaurantpos.core.designsystem.PosBadgePopularFg
import com.restaurantpos.core.designsystem.PosBadgeSpicyBg
import com.restaurantpos.core.designsystem.PosBadgeSpicyFg
import com.restaurantpos.core.designsystem.PosBadgeVeganBg
import com.restaurantpos.core.designsystem.PosBadgeVeganFg
import com.restaurantpos.core.designsystem.PosCardBg
import com.restaurantpos.core.designsystem.PosChipBorder
import com.restaurantpos.core.designsystem.PosContentBg
import com.restaurantpos.core.designsystem.PosHairline
import com.restaurantpos.core.designsystem.PosOnlineDot
import com.restaurantpos.core.designsystem.PosShellBg
import com.restaurantpos.core.designsystem.PosTableReservedBg
import com.restaurantpos.core.designsystem.PosTableReservedFg
import com.restaurantpos.core.designsystem.PosTextMuted
import com.restaurantpos.core.designsystem.PosTextPrimary
import com.restaurantpos.core.designsystem.PosTextSecondary
import com.restaurantpos.core.designsystem.SunmiOrange
import com.restaurantpos.core.designsystem.SunmiOrangeContainer
import com.restaurantpos.core.designsystem.component.SyncStatusIndicator
import com.restaurantpos.feature.tables.R
import com.restaurantpos.core.model.Reservation
import com.restaurantpos.core.model.ReservationStatus
import com.restaurantpos.core.model.Table
import com.restaurantpos.core.model.TableStatus
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

private enum class TablesTab { FLOOR, RESERVATIONS }

@Composable
fun TablesScreen(
    onTableSeated: (orderId: String) -> Unit,
    onTableResumed: (orderId: String) -> Unit,
    onNavigateToReport: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMenu: () -> Unit = {},
    onNavigateToTakeaway: () -> Unit = {},
    pendingSyncCount: Int = 0,
    isOnline: Boolean = true,
    /** Toggles the app shell's left navigation sidebar (hamburger in the top bar). */
    onToggleNav: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TablesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reservationsUiState by viewModel.reservationsUiState.collectAsState()
    var selectedTab by remember { mutableStateOf(TablesTab.FLOOR) }

    Column(modifier = modifier.fillMaxSize().background(PosContentBg)) {
        // ── Header bar (matches mockup: hamburger, Dine In, Table, Search, Scan, Add Customer) ──
        Row(
            modifier = Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Menu, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(22.dp).clickableNoRipple { onToggleNav() })
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.tbl_dine_in), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(14.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder)) {
                Row(Modifier.clickableNoRipple {}.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TableRestaurant, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tbl_table_12), fontSize = 13.sp, color = PosTextPrimary)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.weight(1f).widthIn(min = 120.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tbl_search_tables), fontSize = 14.sp, color = PosTextMuted, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange) {
                Row(Modifier.clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tbl_scan), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder)) {
                Row(Modifier.clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tbl_add_customer), fontSize = 14.sp, color = PosTextPrimary, maxLines = 1, softWrap = false)
                }
            }
        }
        // ── Title + tabs ──
        Row(
            modifier = Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tables_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Spacer(Modifier.width(16.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder)) {
                Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SegPill(stringResource(R.string.tables_tab_floor), selectedTab == TablesTab.FLOOR) { selectedTab = TablesTab.FLOOR }
                    SegPill(stringResource(R.string.tables_tab_reservations), selectedTab == TablesTab.RESERVATIONS) { selectedTab = TablesTab.RESERVATIONS }
                }
            }
            Spacer(Modifier.weight(1f))
            // View toggle + zoom placeholder
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.tbl_view_floor_plan_label), fontSize = 12.sp, color = PosTextMuted)
                Text("100%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosTextSecondary)
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tbl_zoom_in), tint = PosTextMuted, modifier = Modifier.size(16.dp).clickableNoRipple {})
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tbl_zoom_out), tint = PosTextMuted, modifier = Modifier.size(16.dp).clickableNoRipple {})
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickable(onClick = onNavigateToTakeaway)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tables_takeaway), fontSize = 14.sp, color = PosTextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(color = PosHairline)

        when (selectedTab) {
            TablesTab.FLOOR -> FloorView(
                uiState = uiState,
                reservations = reservationsUiState.reservations,
                onTableSeated = onTableSeated,
                onTableResumed = onTableResumed,
                onNewReservation = { selectedTab = TablesTab.RESERVATIONS },
                viewModel = viewModel,
            )
            TablesTab.RESERVATIONS -> ReservationsView(
                uiState = reservationsUiState,
                tables = uiState.tables,
                timeZone = uiState.timeZone,
                onTableSeated = onTableSeated,
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorView(
    uiState: TablesUiState,
    reservations: List<Reservation>,
    onTableSeated: (orderId: String) -> Unit,
    onTableResumed: (orderId: String) -> Unit,
    onNewReservation: () -> Unit,
    viewModel: TablesViewModel,
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SunmiOrange) }
        return
    }

    var pendingSeatTableId by remember { mutableStateOf<String?>(null) }
    var transferOrderId by remember { mutableStateOf<String?>(null) }
    var transferErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedArea by remember { mutableStateOf<String?>(null) }
    // QSR dialogs
    var showMergeDialog by remember { mutableStateOf<String?>(null) }
    var showSplitDialog by remember { mutableStateOf<String?>(null) }
    var showQuickReserve by remember { mutableStateOf(false) }
    var quickReserveTableId by remember { mutableStateOf<String?>(null) }
    var showNotesDialog by remember { mutableStateOf<String?>(null) }
    var showWalkInDialog by remember { mutableStateOf(false) }
    var showWaitlistDialog by remember { mutableStateOf(false) }
    var showAddTableDialog by remember { mutableStateOf(false) }
    var tableSearchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<TableStatus?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }
    val detail by viewModel.detailState.collectAsState()
    val formatter = remember(detail.regionConfig) { AmountFormatter(detail.regionConfig) }

    if (pendingSeatTableId != null) {
        GuestCountDialog(
            onConfirm = { count -> viewModel.seatTable(pendingSeatTableId!!, count, onTableSeated); pendingSeatTableId = null },
            onDismiss = { pendingSeatTableId = null },
        )
    }
    if (transferErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { transferErrorMessage = null },
            title = { Text(stringResource(R.string.transfer_error_title)) },
            text = { Text(transferErrorMessage!!) },
            confirmButton = { TextButton(onClick = { transferErrorMessage = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    // Merge dialog
    showMergeDialog?.let { orderId ->
        MergeTablesDialog(
            currentTableId = detail.selectedTableId ?: "",
            tables = uiState.tables,
            onConfirm = { target -> viewModel.mergeTables(detail.selectedTableId!!, target, onError = { transferErrorMessage = it }); showMergeDialog = null },
            onDismiss = { showMergeDialog = null },
        )
    }
    // Split dialog
    showSplitDialog?.let { _ ->
        SplitTableDialog(
            items = detail.items,
            tables = uiState.tables,
            currentTableId = detail.selectedTableId ?: "",
            formatter = formatter,
            onConfirm = { itemIds, target -> viewModel.splitTable(detail.order!!.id, target, itemIds, onOrderCreated = onTableSeated, onError = { transferErrorMessage = it }); showSplitDialog = null },
            onDismiss = { showSplitDialog = null },
        )
    }
    // Quick Reserve dialog
    if (showQuickReserve) {
        NewReservationDialog(
            tables = uiState.tables.filter { it.status == TableStatus.AVAILABLE },
            preselectedTableId = quickReserveTableId,
            onCreate = { tableId, name, count, time -> viewModel.createReservation(tableId, name, count, time, onError = { transferErrorMessage = it }); showQuickReserve = false },
            onDismiss = { showQuickReserve = false },
        )
    }
    // Notes dialog
    showNotesDialog?.let { orderId ->
        AddNotesDialog(
            currentNotes = detail.order?.orderNotes ?: "",
            onSave = { notes -> viewModel.updateTableNotes(orderId, notes); showNotesDialog = null },
            onDismiss = { showNotesDialog = null },
        )
    }
    // Walk-In dialog
    if (showWalkInDialog) {
        WalkInDialog(
            tables = uiState.tables,
            onSeat = { tableId, count -> viewModel.seatTable(tableId, count, onTableSeated); showWalkInDialog = false },
            onDismiss = { showWalkInDialog = false },
        )
    }
    // Waitlist dialog
    if (showWaitlistDialog) {
        val wl by viewModel.waitlist.collectAsState()
        WaitlistDialog(
            entries = wl,
            onAdd = { name, count -> viewModel.addToWaitlist(name, count) },
            onRemove = { id -> viewModel.removeFromWaitlist(id) },
            onSeatFromWaitlist = { count -> showWaitlistDialog = false; showWalkInDialog = true },
            onDismiss = { showWaitlistDialog = false },
        )
    }
    // Add Table dialog
    if (showAddTableDialog) {
        AddTableDialog(
            areas = remember(uiState.tables) { uiState.tables.map { it.sectionId }.distinct().ifEmpty { listOf("Main Hall") } },
            onAdd = { name, area, cap -> viewModel.addTable(name, area, cap); showAddTableDialog = false },
            onDismiss = { showAddTableDialog = false },
        )
    }

    val areas = remember(uiState.tables) { uiState.tables.map { it.sectionId }.distinct() }
    val visibleTables = remember(uiState.tables, selectedArea, tableSearchQuery, statusFilter) {
        uiState.tables
            .filter { selectedArea == null || it.sectionId == selectedArea }
            .filter { tableSearchQuery.isBlank() || it.name.contains(tableSearchQuery, ignoreCase = true) }
            .filter { statusFilter == null || it.status == statusFilter }
    }
    val inTransferMode = transferOrderId != null
    val selectedTable = uiState.tables.firstOrNull { it.id == detail.selectedTableId }

    Column(Modifier.fillMaxSize()) {
        // Error banner
        uiState.errorMessage?.let { err ->
            Surface(color = PosBadgeSpicyFg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tbl_error_load) + ": $err", fontSize = 13.sp, color = PosBadgeSpicyFg, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { /* retry */ }) { Text(stringResource(R.string.tbl_error_retry), fontSize = 12.sp, color = SunmiOrange) }
                }
            }
        }
        Row(Modifier.fillMaxSize().weight(1f)) {
            // ── Left: area tabs + legend + floor plan ──
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Area tabs
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AreaTab(stringResource(R.string.tbl_area_all), selectedArea == null) { selectedArea = null }
                    areas.forEach { a -> AreaTab(areaDisplayName(a), selectedArea == a) { selectedArea = a } }
                }
                HorizontalDivider(color = PosHairline)
                // Legend + search + add table
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LegendDot(stringResource(R.string.tbl_status_available), PosOnlineDot) { statusFilter = if (statusFilter == TableStatus.AVAILABLE) null else TableStatus.AVAILABLE }
                        LegendDot(stringResource(R.string.tbl_status_occupied), SunmiOrange) { statusFilter = if (statusFilter == TableStatus.OCCUPIED) null else TableStatus.OCCUPIED }
                        LegendDot(stringResource(R.string.tbl_status_reserved), PosTableReservedFg) { statusFilter = if (statusFilter == TableStatus.RESERVED) null else TableStatus.RESERVED }
                        LegendDot(stringResource(R.string.tbl_status_dirty), PosTextMuted) { statusFilter = if (statusFilter == TableStatus.DIRTY) null else TableStatus.DIRTY }
                        LegendDot(stringResource(R.string.tbl_status_checkout), PosBadgePopularFg) { statusFilter = if (statusFilter == TableStatus.CHECKOUT) null else TableStatus.CHECKOUT }
                    }
                    IconButton(onClick = { showSearchBar = !showSearchBar }) { Icon(Icons.Filled.Search, contentDescription = null, tint = if (showSearchBar) SunmiOrange else PosTextMuted, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { showAddTableDialog = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tbl_add_table), tint = PosTextPrimary, modifier = Modifier.size(18.dp)) }
                }
                // Collapsible search bar
                if (showSearchBar) {
                    OutlinedTextField(
                        value = tableSearchQuery, onValueChange = { tableSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.tbl_search_hint)) },
                        trailingIcon = { if (tableSearchQuery.isNotEmpty()) IconButton(onClick = { tableSearchQuery = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tbl_search_clear), modifier = Modifier.size(16.dp)) } },
                    )
                }
                // Active filter indicator
                if (statusFilter != null) {
                    val (lbl, _) = tableStatusStyle(statusFilter!!)
                    Surface(color = SunmiOrange.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.tbl_filter_fmt, lbl), fontSize = 12.sp, color = SunmiOrange)
                            TextButton(onClick = { statusFilter = null }) { Text(stringResource(R.string.tbl_search_clear), fontSize = 12.sp, color = SunmiOrange) }
                        }
                    }
                }
                if (inTransferMode) {
                    Surface(color = SunmiOrangeContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.transfer_pick_target), fontSize = 13.sp, color = SunmiOrange)
                            TextButton(onClick = { transferOrderId = null }) { Text(stringResource(android.R.string.cancel), color = SunmiOrange) }
                        }
                    }
                }
                // Floor plan
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), color = PosContentBg, border = BorderStroke(1.dp, PosHairline)) {
                        Box(Modifier.fillMaxSize()) {
                            // Decorative bar counter
                            Box(Modifier.align(Alignment.TopStart).padding(12.dp).width(64.dp).height(160.dp).clip(RoundedCornerShape(10.dp)).background(MenuImageTint), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.tbl_bar), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PosTextMuted)
                            }
                            Text("🪴", fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                            Text("🪴", fontSize = 28.sp, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
                            // Tables
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(start = 88.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                visibleTables.forEach { table ->
                                    val isTarget = inTransferMode && table.status == TableStatus.AVAILABLE
                                    FloorTable(
                                        table = table,
                                        selected = table.id == detail.selectedTableId,
                                        isTransferTarget = isTarget,
                                        onClick = {
                                            if (inTransferMode) {
                                                if (isTarget) viewModel.transferTable(transferOrderId!!, table.id, onSuccess = { transferOrderId = null }, onError = { transferOrderId = null; transferErrorMessage = it })
                                            } else viewModel.selectTable(table)
                                        },
                                        onLongClick = {
                                            if (!inTransferMode && table.status in setOf(TableStatus.OCCUPIED, TableStatus.ORDERED, TableStatus.CHECKOUT)) {
                                                table.currentOrderId?.let { transferOrderId = it }
                                            }
                                        },
                                    )
                                }
                            }
                            Text(stringResource(R.string.tbl_main_entrance), fontSize = 11.sp, color = PosTextMuted, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                        }
                    }
                }
            }

            VerticalDivider(color = PosHairline)

            // ── Right: detail panel ──
            TableDetailPanel(
                table = selectedTable,
                order = detail.order,
                items = detail.items,
                formatter = formatter,
                onSeat = { selectedTable?.let { pendingSeatTableId = it.id } },
                onOpenOrder = { detail.order?.let { onTableResumed(it.id) } },
                onTransfer = { detail.order?.let { transferOrderId = it.id } },
                onMerge = { showMergeDialog = detail.order?.id },
                onSplit = { showSplitDialog = detail.order?.id },
                onReserve = { selectedTable?.let { quickReserveTableId = it.id; showQuickReserve = true } },
                onAddNotes = { showNotesDialog = detail.order?.id },
                onClearTable = { selectedTable?.let { viewModel.clearTable(it.id) } },
                modifier = Modifier.width(360.dp).fillMaxHeight(),
            )
        }

        // Bottom bar
        HorizontalDivider(color = PosHairline)
        Row(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BottomAction(Icons.Filled.CalendarMonth, stringResource(R.string.tbl_quick_reserve), Modifier.weight(1f)) { quickReserveTableId = null; showQuickReserve = true }
            BottomAction(Icons.Filled.Group, stringResource(R.string.tbl_waitlist), Modifier.weight(1f)) { showWaitlistDialog = true }
            BottomAction(Icons.Filled.PersonAdd, stringResource(R.string.tbl_walk_in), Modifier.weight(1f)) { showWalkInDialog = true }
            BottomAction(Icons.Filled.Settings, stringResource(R.string.tbl_table_settings), Modifier.weight(1f)) { showAddTableDialog = true }
        }
    }
}

@Composable
private fun AreaTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickableNoRipple(onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) SunmiOrange else PosTextSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(if (selected) 24.dp else 0.dp).background(SunmiOrange))
    }
}

@Composable
private fun LegendDot(label: String, color: Color, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.clickableNoRipple(onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = PosTextSecondary)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FloorTable(table: Table, selected: Boolean, isTransferTarget: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val (statusLabel, statusColor) = tableStatusStyle(table.status)
    val solid = table.status == TableStatus.ORDERED || table.status == TableStatus.OCCUPIED
    val bg = when {
        isTransferTarget -> SunmiOrangeContainer
        solid -> statusColor.copy(alpha = 0.12f)
        else -> PosCardBg
    }
    val circle = table.capacity <= 2
    Box(
        Modifier
            .size(if (circle) 92.dp else 104.dp, 92.dp)
            .clip(if (circle) CircleShape else RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) SunmiOrange else statusColor, if (circle) CircleShape else RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.height(3.dp))
            Text(table.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary, maxLines = 1)
            Text(stringResource(R.string.tbl_seats_fmt, table.capacity), fontSize = 10.sp, color = PosTextMuted)
            Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
        }
    }
}

@Composable
private fun TableDetailPanel(
    table: Table?,
    order: com.restaurantpos.core.model.Order?,
    items: List<com.restaurantpos.core.model.OrderItem>,
    formatter: AmountFormatter,
    onSeat: () -> Unit,
    onOpenOrder: () -> Unit,
    onTransfer: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onReserve: () -> Unit,
    onAddNotes: () -> Unit,
    onClearTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (table == null) {
        Box(modifier.background(PosShellBg), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.tbl_select_hint), fontSize = 14.sp, color = PosTextMuted)
        }
        return
    }
    when (table.status) {
        TableStatus.AVAILABLE -> AvailableDetail(table, onSeat, onReserve, modifier)
        TableStatus.OCCUPIED, TableStatus.ORDERED -> ActiveOrderDetail(table, order, items, formatter, onOpenOrder, onTransfer, onMerge, onSplit, onReserve, onAddNotes, modifier)
        TableStatus.CHECKOUT -> CheckoutDetail(table, order, items, formatter, onOpenOrder, onAddNotes, modifier)
        TableStatus.DIRTY -> DirtyDetail(table, onClearTable, modifier)
        TableStatus.RESERVED -> ReservedDetail(table, onSeat, onReserve, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableDetail(table: Table, onSeat: () -> Unit, onReserve: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(PosShellBg)) {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(table.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Surface(shape = RoundedCornerShape(6.dp), color = PosOnlineDot.copy(alpha = 0.14f)) {
                    Text(stringResource(R.string.tbl_status_available), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PosOnlineDot, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tbl_seats_fmt, table.capacity), fontSize = 13.sp, color = PosTextSecondary)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.tbl_detail_available_hint), fontSize = 14.sp, color = PosTextMuted)
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailAction(Icons.Filled.CalendarMonth, stringResource(R.string.tbl_reserve), onReserve)
            }
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(Icons.Filled.PersonAdd, stringResource(R.string.tables_seat_action), onSeat)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveOrderDetail(
    table: Table, order: com.restaurantpos.core.model.Order?, items: List<com.restaurantpos.core.model.OrderItem>,
    formatter: AmountFormatter, onOpenOrder: () -> Unit, onTransfer: () -> Unit, onMerge: () -> Unit,
    onSplit: () -> Unit, onReserve: () -> Unit, onAddNotes: () -> Unit, modifier: Modifier = Modifier,
) {
    val (statusLabel, statusColor) = tableStatusStyle(table.status)
    Column(modifier.background(PosShellBg)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(table.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.14f)) {
                        Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (order != null) Text(stringResource(R.string.tbl_open_for, openForText(order.createdAt)), fontSize = 12.sp, color = PosTextMuted)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column { Text(stringResource(R.string.tbl_guests_label), fontSize = 12.sp, color = PosTextMuted); Text("${order?.guestCount ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary) }
                    Column { Text(stringResource(R.string.tbl_customer_label), fontSize = 12.sp, color = PosTextMuted); Text(stringResource(R.string.tbl_walkin), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary) }
                }
                if (order != null && order.orderNotes.isNotBlank()) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(order.orderNotes, fontSize = 12.sp, color = PosTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (order != null) {
                item { HorizontalDivider(color = PosHairline); Spacer(Modifier.height(6.dp)); Text("${stringResource(R.string.tbl_current_order)}  #${order.id.takeLast(4).uppercase()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary) }
                items(items.take(4), key = { it.id }) { it2 ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("${it2.quantity}", fontSize = 13.sp, color = PosTextSecondary, modifier = Modifier.width(22.dp))
                        Text(it2.menuItemNameSnapshot.values.firstOrNull() ?: "", fontSize = 14.sp, color = PosTextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatter.format(it2.lineTotalMinorUnit), fontSize = 14.sp, color = PosTextPrimary)
                    }
                }
                if (items.size > 4) item { Text(stringResource(R.string.tbl_more_items, items.size - 4), fontSize = 12.sp, color = PosTextMuted) }
                item {
                    HorizontalDivider(color = PosHairline); Spacer(Modifier.height(4.dp))
                    DetailTotal(stringResource(R.string.tbl_subtotal), formatter.format(order.subtotalMinorUnit))
                    DetailTotal(stringResource(R.string.tbl_tax), formatter.format(order.taxTotalMinorUnit))
                    if (order.serviceChargeMinorUnit > 0) DetailTotal(stringResource(R.string.tbl_service), formatter.format(order.serviceChargeMinorUnit))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.tbl_total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                        Text(formatter.format(order.totalMinorUnit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    }
                }
            }
            item { FlowRowActions(onOpenOrder, onTransfer, onMerge, onSplit, onReserve, onAddNotes) }
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(Icons.Filled.PointOfSale, "${stringResource(R.string.tbl_start_checkout)}   ${formatter.format(order?.totalMinorUnit ?: 0L)}", onOpenOrder)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutDetail(table: Table, order: com.restaurantpos.core.model.Order?, items: List<com.restaurantpos.core.model.OrderItem>, formatter: AmountFormatter, onOpenOrder: () -> Unit, onAddNotes: () -> Unit, modifier: Modifier = Modifier) {
    val (statusLabel, statusColor) = tableStatusStyle(table.status)
    Column(modifier.background(PosShellBg)) {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(table.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.14f)) {
                    Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text(stringResource(R.string.tbl_detail_billing_hint), fontSize = 13.sp, color = PosTextMuted)
            if (order != null) {
                HorizontalDivider(color = PosHairline)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.tbl_total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    Text(formatter.format(order.totalMinorUnit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.tbl_open_order), onOpenOrder)
                DetailAction(Icons.Filled.EditNote, stringResource(R.string.tbl_add_notes), onAddNotes)
            }
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(Icons.Filled.PointOfSale, stringResource(R.string.tbl_start_checkout), onOpenOrder)
        }
    }
}

@Composable
private fun DirtyDetail(table: Table, onClearTable: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(PosShellBg)) {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(table.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Surface(shape = RoundedCornerShape(6.dp), color = PosTextMuted.copy(alpha = 0.14f)) {
                    Text(stringResource(R.string.tbl_status_dirty), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PosTextMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tbl_detail_dirty_hint), fontSize = 14.sp, color = PosTextMuted)
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(Icons.Filled.Check, stringResource(R.string.tbl_detail_clear_table), onClearTable)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReservedDetail(table: Table, onSeat: () -> Unit, onReserve: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(PosShellBg)) {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(table.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Surface(shape = RoundedCornerShape(6.dp), color = PosTableReservedBg.copy(alpha = 0.14f)) {
                    Text(stringResource(R.string.tbl_status_reserved), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PosTableReservedBg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tbl_seats_fmt, table.capacity), fontSize = 13.sp, color = PosTextSecondary)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailAction(Icons.Filled.PersonAdd, stringResource(R.string.tbl_detail_seat_now), onSeat)
                DetailAction(Icons.Filled.CalendarMonth, stringResource(R.string.tbl_reserve), onReserve)
            }
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(Icons.Filled.PersonAdd, stringResource(R.string.tbl_detail_seat_now), onSeat)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowActions(onOpenOrder: () -> Unit, onTransfer: () -> Unit, onMerge: () -> Unit = {}, onSplit: () -> Unit = {}, onReserve: () -> Unit = {}, onAddNotes: () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.tbl_open_order), onOpenOrder)
        DetailAction(Icons.Filled.SwapHoriz, stringResource(R.string.tbl_transfer), onTransfer)
        DetailAction(Icons.Filled.CallMerge, stringResource(R.string.tbl_merge), onMerge)
        DetailAction(Icons.Filled.CallSplit, stringResource(R.string.tbl_split), onSplit)
        DetailAction(Icons.Filled.CalendarMonth, stringResource(R.string.tbl_reserve), onReserve)
        DetailAction(Icons.Filled.EditNote, stringResource(R.string.tbl_add_notes), onAddNotes)
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = PosTextPrimary)
        }
    }
}

@Composable
private fun PrimaryButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = SunmiOrange, modifier = Modifier.fillMaxWidth().clickableNoRipple(onClick)) {
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun DetailTotal(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PosTextSecondary)
        Text(value, fontSize = 13.sp, color = PosTextPrimary)
    }
}

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = PosTextPrimary)
        }
    }
}

private fun openForText(createdAt: Long): String {
    val mins = ((System.currentTimeMillis() - createdAt) / 60000L).coerceAtLeast(0)
    return "%02d:%02d".format(mins / 60, mins % 60)
}

@Composable
private fun GuestCountDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var countText by remember { mutableStateOf("2") }
    val presets = listOf(1, 2, 3, 4, 5, 6, 8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tables_guest_count_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick-select chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { n ->
                        FilterChip(
                            selected = countText == n.toString(),
                            onClick = { countText = n.toString() },
                            label = { Text("$n") },
                        )
                    }
                }
                OutlinedTextField(
                    value = countText,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) countText = it },
                    label = { Text(stringResource(R.string.tables_guest_count_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(countText.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
                enabled = countText.isNotBlank(),
            ) { Text(stringResource(R.string.tables_seat)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ReservationsView(
    uiState: ReservationsUiState,
    tables: List<Table>,
    timeZone: String = "UTC",
    onTableSeated: (orderId: String) -> Unit,
    viewModel: TablesViewModel,
) {
    var showNewDialog by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.reservations, key = { it.id }) { reservation ->
                val table = tables.find { it.id == reservation.tableId }
                ReservationRow(
                    reservation = reservation,
                    tableName = table?.name ?: reservation.tableId,
                    onSeat = {
                        viewModel.seatReservation(reservation.id, onTableSeated)
                    },
                    onCancel = {
                        viewModel.cancelReservation(reservation.id)
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.tables_new_reservation)) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = { showNewDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showNewDialog) {
        NewReservationDialog(
            tables = tables.filter { it.status == TableStatus.AVAILABLE },
            timeZone = timeZone,
            onDismiss = { showNewDialog = false },
            onCreate = { tableId, guestName, guestCount, scheduledAt ->
                viewModel.createReservation(tableId, guestName, guestCount, scheduledAt)
                showNewDialog = false
            },
        )
    }
}

@Composable
private fun ReservationRow(
    reservation: Reservation,
    tableName: String,
    onSeat: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val (badgeBg, badgeFg) = when (reservation.status) {
        ReservationStatus.CONFIRMED -> PosTableReservedBg to PosTableReservedFg
        ReservationStatus.SEATED -> PosBadgeVeganBg to PosBadgeVeganFg
        ReservationStatus.NO_SHOW -> PosBadgeSpicyBg to PosBadgeSpicyFg
        ReservationStatus.CANCELLED -> PosContentBg to PosTextMuted
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PosCardBg,
        border = BorderStroke(1.dp, PosHairline),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = timeFormat.format(Date(reservation.scheduledAt)),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PosTextPrimary,
                modifier = Modifier.width(54.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(reservation.guestName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                Text(stringResource(R.string.tbl_guest_table_fmt, reservation.guestCount, tableName), fontSize = 13.sp, color = PosTextSecondary)
            }
            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = when (reservation.status) {
                        ReservationStatus.CONFIRMED -> stringResource(R.string.res_status_confirmed)
                        ReservationStatus.SEATED -> stringResource(R.string.res_status_seated)
                        ReservationStatus.NO_SHOW -> stringResource(R.string.res_status_no_show)
                        ReservationStatus.CANCELLED -> stringResource(R.string.res_status_cancelled)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (reservation.status == ReservationStatus.CONFIRMED) {
                Surface(shape = RoundedCornerShape(8.dp), color = SunmiOrange, modifier = Modifier.clickable(onClick = onSeat)) {
                    Text(stringResource(R.string.tables_seat_action), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Surface(shape = RoundedCornerShape(8.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickable(onClick = onCancel)) {
                    Text(stringResource(R.string.tables_cancel_reservation), fontSize = 13.sp, color = PosBadgeSpicyFg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReservationDialog(
    tables: List<Table>,
    timeZone: String = "UTC",
    preselectedTableId: String? = null,
    onDismiss: () -> Unit,
    onCreate: (tableId: String, guestName: String, guestCount: Int, scheduledAt: Long) -> Unit,
) {
    var selectedTableId by remember { mutableStateOf(preselectedTableId ?: tables.firstOrNull()?.id ?: "") }
    var guestName by remember { mutableStateOf("") }
    var guestCountText by remember { mutableStateOf("2") }
    var timeText by remember { mutableStateOf("19:00") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.tables_new_reservation),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    label = { Text(stringResource(R.string.tables_reservation_guest_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = guestCountText,
                    onValueChange = { guestCountText = it },
                    label = { Text(stringResource(R.string.tables_reservation_guest_count)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.tables_reservation_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Table selector
                if (tables.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedTable = tables.find { it.id == selectedTableId }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedTable?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.tables_reservation_table)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            tables.forEach { table ->
                                DropdownMenuItem(
                                    text = { Text(table.name) },
                                    onClick = {
                                        selectedTableId = table.id
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.tables_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val guestCount = guestCountText.toIntOrNull() ?: return@Button
                            val parts = timeText.split(":")
                            if (parts.size != 2) return@Button
                            val hour = parts[0].toIntOrNull() ?: return@Button
                            val minute = parts[1].toIntOrNull() ?: return@Button
                            val zone = ZoneId.of(timeZone)
                            val today = LocalDate.now(zone)
                            val scheduledAt = today.atTime(LocalTime.of(hour, minute))
                                .atZone(zone).toInstant().toEpochMilli()
                            if (selectedTableId.isNotBlank() && guestName.isNotBlank()) {
                                onCreate(selectedTableId, guestName, guestCount, scheduledAt)
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(stringResource(R.string.tables_create))
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TableCard(
    table: Table,
    nextReservationAt: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isTransferTarget: Boolean = false,
) {
    // Status → (background, foreground, accent dot). Solid orange for ORDERED to draw the eye.
    val solidOrdered = table.status == TableStatus.ORDERED
    val bgColor by animateColorAsState(
        targetValue = when {
            isTransferTarget -> SunmiOrangeContainer
            table.status == TableStatus.ORDERED -> SunmiOrange
            else -> PosCardBg
        },
        animationSpec = spring(),
        label = "tableColor",
    )
    val border = when {
        isTransferTarget -> BorderStroke(1.5.dp, SunmiOrange)
        solidOrdered -> null
        else -> BorderStroke(1.dp, PosHairline)
    }
    val nameColor = if (solidOrdered) Color.White else PosTextPrimary
    val (statusLabel, statusColor) = tableStatusStyle(table.status)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = border,
        modifier = Modifier
            .aspectRatio(1.25f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (solidOrdered) Color.White else statusColor))
                Spacer(Modifier.weight(1f))
                if (table.capacity > 0) {
                    Text(stringResource(R.string.tbl_capacity_short_fmt, table.capacity), fontSize = 12.sp, color = if (solidOrdered) Color.White.copy(alpha = 0.85f) else PosTextMuted)
                }
            }
            Text(table.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = nameColor)
            Column {
                Text(
                    statusLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (solidOrdered) Color.White.copy(alpha = 0.9f) else statusColor,
                )
                if (nextReservationAt != null) {
                    Text(
                        stringResource(R.string.tbl_next_fmt, timeFormat.format(Date(nextReservationAt))),
                        fontSize = 11.sp,
                        color = if (solidOrdered) Color.White.copy(alpha = 0.7f) else PosTextMuted,
                    )
                }
            }
        }
    }
}

// ── Merge Tables Dialog ─────────────────────────────────────────────────────

@Composable
private fun MergeTablesDialog(
    currentTableId: String,
    tables: List<Table>,
    onConfirm: (targetTableId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mergeable = tables.filter { it.id != currentTableId && it.status in setOf(TableStatus.OCCUPIED, TableStatus.ORDERED) }
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_merge_title)) },
        text = {
            if (mergeable.isEmpty()) {
                Text(stringResource(R.string.tbl_merge_no_targets))
            } else {
                LazyColumn { items(mergeable) { t ->
                    val (lbl, _) = tableStatusStyle(t.status)
                    Surface(shape = RoundedCornerShape(10.dp), color = if (selectedTarget == t.id) SunmiOrange.copy(alpha = 0.08f) else Color.Transparent, border = BorderStroke(1.dp, if (selectedTarget == t.id) SunmiOrange else PosHairline), modifier = Modifier.fillMaxWidth().clickableNoRipple { selectedTarget = t.id }.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PosTextPrimary); Text(stringResource(R.string.tbl_status_seats_fmt, lbl, t.capacity), fontSize = 12.sp, color = PosTextSecondary) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }}
            }
        },
        confirmButton = {
            TextButton(onClick = { selectedTarget?.let(onConfirm) }, enabled = selectedTarget != null) { Text(stringResource(R.string.tbl_merge_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tables_cancel)) } },
    )
}

// ── Split Table Dialog ──────────────────────────────────────────────────────

@Composable
private fun SplitTableDialog(
    items: List<com.restaurantpos.core.model.OrderItem>,
    tables: List<Table>,
    currentTableId: String,
    formatter: AmountFormatter,
    onConfirm: (itemIds: List<String>, targetTableId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var targetTable by remember { mutableStateOf<String?>(null) }
    val available = tables.filter { it.id != currentTableId && it.status == TableStatus.AVAILABLE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_split_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tbl_split_hint), fontSize = 13.sp, color = PosTextSecondary)
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickableNoRipple {
                        selectedItems = if (item.id in selectedItems) selectedItems - item.id else selectedItems + item.id
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.id in selectedItems, onCheckedChange = { checked -> selectedItems = if (checked) selectedItems + item.id else selectedItems - item.id })
                        Text(item.menuItemNameSnapshot.values.firstOrNull() ?: "", Modifier.weight(1f), fontSize = 14.sp)
                        Text(formatter.format(item.lineTotalMinorUnit), fontSize = 13.sp, color = PosTextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tbl_split_target), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                available.forEach { t ->
                    Surface(shape = RoundedCornerShape(8.dp), color = if (targetTable == t.id) SunmiOrange.copy(alpha = 0.08f) else PosContentBg, border = BorderStroke(1.dp, if (targetTable == t.id) SunmiOrange else PosHairline), modifier = Modifier.fillMaxWidth().clickableNoRipple { targetTable = t.id }.padding(10.dp)) {
                        Text(stringResource(R.string.tbl_table_seats_fmt, t.name, t.capacity), fontSize = 13.sp, color = PosTextPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { val tgt = targetTable; if (tgt != null && selectedItems.isNotEmpty()) onConfirm(selectedItems.toList(), tgt) }, enabled = selectedItems.isNotEmpty() && targetTable != null) { Text(stringResource(R.string.tbl_split_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tables_cancel)) } },
    )
}

// ── Walk-In Dialog ──────────────────────────────────────────────────────────

@Composable
private fun WalkInDialog(
    tables: List<Table>,
    onSeat: (tableId: String, guestCount: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var guestCount by remember { mutableStateOf(2) }
    val available = tables.filter { it.status == TableStatus.AVAILABLE }
    var selectedTable by remember { mutableStateOf(available.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_walkin_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tables_guest_count_label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { n ->
                        Surface(shape = RoundedCornerShape(8.dp), color = if (guestCount == n) SunmiOrange else PosContentBg, border = BorderStroke(1.dp, if (guestCount == n) SunmiOrange else PosHairline), modifier = Modifier.clickableNoRipple { guestCount = n }) {
                            Text("$n", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = if (guestCount == n) FontWeight.Bold else FontWeight.Normal, color = if (guestCount == n) Color.White else PosTextPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.tbl_walkin_table_hint), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                available.forEach { t ->
                    Surface(shape = RoundedCornerShape(8.dp), color = if (selectedTable == t.id) SunmiOrange.copy(alpha = 0.08f) else PosContentBg, border = BorderStroke(1.dp, if (selectedTable == t.id) SunmiOrange else PosHairline), modifier = Modifier.fillMaxWidth().clickableNoRipple { selectedTable = t.id }.padding(10.dp)) {
                        Text(stringResource(R.string.tbl_table_seats_fmt, t.name, t.capacity), fontSize = 13.sp, color = PosTextPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { val tbl = selectedTable; if (tbl != null) onSeat(tbl, guestCount) }, enabled = selectedTable != null) { Text(stringResource(R.string.tbl_walkin_seat)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tables_cancel)) } },
    )
}

// ── Waitlist Dialog ──────────────────────────────────────────────────────────

@Composable
private fun WaitlistDialog(
    entries: List<TablesViewModel.WaitlistEntry>,
    onAdd: (name: String, count: Int) -> Unit,
    onRemove: (id: String) -> Unit,
    onSeatFromWaitlist: (guestCount: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newCount by remember { mutableStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_waitlist_title)) },
        text = {
            Column {
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.tbl_waitlist_empty), fontSize = 14.sp, color = PosTextMuted, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    entries.forEach { e ->
                        val mins = ((System.currentTimeMillis() - e.createdAt) / 60000L).coerceAtLeast(0)
                        Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.guestName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                                    Text(stringResource(R.string.tbl_waitlist_guest_fmt, e.guestCount, stringResource(R.string.tbl_waitlist_waiting, "$mins")), fontSize = 12.sp, color = PosTextSecondary)
                                }
                                TextButton(onClick = { onSeatFromWaitlist(e.guestCount) }) { Text(stringResource(R.string.tbl_waitlist_seat), fontSize = 12.sp) }
                                IconButton(onClick = { onRemove(e.id) }) { Icon(Icons.Filled.Close, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = PosHairline)
                }
                Text(stringResource(R.string.tbl_waitlist_add), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text(stringResource(R.string.tbl_waitlist_name)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { n ->
                        Surface(shape = RoundedCornerShape(8.dp), color = if (newCount == n) SunmiOrange else PosContentBg, border = BorderStroke(1.dp, if (newCount == n) SunmiOrange else PosHairline), modifier = Modifier.clickableNoRipple { newCount = n }) {
                            Text("$n", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, color = if (newCount == n) Color.White else PosTextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newName.isNotBlank()) { onAdd(newName, newCount); newName = "" } }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.tbl_waitlist_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tbl_quick_reserve)) } },
    )
}

// ── Add Table Dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTableDialog(
    areas: List<String>,
    onAdd: (name: String, sectionId: String, capacity: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedArea by remember { mutableStateOf(areas.firstOrNull() ?: "Main Hall") }
    var capacity by remember { mutableStateOf(4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_add_table_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.tbl_add_table_name)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tbl_add_table_section), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    areas.forEach { a ->
                        Surface(shape = RoundedCornerShape(8.dp), color = if (selectedArea == a) SunmiOrange else PosContentBg, border = BorderStroke(1.dp, if (selectedArea == a) SunmiOrange else PosHairline), modifier = Modifier.clickableNoRipple { selectedArea = a }) {
                            Text(areaDisplayName(a), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, color = if (selectedArea == a) Color.White else PosTextPrimary)
                        }
                    }
                }
                Text(stringResource(R.string.tbl_add_table_capacity), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8, 10).forEach { n ->
                        Surface(shape = RoundedCornerShape(8.dp), color = if (capacity == n) SunmiOrange else PosContentBg, border = BorderStroke(1.dp, if (capacity == n) SunmiOrange else PosHairline), modifier = Modifier.clickableNoRipple { capacity = n }) {
                            Text("$n", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, color = if (capacity == n) Color.White else PosTextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name, selectedArea, capacity) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.tbl_add_table_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tables_cancel)) } },
    )
}

@Composable
private fun areaDisplayName(area: String): String = when (area.lowercase().replace('_', '-').replace(' ', '-')) {
    "indoor", "main-hall" -> stringResource(R.string.tbl_area_indoor)
    "outdoor" -> stringResource(R.string.tbl_area_outdoor)
    else -> area.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

// ── Add Notes Dialog ────────────────────────────────────────────────────────

@Composable
private fun AddNotesDialog(
    currentNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by remember { mutableStateOf(currentNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tbl_notes_title)) },
        text = {
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text(stringResource(R.string.tbl_notes_hint)) })
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes) }) { Text(stringResource(R.string.tbl_notes_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tables_cancel)) } },
    )
}

/** Status label + accent color for a floor tile, from the shared token palette. */
@Composable
private fun tableStatusStyle(status: TableStatus): Pair<String, Color> = when (status) {
    TableStatus.AVAILABLE -> stringResource(R.string.tbl_status_available) to PosOnlineDot
    TableStatus.OCCUPIED -> stringResource(R.string.tbl_status_occupied) to SunmiOrange
    TableStatus.ORDERED -> stringResource(R.string.tbl_status_ordered) to SunmiOrange
    TableStatus.CHECKOUT -> stringResource(R.string.tbl_status_checkout) to PosBadgePopularFg
    TableStatus.DIRTY -> stringResource(R.string.tbl_status_dirty) to PosTextMuted
    TableStatus.RESERVED -> stringResource(R.string.tbl_status_reserved) to PosTableReservedFg
}

@Composable
private fun SegPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PosShellBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) PosTextPrimary else PosTextSecondary,
        )
    }
}


@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
