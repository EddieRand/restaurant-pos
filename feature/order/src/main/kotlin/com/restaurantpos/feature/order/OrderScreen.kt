package com.restaurantpos.feature.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.PosBadgePopularBg
import com.restaurantpos.core.designsystem.PosBadgePopularFg
import com.restaurantpos.core.designsystem.PosOnlineDot
import com.restaurantpos.core.designsystem.PosBadgeSpicyBg
import com.restaurantpos.core.designsystem.PosBadgeSpicyFg
import com.restaurantpos.core.designsystem.PosBadgeVeganBg
import com.restaurantpos.core.designsystem.PosBadgeVeganFg
import com.restaurantpos.core.designsystem.PosCardBg
import com.restaurantpos.core.designsystem.PosChipBg
import com.restaurantpos.core.designsystem.PosChipBorder
import com.restaurantpos.core.designsystem.PosContentBg
import com.restaurantpos.core.designsystem.PosHairline
import com.restaurantpos.core.designsystem.PosShellBg
import com.restaurantpos.core.designsystem.PosTextMuted
import com.restaurantpos.core.designsystem.PosTextPrimary
import com.restaurantpos.core.designsystem.PosTextSecondary
import com.restaurantpos.core.designsystem.SunmiOrange
import com.restaurantpos.core.designsystem.SunmiOrangeContainer
import com.restaurantpos.core.designsystem.MenuImageTint
import com.restaurantpos.core.model.*
import com.restaurantpos.core.model.Modifier as DomainModifier

/** Picks the best display name from a multilingual map using the given BCP-47 locale. */
private fun Map<String, String>.localeName(locale: String): String {
    val lang = locale.substringBefore('-')
    return this[lang] ?: this[locale] ?: this["en"] ?: values.firstOrNull() ?: ""
}

