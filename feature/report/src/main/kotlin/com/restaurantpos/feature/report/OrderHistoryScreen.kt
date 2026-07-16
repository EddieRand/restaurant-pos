package com.restaurantpos.feature.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.*
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import java.text.SimpleDateFormat
import java.util.*

private val rowTimeFmt = SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE)
private val detailDateFmt = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
private const val CASHIER_DISPLAY_LOCALE = "zh-CN"

@Composable
fun OrderHistoryScreen(
    onBack: () -> Unit,
    onOrderTap: (orderId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OrderHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }
    val locale = CASHIER_DISPLAY_LOCALE

    LaunchedEffect(uiState.query) { viewModel.search() }

    Column(modifier = modifier.fillMaxSize().background(PosContentBg)) {
        // Header: title + New Order
        Row(
            modifier = Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.orders_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.clickableNoRipple { uiState.selectedOrderId?.let(onOrderTap) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.orders_new_order), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        HorizontalDivider(color = PosHairline)

        Row(Modifier.fillMaxSize().weight(1f)) {
            // ── Left: tabs + filters + table ─────────────────────────────────────
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Tabs
                Row(
                    Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    OrdersTab(stringResource(R.string.orders_tab_all), uiState.tab == OrdersTab.ALL) { viewModel.setTab(OrdersTab.ALL) }
                    OrdersTab(stringResource(R.string.orders_tab_open), uiState.tab == OrdersTab.OPEN) { viewModel.setTab(OrdersTab.OPEN) }
                    OrdersTab(stringResource(R.string.orders_tab_completed), uiState.tab == OrdersTab.COMPLETED) { viewModel.setTab(OrdersTab.COMPLETED) }
                    OrdersTab(stringResource(R.string.orders_tab_voided), uiState.tab == OrdersTab.VOIDED) { viewModel.setTab(OrdersTab.VOIDED) }
                }
                HorizontalDivider(color = PosHairline)

                // Filters
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(uiState.query, viewModel::setQuery, Modifier.width(280.dp))
                    FilterChip(stringResource(R.string.ohs_filter_status))
                    FilterChip(stringResource(R.string.ohs_filter_order_type))
                    FilterChip(stringResource(R.string.ohs_filter_date))
                    FilterChip(stringResource(R.string.ohs_filter_staff))
                }

                uiState.error?.let { err ->
                    Surface(color = PosBadgeSpicyBg, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(err, color = PosBadgeSpicyFg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) }
                        }
                    }
                }

                val rows = uiState.filtered
                when {
                    uiState.isLoading && uiState.results.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SunmiOrange) }
                    uiState.hasSearched && rows.isEmpty() -> EmptyState()
                    else -> {
                        TableHeader()
                        HorizontalDivider(color = PosHairline)
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(rows, key = { it.id }) { order ->
                                OrderTableRow(
                                    order = order,
                                    locale = locale,
                                    formatter = formatter,
                                    selected = order.id == uiState.selectedOrderId,
                                    onClick = { viewModel.select(order.id) },
                                )
                                HorizontalDivider(color = PosHairline)
                            }
                        }
                    }
                }
            }

            VerticalDivider(color = PosHairline)

            // ── Right: detail panel ──────────────────────────────────────────────
            OrderDetailPanel(
                order = uiState.selectedOrder,
                items = uiState.selectedItems,
                locale = locale,
                formatter = formatter,
                onOpen = { uiState.selectedOrderId?.let(onOrderTap) },
                modifier = Modifier.width(400.dp).fillMaxHeight(),
            )
        }
    }
}

// ── Table ───────────────────────────────────────────────────────────────────

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().background(PosContentBg).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(stringResource(R.string.ohs_col_order), 1.1f)
        HeaderCell(stringResource(R.string.ohs_col_type), 1f)
        HeaderCell(stringResource(R.string.ohs_col_customer), 1.7f)
        HeaderCell(stringResource(R.string.ohs_col_status), 1f)
        HeaderCell(stringResource(R.string.ohs_col_time), 0.8f)
        HeaderCell(stringResource(R.string.ohs_col_amount), 0.9f)
        HeaderCell(stringResource(R.string.ohs_col_staff), 1.2f)
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosTextMuted, modifier = Modifier.weight(weight))
}