@Composable
fun OrderScreen(
    onOrderPlaced: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set false in kiosk/self-service contexts to hide void, split, and order-type controls. */
    showStaffActions: Boolean = true,
    /** Called when the cashier holds/parks the order — navigate away to take the next one. */
    onOrderHeld: () -> Unit = {},
    /**
     * Called after a QSR payment success is dismissed (New Order tapped, or the 5s auto-timeout
     * fires) — MUST navigate to a freshly created order. Regression: this used to be a no-op,
     * so the cashier stayed on the same now-CLOSED orderId; adding any item to it and trying to
     * check out again failed with "Cannot place order in status CLOSED" — found via real-device
     * regression testing.
     */
    onStartNewOrder: () -> Unit = {},
    viewModel: OrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }

    LaunchedEffect(uiState.orderPlaced) {
        if (uiState.orderPlaced) onOrderPlaced(uiState.orderId)
    }
    LaunchedEffect(uiState.orderHeld) {
        if (uiState.orderHeld) onOrderHeld()
    }

    // Modifier bottom-sheet
    if (uiState.pendingModifierItem != null) {
        ModifierBottomSheet(
            menuItem = uiState.pendingModifierItem!!,
            groups = uiState.pendingModifierGroups,
            selections = uiState.modifierSelections,
            formatter = formatter,
            locale = uiState.regionConfig.locale,
            onToggle = viewModel::onModifierToggled,
            onConfirm = viewModel::confirmModifiers,
            onDismiss = viewModel::dismissModifiers,
        )
    }

    // Item note editing dialog
    uiState.noteEditingItemId?.let { editId ->
        val editItem = uiState.items.find { it.id == editId }
        if (editItem != null) {
            ItemNoteDialog(
                itemName = editItem.menuItemNameSnapshot.entries.firstOrNull()?.value ?: "",
                currentNote = editItem.notes,
                onConfirm = { note -> viewModel.saveItemNote(editId, note) },
                onDismiss = viewModel::dismissNoteEdit,
            )
        }
    }

    // Permission denied dialog
    if (uiState.permissionDeniedAction != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionDenied,
            title = { Text(stringResource(R.string.permission_denied_title)) },
            text = { Text(stringResource(R.string.permission_denied_body, uiState.permissionDeniedAction!!)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPermissionDenied) {
                    Text(stringResource(R.string.permission_denied_ok))
                }
            },
        )
    }

    // Split order dialog
    if (uiState.splitDialogVisible) {
        SplitOrderDialog(
            items = uiState.items.filter { it.status != OrderItemStatus.REFUNDED },
            selectedIds = uiState.splitSelectedItemIds,
            formatter = formatter,
            locale = uiState.regionConfig.locale,
            onToggle = viewModel::toggleSplitItem,
            onConfirm = viewModel::confirmSplit,
            onDismiss = viewModel::dismissSplitDialog,
        )
    }

    // Order-level notes dialog
    if (uiState.orderNotesDialogVisible) {
        OrderNotesDialog(
            initialNotes = uiState.orderNotes,
            onConfirm = viewModel::saveOrderNotes,
            onDismiss = viewModel::dismissOrderNotesDialog,
        )
    }

    // Discount dialog
    if (uiState.discountDialogVisible) {
        DiscountDialog(
            formatter = formatter,
            subtotal = uiState.subtotalMinorUnit,
            onApply = viewModel::applyOrderDiscount,
            onDismiss = viewModel::dismissDiscountDialog,
        )
    }

    // Walk-in Customer dialog
    if (uiState.walkinCustomerDialogVisible) {
        WalkinCustomerDialog(
            customers = uiState.customers,
            searchQuery = uiState.customerSearchQuery,
            onSearchChange = viewModel::setCustomerSearchQuery,
            onSelect = viewModel::selectCustomer,
            onDismiss = viewModel::dismissWalkinCustomerDialog,
        )
    }

    val locale = uiState.regionConfig.locale

    Column(modifier = modifier.fillMaxSize().background(PosContentBg)) {
        // Shared top bar (order type + table/guests/customer + search/scan)
        if (showStaffActions) {
            PosTopBar(
                selectedType = uiState.orderType,
                onTypeSelected = viewModel::setOrderType,
                pickupNumber = uiState.pickupNumber,
                onPickupNumberChange = viewModel::editPickupNumber,
                onWalkinCustomerClick = viewModel::showWalkinCustomerDialog,
                selectedCustomerName = uiState.selectedCustomerName,
                searchQuery = uiState.searchQuery,
                onSearchChange = viewModel::setSearchQuery,
            )
            HorizontalDivider(color = PosHairline)
        }

        // FSR (dine-in/table) orders fire to the kitchen and settle later at the cashier
        // terminal; QSR (walk-in/counter) orders collect payment right here on this screen.
        val onCheckoutAction: () -> Unit = if (uiState.isQsrMode) viewModel::openPaymentModal else viewModel::placeOrder

        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            // Center: category tabs + menu grid + bottom action bar
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val filteredItems = remember(uiState.menuItems, uiState.searchQuery) {
                    if (uiState.searchQuery.isBlank()) uiState.menuItems
                    else uiState.menuItems.filter {
                        it.names.values.any { name -> name.contains(uiState.searchQuery, ignoreCase = true) }
                    }
                }
                MenuGridArea(
                    items = filteredItems,
                    combos = uiState.combos,
                    locale = locale,
                    formatter = formatter,
                    onItemClick = viewModel::onMenuItemTapped,
                    onComboClick = viewModel::addCombo,
                    modifier = Modifier.weight(1f),
                )
                if (showStaffActions) {
                    HorizontalDivider(color = PosHairline)
                    PosBottomActionBar(
                        onNote = viewModel::showOrderNotesDialog,
                        onDiscount = viewModel::showDiscountDialog,
                        onHold = viewModel::holdOrder,
                        onClearCart = viewModel::clearCart,
                        onCheckout = onCheckoutAction,
                        checkoutEnabled = uiState.items.any { it.status == OrderItemStatus.PENDING } && !uiState.isLoading,
                    )
                }
            }

            VerticalDivider(color = PosHairline)

            // Right: cart panel
            CartPanel(
                uiState = uiState,
                locale = locale,
                formatter = formatter,
                onOrderTypeSelected = viewModel::setOrderType,
                onCheckoutPay = onCheckoutAction,
                onClearCart = viewModel::clearCart,
                onIncrement = viewModel::incrementItem,
                onDecrement = viewModel::decrementItem,
                onRemove = viewModel::removeItem,
                onVoidItem = viewModel::voidItem,
                onNoteEdit = viewModel::startNoteEdit,
                onOrderNotesClick = viewModel::showOrderNotesDialog,
                onDismissError = viewModel::dismissError,
                showStaffActions = showStaffActions,
                modifier = Modifier.width(384.dp).fillMaxHeight(),
            )
        }
    }

    // QSR: Payment modal (rendered AFTER main content — overlays on top)
    if (uiState.paymentModalVisible) {
        QsrPaymentModal(
            uiState = uiState,
            formatter = formatter,
            onSelectMethod = viewModel::selectPaymentMethod,
            onCashInputChanged = viewModel::onCashReceivedInputChanged,
            onQuickAmount = viewModel::setQuickCashAmount,
            onConfirmCash = viewModel::confirmCashPayment,
            onBackFromCash = viewModel::backToMethodSelection,
            onDismiss = viewModel::dismissPaymentModal,
        )
    }

    // QSR: Payment success overlay
    if (uiState.paymentSuccessVisible) {
        QsrPaymentSuccessOverlay(
            orderNumber = uiState.completedOrderNumber,
            pickupNumber = uiState.pickupNumber,
            onNewOrder = { viewModel.dismissPaymentSuccess(); onStartNewOrder() },
            onPrintAgain = { },
            onViewOrder = {
                viewModel.dismissPaymentSuccess()
                onOrderPlaced(uiState.orderId)
            },
        )
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(5000)
            viewModel.dismissPaymentSuccess()
            onStartNewOrder()
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────


// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun PosTopBar(
    selectedType: OrderType,
    onTypeSelected: (OrderType) -> Unit,
    pickupNumber: String = "",
    onPickupNumberChange: (String) -> Unit = {},
    onWalkinCustomerClick: () -> Unit = {},
    selectedCustomerName: String? = null,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPickupEdit by remember { mutableStateOf(false) }
    var pickupInput by remember { mutableStateOf(pickupNumber) }

    if (showPickupEdit) {
        AlertDialog(
            onDismissRequest = { showPickupEdit = false },
            title = { Text(stringResource(R.string.qsr_pickup_edit_hint)) },
            text = {
                OutlinedTextField(
                    value = pickupInput,
                    onValueChange = { pickupInput = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.qsr_pickup_auto)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPickupNumberChange(pickupInput)
                    showPickupEdit = false
                }) { Text(stringResource(R.string.permission_denied_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPickupEdit = false }) { Text(stringResource(R.string.split_order_cancel)) }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PosShellBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hamburger (collapse nav — visual, matches mockup)
        Icon(
            Icons.Filled.Menu,
            contentDescription = null,
            tint = PosTextSecondary,
            modifier = Modifier.size(22.dp).clickableNoRipple {},
        )
        Spacer(Modifier.width(14.dp))
        // Order-type segmented pills
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PosContentBg,
            border = BorderStroke(1.dp, PosChipBorder),
        ) {
            Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                OrderType.entries.forEach { type ->
                    val label = when (type) {
                        OrderType.DINE_IN -> stringResource(R.string.order_type_dine_in)
                        OrderType.TAKEAWAY -> stringResource(R.string.order_type_takeaway)
                        OrderType.DELIVERY -> stringResource(R.string.order_type_delivery)
                    }
                    val selected = type == selectedType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PosShellBg else Color.Transparent)
                            .clickableNoRipple { onTypeSelected(type) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) PosTextPrimary else PosTextSecondary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // QSR: Pickup number (editable), Walk-in Customer, no table binding
        val pickupLabel = if (pickupNumber.isBlank()) stringResource(R.string.qsr_pickup_auto) else stringResource(R.string.qsr_pickup_chip, pickupNumber)
        InfoChip(icon = Icons.Filled.QrCodeScanner, text = pickupLabel, trailing = Icons.Filled.EditNote, onClick = { showPickupEdit = true; pickupInput = pickupNumber })
        Spacer(Modifier.width(8.dp))
        InfoChip(
            icon = Icons.Filled.PersonOutline,
            text = selectedCustomerName ?: stringResource(R.string.pos_walk_in_customer),
            trailing = Icons.Filled.Add,
            onClick = onWalkinCustomerClick,
        )

        Spacer(Modifier.width(12.dp))

        // Search field — functional text input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f).widthIn(min = 120.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.pos_search_menu), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp)) } },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SunmiOrange,
                unfocusedBorderColor = PosChipBorder,
                focusedContainerColor = PosContentBg,
                unfocusedContainerColor = PosContentBg,
            ),
        )
        Spacer(Modifier.width(8.dp))
        // Scan button
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SunmiOrange,
        ) {
            Row(
                modifier = Modifier.clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pos_scan), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, trailing: ImageVector? = null, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PosChipBg,
        border = BorderStroke(1.dp, PosChipBorder),
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 13.sp, color = PosTextPrimary)
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Icon(trailing, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── QSR Payment Modal ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QsrPaymentModal(
    uiState: OrderUiState,
    formatter: AmountFormatter,
    onSelectMethod: (PaymentMethod) -> Unit,
    onCashInputChanged: (String) -> Unit,
    onQuickAmount: (Long) -> Unit,
    onConfirmCash: () -> Unit,
    onBackFromCash: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = uiState.totalMinorUnit
    val change = (uiState.cashReceivedMinorUnit - total).coerceAtLeast(0L)
    val canConfirmCash = uiState.cashReceivedMinorUnit >= total && total > 0

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickableNoRipple {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PosShellBg,
            shadowElevation = 16.dp,
            modifier = Modifier.widthIn(max = 520.dp).padding(16.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.qsr_payment_modal_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = null, tint = PosTextSecondary) }
                }
                Spacer(Modifier.height(16.dp))

                // Order summary
                Surface(shape = RoundedCornerShape(12.dp), color = PosContentBg) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.order_total_label), fontSize = 13.sp, color = PosTextMuted)
                        Text(formatter.format(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SunmiOrange)
                    }
                }
                Spacer(Modifier.height(16.dp))

                when (uiState.paymentStep) {
                    QsrPaymentStep.SELECT_METHOD -> {
                        Text(stringResource(R.string.pos_filters), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                        Spacer(Modifier.height(12.dp))
                        val methods = listOf(
                            PaymentMethod.CASH to R.string.qsr_payment_cash,
                            PaymentMethod.CARD to R.string.qsr_payment_card,
                            PaymentMethod.OTHER to R.string.qsr_payment_mobile,
                            PaymentMethod.QR_CODE to R.string.qsr_payment_qr,
                            PaymentMethod.GIFT_CARD to R.string.qsr_payment_gift_card,
                            PaymentMethod.OTHER to R.string.qsr_payment_other,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            methods.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (method, labelRes) ->
                                        OutlinedButton(
                                            onClick = { onSelectMethod(method) },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PosTextPrimary),
                                            border = BorderStroke(1.dp, PosHairline),
                                        ) {
                                            Text(stringResource(labelRes), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    QsrPaymentStep.CASH_INPUT -> {
                        Text(stringResource(R.string.qsr_payment_cash), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.order_total_label) + ": " + formatter.format(total), fontSize = 14.sp, color = PosTextMuted)
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = uiState.cashReceivedInput,
                            onValueChange = onCashInputChanged,
                            label = { Text(stringResource(R.string.qsr_cash_received)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))

                        // Quick amount chips
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10_00L, 20_00L, 50_00L).forEach { amt ->
                                FilterChip(
                                    selected = uiState.cashReceivedMinorUnit == amt,
                                    onClick = { onQuickAmount(amt) },
                                    label = { Text(formatter.format(amt)) },
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                            FilterChip(
                                selected = uiState.cashReceivedMinorUnit == total,
                                onClick = { onQuickAmount(total) },
                                label = { Text(stringResource(R.string.qsr_cash_exact)) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SunmiOrange.copy(alpha = 0.15f)),
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        // Change
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.qsr_cash_change), fontSize = 14.sp, color = PosTextSecondary)
                            Text(formatter.format(change), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (change >= 0) PosOnlineDot else PosBadgeSpicyFg)
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onBackFromCash, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.split_order_cancel)) }
                            Button(
                                onClick = onConfirmCash,
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = canConfirmCash,
                                colors = ButtonDefaults.buttonColors(containerColor = SunmiOrange),
                            ) { Text(stringResource(R.string.qsr_cash_confirm)) }
                        }
                    }

                    QsrPaymentStep.PROCESSING -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = SunmiOrange, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.qsr_card_waiting), fontSize = 16.sp, color = PosTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ── QSR Payment Success ──────────────────────────────────────────────────────

@Composable
private fun QsrPaymentSuccessOverlay(
    orderNumber: String,
    pickupNumber: String,
    onNewOrder: () -> Unit,
    onPrintAgain: () -> Unit,
    onViewOrder: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickableNoRipple {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PosShellBg,
            shadowElevation = 16.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = PosOnlineDot.copy(alpha = 0.15f), modifier = Modifier.size(72.dp)) {
                    Text("✓", Modifier.align(Alignment.CenterHorizontally).wrapContentHeight(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PosOnlineDot)
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.qsr_payment_success), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.qsr_sent_to_kitchen), fontSize = 14.sp, color = PosTextSecondary)
                Text(stringResource(R.string.qsr_receipt_printed), fontSize = 14.sp, color = PosTextSecondary)
                if (orderNumber.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = PosContentBg) {
                        Text(orderNumber, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SunmiOrange)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onNewOrder, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.qsr_new_order)) }
                    OutlinedButton(onClick = onPrintAgain, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.qsr_print_again)) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onViewOrder) { Text(stringResource(R.string.qsr_view_order)) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.qsr_auto_close), fontSize = 12.sp, color = PosTextMuted)
            }
        }
    }
}

// ── Menu grid area (category tabs + grid) ───────────────────────────────────────

/** Fixed meal-part category tabs from the mockup. `categoryId == null` is the Favorites tab. */
private val POS_CATEGORY_TABS: List<Pair<String?, Int>> = listOf(
    null to R.string.pos_favorites,
    "cat-coffee" to R.string.qsr_cat_coffee,
    "cat-tea" to R.string.qsr_cat_tea,
    "cat-milk-tea" to R.string.qsr_cat_milk_tea,
    "cat-food" to R.string.qsr_cat_food,
    "cat-bakery" to R.string.pos_cat_bakery,
    "cat-desserts" to R.string.cat_desserts,
    "cat-combo" to R.string.qsr_cat_combo,
    "cat-retail" to R.string.pos_cat_retail,
    "cat-seasonal" to R.string.pos_cat_seasonal,
)