@Composable
private fun OrderTableRow(
    order: Order,
    locale: String,
    formatter: AmountFormatter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) SunmiOrangeContainer else PosShellBg)
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${order.id.takeLast(4).uppercase()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary, modifier = Modifier.weight(1.1f))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(typeIcon(order.type), contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(typeLabel(order.type), fontSize = 13.sp, color = PosTextPrimary, maxLines = 1)
        }
        Column(Modifier.weight(1.7f)) {
            Text(tableOrType(order), fontSize = 14.sp, color = PosTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.ohs_walkin), fontSize = 12.sp, color = PosTextMuted)
        }
        Box(Modifier.weight(1f)) { StatusBadge(order.status) }
        Text(rowTimeFmt.format(Date(order.createdAt)), fontSize = 13.sp, color = PosTextSecondary, modifier = Modifier.weight(0.8f))
        Text(formatter.format(order.totalMinorUnit), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary, modifier = Modifier.weight(0.9f))
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
            StaffAvatar(order.operatorId, 26)
            Spacer(Modifier.width(8.dp))
            Text(staffName(order.operatorId), fontSize = 13.sp, color = PosTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PosTextMuted, modifier = Modifier.width(24.dp))
    }
}

// ── Detail panel ──────────────────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(
    order: Order?,
    items: List<OrderItem>,
    locale: String,
    formatter: AmountFormatter,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (order == null) {
        Box(modifier.background(PosShellBg), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ohs_select_hint), fontSize = 14.sp, color = PosTextMuted)
        }
        return
    }
    Column(modifier.background(PosShellBg)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${order.id.takeLast(4).uppercase()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(order.status)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(order.type), contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${typeLabel(order.type)} · ${tableOrType(order)} · ${stringResource(R.string.ohs_walkin)}", fontSize = 13.sp, color = PosTextSecondary)
                }
                Spacer(Modifier.height(2.dp))
                Text("${detailDateFmt.format(Date(order.createdAt))} · ${staffName(order.operatorId)}", fontSize = 12.sp, color = PosTextMuted)
            }
            // Items
            item { SectionLabel(stringResource(R.string.ohs_items)) }
            if (items.isEmpty()) {
                item { Text("—", fontSize = 13.sp, color = PosTextMuted) }
            } else {
                items(items, key = { it.id }) { it2 ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("${it2.quantity}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosTextSecondary, modifier = Modifier.width(24.dp))
                        Text(it2.menuItemNameSnapshot.values.firstOrNull() ?: "", fontSize = 14.sp, color = PosTextPrimary, modifier = Modifier.weight(1f))
                        Text(formatter.format(it2.lineTotalMinorUnit), fontSize = 14.sp, color = PosTextPrimary)
                    }
                }
            }
            if (order.orderNotes.isNotBlank()) {
                item {
                    SectionLabel(stringResource(R.string.ohs_notes))
                    Text(order.orderNotes, fontSize = 13.sp, color = PosTextSecondary)
                }
            }
            // Totals
            item {
                HorizontalDivider(color = PosHairline)
                Spacer(Modifier.height(8.dp))
                TotalLine(stringResource(R.string.ohs_subtotal), formatter.format(order.subtotalMinorUnit))
                TotalLine(stringResource(R.string.ohs_tax), formatter.format(order.taxTotalMinorUnit))
                if (order.serviceChargeMinorUnit > 0) TotalLine(stringResource(R.string.ohs_service), formatter.format(order.serviceChargeMinorUnit))
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.ohs_total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    Text(formatter.format(order.totalMinorUnit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ohs_payment), fontSize = 13.sp, color = PosTextSecondary)
                    val paid = order.status == OrderStatus.CLOSED
                    Surface(shape = RoundedCornerShape(6.dp), color = if (paid) PosBadgeVeganBg else PosBadgeSpicyBg) {
                        Text(
                            stringResource(if (paid) R.string.ohs_paid else R.string.ohs_unpaid),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (paid) PosBadgeVeganFg else PosBadgeSpicyFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            // Activity
            item { SectionLabel(stringResource(R.string.ohs_activity)) }
            item { ActivityTimeline(order) }
        }

        // Action buttons
        HorizontalDivider(color = PosHairline)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.ohs_btn_open), Modifier.weight(1f), onOpen)
                GhostButton(Icons.Filled.Print, stringResource(R.string.ohs_btn_print), Modifier.weight(1f)) {}
                GhostButton(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ohs_btn_send_again), Modifier.weight(1f)) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosBadgeSpicyFg.copy(alpha = 0.4f)), modifier = Modifier.weight(1f).clickableNoRipple {}) {
                    Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, contentDescription = null, tint = PosBadgeSpicyFg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ohs_btn_void), fontSize = 14.sp, color = PosBadgeSpicyFg)
                    }
                }
                Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.weight(1f).clickableNoRipple(onOpen)) {
                    Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ohs_btn_settle), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTimeline(order: Order) {
    data class Evt(val labelRes: Int, val done: Boolean)
    val s = order.status
    val placedOrBeyond = s in setOf(OrderStatus.PLACED, OrderStatus.READY, OrderStatus.CLOSED)
    val events = if (s == OrderStatus.VOIDED) {
        listOf(Evt(R.string.ohs_evt_created, true), Evt(R.string.ohs_evt_voided, true))
    } else {
        listOf(
            Evt(R.string.ohs_evt_created, true),
            Evt(R.string.ohs_evt_sent, placedOrBeyond),
            Evt(R.string.ohs_evt_preparing, placedOrBeyond),
            Evt(R.string.ohs_evt_ready, s == OrderStatus.READY || s == OrderStatus.CLOSED),
            Evt(if (s == OrderStatus.CLOSED) R.string.ohs_evt_paid else R.string.ohs_evt_payment_pending, s == OrderStatus.CLOSED),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        events.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (e.done) SunmiOrange else PosChipBorder))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(e.labelRes), fontSize = 13.sp, color = if (e.done) PosTextPrimary else PosTextMuted)
            }
        }
    }
}

// ── Small pieces ──────────────────────────────────────────────────────────────

@Composable
private fun OrdersTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickableNoRipple(onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) SunmiOrange else PosTextSecondary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(if (selected) 28.dp else 0.dp).background(SunmiOrange))
    }
}

@Composable
private fun FilterChip(label: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple {}) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = PosTextPrimary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GhostButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = PosTextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun StaffAvatar(operatorId: String, size: Int) {
    val initial = staffName(operatorId).firstOrNull()?.uppercase() ?: "?"
    Box(Modifier.size(size.dp).clip(CircleShape).background(SunmiOrangeContainer), contentAlignment = Alignment.Center) {
        Text(initial, fontSize = (size / 2.4).sp, fontWeight = FontWeight.Bold, color = SunmiOrange)
    }
}

private fun staffName(operatorId: String): String =
    operatorId.removePrefix("user-").replaceFirstChar { it.uppercase() }.ifBlank { "—" }

@Composable
private fun tableOrType(order: Order): String =
    order.tableId?.let { stringResource(R.string.ohs_table_fmt, it.removePrefix("table-").removePrefix("T")) } ?: when (order.type) {
        OrderType.TAKEAWAY -> stringResource(R.string.ohs_type_takeaway)
        OrderType.DELIVERY -> stringResource(R.string.ohs_type_delivery)
        else -> stringResource(R.string.ohs_type_dine_in)
    }

private fun typeIcon(type: OrderType): ImageVector = when (type) {
    OrderType.DINE_IN -> Icons.Filled.TableRestaurant
    OrderType.TAKEAWAY -> Icons.Filled.ShoppingBag
    OrderType.DELIVERY -> Icons.Filled.DeliveryDining
}

@Composable
private fun typeLabel(type: OrderType): String = when (type) {
    OrderType.DINE_IN -> stringResource(R.string.ohs_type_dine_in)
    OrderType.TAKEAWAY -> stringResource(R.string.ohs_type_takeaway)
    OrderType.DELIVERY -> stringResource(R.string.ohs_type_delivery)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PosTextSecondary)
        Text(value, fontSize = 13.sp, color = PosTextPrimary)
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.ohs_search_orders), fontSize = 13.sp, color = PosTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = PosTextPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SunmiOrange),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.CLOSED -> PosBadgeVeganBg to PosBadgeVeganFg
        OrderStatus.VOIDED -> PosBadgeSpicyBg to PosBadgeSpicyFg
        OrderStatus.IN_PROGRESS, OrderStatus.PLACED, OrderStatus.READY -> PosBadgePopularBg to PosBadgePopularFg
        OrderStatus.DRAFT -> PosChipBg to PosTextSecondary
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg, border = if (status == OrderStatus.DRAFT) BorderStroke(1.dp, PosChipBorder) else null) {
        Text(statusLabel(status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.order_history_empty), fontSize = 15.sp, color = PosTextSecondary)
        }
    }
}

@Composable
private fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.DRAFT -> stringResource(R.string.ohs_status_draft)
    OrderStatus.IN_PROGRESS -> stringResource(R.string.ohs_status_in_progress)
    OrderStatus.PLACED -> stringResource(R.string.ohs_status_placed)
    OrderStatus.READY -> stringResource(R.string.ohs_status_ready)
    OrderStatus.CLOSED -> stringResource(R.string.ohs_status_completed)
    OrderStatus.VOIDED -> stringResource(R.string.ohs_status_voided)
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