@Composable
private fun MenuGridArea(
    items: List<MenuItem>,
    combos: List<com.restaurantpos.core.model.Combo>,
    locale: String,
    formatter: AmountFormatter,
    onItemClick: (MenuItem) -> Unit,
    onComboClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val catId = POS_CATEGORY_TABS[selectedTab].first
    val visibleItems = if (catId == null) {
        items.filter { MenuCardContent.forName(it.names.localeName(locale)).favorite }
    } else {
        items.filter { it.categoryId == catId }
    }

    Column(modifier = modifier.background(PosContentBg)) {
        // Category tab row + Filters
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                POS_CATEGORY_TABS.forEachIndexed { idx, (_, labelRes) ->
                    CategoryTab(stringResource(labelRes), selectedTab == idx) { selectedTab = idx }
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PosShellBg,
                border = BorderStroke(1.dp, PosChipBorder),
            ) {
                Row(
                    modifier = Modifier.clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pos_filters), fontSize = 14.sp, color = PosTextPrimary)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(visibleItems, key = { it.id }) { item ->
                MenuCard(
                    item = item,
                    locale = locale,
                    formatter = formatter,
                    onClick = { if (!item.isSoldOut) onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) SunmiOrange else PosShellBg)
            .then(if (selected) Modifier else Modifier.border(1.dp, PosChipBorder, RoundedCornerShape(20.dp)))
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else PosTextSecondary,
            maxLines = 1,
        )
    }
}

// ── Menu card ──────────────────────────────────────────────────────────────────

@Composable
private fun MenuCard(item: MenuItem, locale: String, formatter: AmountFormatter, onClick: () -> Unit) {
    val name = item.names.localeName(locale)
    val extra = remember(name) { MenuCardContent.forName(name) }
    val alpha = if (item.isSoldOut) 0.45f else 1f

    Surface(
        onClick = onClick,
        enabled = !item.isSoldOut,
        shape = RoundedCornerShape(14.dp),
        color = PosCardBg,
        border = BorderStroke(1.dp, PosHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // "Photo": emoji stand-in on a soft tinted plate, with star + spicy/sold-out overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .background(MenuImageTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(extra.emoji, fontSize = 52.sp)
                Surface(
                    shape = CircleShape,
                    color = PosShellBg,
                    shadowElevation = 1.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                ) {
                    Icon(
                        if (extra.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = if (extra.starred) SunmiOrange else PosTextSecondary,
                        modifier = Modifier.padding(5.dp),
                    )
                }
                if (MenuBadge.SPICY in extra.badges) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosBadgeSpicyBg,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    ) {
                        Text(
                            "🔥 ${stringResource(R.string.pos_badge_spicy)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosBadgeSpicyFg,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
                if (item.isSoldOut) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PosBadgeSpicyBg,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.order_sold_out),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosBadgeSpicyFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp).alpha(alpha)) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    extra.description,
                    fontSize = 12.sp,
                    color = PosTextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    extra.badges.filter { it != MenuBadge.SPICY }.forEach { badge ->
                        MenuBadgeChip(badge)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatter.format(item.priceMinorUnit),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosTextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuBadgeChip(badge: MenuBadge) {
    // Mockup: Popular & Vegan are both soft-green badges.
    val (bg, fg, labelRes) = when (badge) {
        MenuBadge.POPULAR -> Triple(PosBadgeVeganBg, PosBadgeVeganFg, R.string.pos_badge_popular)
        MenuBadge.VEGAN -> Triple(PosBadgeVeganBg, PosBadgeVeganFg, R.string.pos_badge_vegan)
        MenuBadge.SPICY -> Triple(PosBadgeSpicyBg, PosBadgeSpicyFg, R.string.pos_badge_spicy)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            stringResource(labelRes),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ComboCard(
    combo: com.restaurantpos.core.model.Combo,
    locale: String,
    formatter: AmountFormatter,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PosCardBg,
        border = BorderStroke(1.dp, PosHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp).background(SunmiOrangeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.RestaurantMenu, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(34.dp))
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    combo.names.localeName(locale),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PosTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.combo_component_count, combo.components.size),
                    fontSize = 12.sp,
                    color = PosTextSecondary,
                    modifier = Modifier.height(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MenuBadgeChip(MenuBadge.POPULAR)
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatter.format(combo.comboPriceMinorUnit),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosTextPrimary,
                    )
                }
            }
        }
    }
}

// ── Cart panel ───────────────────────────────────────────────────────────────

@Composable
private fun CartPanel(
    uiState: OrderUiState,
    locale: String,
    formatter: AmountFormatter,
    onOrderTypeSelected: (OrderType) -> Unit,
    onCheckoutPay: () -> Unit,
    onClearCart: () -> Unit,
    onIncrement: (OrderItem) -> Unit,
    onDecrement: (OrderItem) -> Unit,
    onRemove: (OrderItem) -> Unit,
    onVoidItem: (OrderItem) -> Unit,
    onNoteEdit: (itemId: String) -> Unit,
    onOrderNotesClick: () -> Unit,
    onDismissError: () -> Unit,
    showStaffActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val typeLabel = when (uiState.orderType) {
        OrderType.DINE_IN -> stringResource(R.string.order_type_dine_in)
        OrderType.TAKEAWAY -> stringResource(R.string.order_type_takeaway)
        OrderType.DELIVERY -> stringResource(R.string.order_type_delivery)
    }
    val typeSubtitle = when (uiState.orderType) {
        OrderType.DINE_IN -> stringResource(R.string.qsr_dine_in_subtitle)
        OrderType.TAKEAWAY -> stringResource(R.string.qsr_takeaway_subtitle)
        OrderType.DELIVERY -> stringResource(R.string.qsr_delivery_subtitle)
    }
    val hasPending = uiState.items.any { it.status == OrderItemStatus.PENDING }
    Column(modifier = modifier.background(PosShellBg)) {
        // Header: Order Type + subtitle, Pickup No., Customer
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(stringResource(R.string.pos_order_type_label), fontSize = 12.sp, color = PosTextMuted)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickableNoRipple {}) {
                        Text(typeLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(typeSubtitle, fontSize = 12.sp, color = PosTextMuted)
                }
                // Clear Cart button (when items present)
                if (hasPending) {
                    TextButton(onClick = onClearCart, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.qsr_clear_cart), fontSize = 12.sp, color = PosBadgeSpicyFg)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Pickup No.
            Text(stringResource(R.string.qsr_pickup_label), fontSize = 12.sp, color = PosTextMuted)
            Spacer(Modifier.height(2.dp))
            Text(
                if (uiState.pickupNumber.isBlank()) stringResource(R.string.qsr_pickup_auto) else uiState.pickupNumber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PosTextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            // Customer
            Text(stringResource(R.string.pos_customer_label), fontSize = 12.sp, color = PosTextMuted)
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth().clickableNoRipple {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pos_walk_in_customer), fontSize = 14.sp, color = PosTextPrimary)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = PosHairline)

        // Line items
        if (uiState.items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pos_no_items), fontSize = 14.sp, color = PosTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    CartLineRow(
                        item = item,
                        isPlaced = uiState.isPlaced,
                        locale = locale,
                        formatter = formatter,
                        onIncrement = { onIncrement(item) },
                        onDecrement = { onDecrement(item) },
                        onRemove = { onRemove(item) },
                        onVoid = { onVoidItem(item) },
                        onNote = { if (showStaffActions) onNoteEdit(item.id) },
                        showStaffActions = showStaffActions,
                    )
                    HorizontalDivider(color = PosHairline)
                }
            }
        }

        // Add note for kitchen
        if (showStaffActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableNoRipple(onOrderNotesClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (uiState.orderNotes.isBlank()) stringResource(R.string.order_add_notes) else uiState.orderNotes,
                    fontSize = 13.sp,
                    color = if (uiState.orderNotes.isBlank()) SunmiOrange else PosTextPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = PosHairline)
        }

        // Totals — Service Charge rate from RegionConfig (configurable via Web Admin)
        val sub = uiState.subtotalMinorUnit
        val taxPctText = if (sub > 0) {
            ("%.3f".format(uiState.taxTotalMinorUnit * 100.0 / sub)).trimEnd('0').trimEnd('.') + "%"
        } else "0%"
        val scRatePermille = uiState.regionConfig.serviceChargeRatePermille
        val serviceCharge = uiState.serviceChargeMinorUnit
        // Single source of truth shared with the payment modal — see OrderUiState.totalMinorUnit.
        val displayTotal = uiState.totalMinorUnit
        val scDisplayPct = "${scRatePermille / 10}.${scRatePermille % 10}%"
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TotalRow(stringResource(R.string.order_subtotal_label), formatter.format(sub))
            Spacer(Modifier.height(6.dp))
            TotalRow(stringResource(R.string.pos_tax_pct, taxPctText), formatter.format(uiState.taxTotalMinorUnit))
            Spacer(Modifier.height(6.dp))
            TotalRow(stringResource(R.string.pos_service_charge, scDisplayPct), formatter.format(serviceCharge))
            if (uiState.discountMinorUnit > 0) {
                Spacer(Modifier.height(6.dp))
                TotalRow(stringResource(R.string.pos_action_discount), "-" + formatter.format(uiState.discountMinorUnit), valueColor = PosOnlineDot)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.order_total_label), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Text(formatter.format(displayTotal), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            }

            uiState.errorMessage?.let { err ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, color = PosBadgeSpicyFg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text(stringResource(android.R.string.ok)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            // QSR: Checkout & Pay (replaces Send to Kitchen)
            Button(
                onClick = onCheckoutPay,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = hasPending && !uiState.isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunmiOrange),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.qsr_checkout_pay, formatter.format(displayTotal)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: String, valueColor: Color = PosTextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = PosTextSecondary)
        Text(amount, fontSize = 14.sp, color = valueColor)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CartLineRow(
    item: OrderItem,
    isPlaced: Boolean,
    locale: String,
    formatter: AmountFormatter,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onVoid: () -> Unit,
    onNote: () -> Unit,
    showStaffActions: Boolean,
) {
    val isVoided = item.status == OrderItemStatus.REFUNDED
    val decoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
    val nameColor = if (isVoided) PosTextMuted else PosTextPrimary
    // Editable only while the line hasn't been sent to the kitchen.
    val editable = !isPlaced && item.status == OrderItemStatus.PENDING && !isVoided

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onNote)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Quantity stepper
        QtyStepper(
            quantity = item.quantity,
            editable = editable,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.menuItemNameSnapshot.localeName(locale),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = nameColor,
                    textDecoration = decoration,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isVoided) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = PosBadgeSpicyBg) {
                        Text(
                            stringResource(R.string.order_item_void_label),
                            fontSize = 10.sp,
                            color = PosBadgeSpicyFg,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            item.selectedModifiers.forEach { mod ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "• ${mod.nameSnapshot.localeName(locale)}",
                        fontSize = 12.sp,
                        color = PosTextSecondary,
                        textDecoration = decoration,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (mod.priceAdjustmentMinorUnit != 0L) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (mod.priceAdjustmentMinorUnit > 0) "+" else "") + formatter.format(mod.priceAdjustmentMinorUnit),
                            fontSize = 12.sp,
                            color = PosTextMuted,
                            textDecoration = decoration,
                        )
                    }
                }
            }
            if (item.notes.isNotBlank()) {
                Text("📝 ${item.notes}", fontSize = 12.sp, color = SunmiOrange, textDecoration = decoration)
            }
        }

        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatter.format(item.lineTotalMinorUnit),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = nameColor,
                    textDecoration = decoration,
                )
                if (editable) {
                    Spacer(Modifier.width(4.dp))
                    // Per-line overflow (mockup ⋮): removes the draft line.
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.pos_remove_item),
                        tint = PosTextMuted,
                        modifier = Modifier.size(18.dp).clickableNoRipple(onRemove),
                    )
                }
            }
            if (showStaffActions && isPlaced && !isVoided && item.status != OrderItemStatus.PENDING) {
                TextButton(onClick = onVoid, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.order_item_refund_action), fontSize = 11.sp, color = PosBadgeSpicyFg)
                }
            }
        }
    }
}

@Composable
private fun QtyStepper(quantity: Int, editable: Boolean, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    if (!editable) {
        Surface(shape = RoundedCornerShape(8.dp), color = PosContentBg, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$quantity", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
            }
        }
        return
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PosShellBg,
        border = BorderStroke(1.dp, PosChipBorder),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clickableNoRipple(onDecrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            }
            Text("$quantity", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary, modifier = Modifier.widthIn(min = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(
                modifier = Modifier.size(30.dp).clickableNoRipple(onIncrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = SunmiOrange, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Bottom action bar ──────────────────────────────────────────────────────────

@Composable
private fun PosBottomActionBar(
    onNote: () -> Unit,
    onDiscount: () -> Unit,
    onHold: () -> Unit,
    onClearCart: () -> Unit,
    onCheckout: () -> Unit,
    checkoutEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PosShellBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionBarButton(Icons.Filled.EditNote, stringResource(R.string.pos_action_note), onNote)
        ActionBarButton(Icons.Filled.Percent, stringResource(R.string.pos_action_discount), onDiscount)
        ActionBarButton(Icons.Filled.PauseCircleOutline, stringResource(R.string.pos_action_hold), onHold)
        ActionBarButton(Icons.Filled.Remove, stringResource(R.string.qsr_clear_cart), onClearCart)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onCheckout,
            enabled = checkoutEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SunmiOrange),
            modifier = Modifier.height(48.dp),
        ) {
            Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pos_action_checkout), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun ActionBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PosContentBg,
        border = BorderStroke(1.dp, PosChipBorder),
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = PosTextPrimary)
        }
    }
}

/** Tap handling without a ripple, to match the clean mockup surfaces. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}

// ── Discount dialog ─────────────────────────────────────────────────────────

@Composable
private fun DiscountDialog(
    formatter: AmountFormatter,
    subtotal: Long,
    onApply: (discountMinorUnit: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var isPercentage by remember { mutableStateOf(true) }
    val pct = amountText.toDoubleOrNull()
    val discountMinorUnit = if (pct != null) {
        if (isPercentage) ((pct / 100.0) * subtotal).toLong().coerceAtMost(subtotal)
        else (pct * 100).toLong().coerceAtMost(subtotal)
    } else 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pos_action_discount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    FilterChip(selected = isPercentage, onClick = { isPercentage = true }, label = { Text("%") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isPercentage, onClick = { isPercentage = false }, label = { Text("$") })
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    label = { Text(if (isPercentage) "Percentage (%)" else "Amount ($)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (discountMinorUnit > 0) Text("Discount: ${formatter.format(discountMinorUnit)}", fontSize = 14.sp, color = PosOnlineDot)
            }
        },
        confirmButton = { TextButton(onClick = { if (discountMinorUnit > 0) onApply(discountMinorUnit) }, enabled = discountMinorUnit > 0) { Text(stringResource(R.string.permission_denied_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_order_cancel)) } },
    )
}

// ── Walk-in Customer dialog ─────────────────────────────────────────────────

@Composable
private fun WalkinCustomerDialog(
    customers: List<com.restaurantpos.core.model.Customer>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (com.restaurantpos.core.model.Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pos_select_customer)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.pos_customer_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.pos_customer_empty), fontSize = 13.sp, color = PosTextMuted, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(filtered, key = { it.id }) { customer ->
                            Row(
                                Modifier.fillMaxWidth().clickableNoRipple { onSelect(customer) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(customer.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PosTextPrimary)
                                    Text(customer.phone, fontSize = 12.sp, color = PosTextSecondary)
                                }
                                if (customer.loyaltyPoints > 0) {
                                    Text(stringResource(R.string.pos_customer_points, customer.loyaltyPoints), fontSize = 12.sp, color = SunmiOrange)
                                }
                            }
                            HorizontalDivider(color = PosHairline)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_order_cancel)) } },
    )
}

// ── Modifier bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModifierBottomSheet(
    menuItem: MenuItem,
    groups: List<ModifierGroup>,
    selections: Map<String, Set<String>>,
    formatter: AmountFormatter,
    locale: String,
    onToggle: (ModifierGroup, DomainModifier) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.modifier_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = menuItem.names.localeName(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            groups.forEach { group ->
                val groupName = group.names.localeName(locale)
                val badge = if (group.required) {
                    stringResource(R.string.modifier_required_badge)
                } else {
                    stringResource(R.string.modifier_optional_badge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(groupName, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = if (group.required) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (group.required) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                val selectedIds = selections[group.id] ?: emptySet()
                group.modifiers.forEach { mod ->
                    val isSelected = mod.id in selectedIds
                    val modName = mod.names.localeName(locale)
                    val priceText = if (mod.priceAdjustmentMinorUnit != 0L) {
                        " (${if (mod.priceAdjustmentMinorUnit > 0) "+" else ""}${formatter.format(mod.priceAdjustmentMinorUnit)})"
                    } else ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (group.type == ModifierGroupType.SINGLE) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onToggle(group, mod) },
                            )
                        } else {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggle(group, mod) },
                            )
                        }
                        Text(
                            text = "$modName$priceText",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(stringResource(R.string.modifier_sheet_confirm))
            }
        }
    }
}

// ── Split order dialog ────────────────────────────────────────────────────────

@Composable
private fun SplitOrderDialog(
    items: List<OrderItem>,
    selectedIds: Set<String>,
    formatter: AmountFormatter,
    locale: String,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_order_dialog_title)) },
        text = {
            LazyColumn {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.id in selectedIds,
                            onCheckedChange = { onToggle(item.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity}× ${item.menuItemNameSnapshot.localeName(locale)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = formatter.format(item.lineTotalMinorUnit),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text(stringResource(R.string.split_order_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.split_order_cancel))
            }
        },
    )
}

@Composable
private fun ItemNoteDialog(
    itemName: String,
    currentNote: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteText by remember { mutableStateOf(currentNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemName) },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.order_item_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 3,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(noteText.trim()) }) {
                Text(stringResource(R.string.order_item_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun OrderNotesDialog(
    initialNotes: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_notes_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.order_notes_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                maxLines = 5,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.order_item_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
