package com.restaurantpos.app.pad.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.restaurantpos.app.pad.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantpos.app.pad.*
import com.restaurantpos.core.model.*

/**
 * Main ordering screen.
 *
 * Layout (landscape, Ziosk-inspired):
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  [Table 05]   [🌐 EN]          [AYCE: Round 2/5 | ⏱ 3:45]     │  TopBar
 * ├─────────────────────────────────────────┬────────────────────────┤
 * │  [Cat1] [Cat2] [Cat3] …                 │  CART                  │  CategoryRow
 * │  ┌─────┐ ┌─────┐ ┌─────┐              │  Item1 ×2   $12        │
 * │  │ img │ │ img │ │ img │ …            │  Item2 ×1   $8         │  ItemGrid
 * │  └─────┘ └─────┘ └─────┘              │  ──────────────────    │
 * │                                         │  Subtotal    $20       │
 * │                                         │  [Submit Order]        │  CartPanel
 * │                                         │  [📋 My Orders]        │
 * │                                         │  [🔔 Call Waiter ▾]   │
 * └─────────────────────────────────────────┴────────────────────────┘
 */
@Composable
fun MenuScreen(
    state: PadUiState,
    visibleItems: List<MenuItem>,
    cartTotal: Long,
    cartCount: Int,
    canSubmit: Boolean,
    onCategorySelect: (String) -> Unit,
    onAddToCart: (MenuItem) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onUpdateQty: (String, Int) -> Unit,
    onSubmit: () -> Unit,
    onCallWaiter: (WaiterCallReason) -> Unit,
    onShowHistory: () -> Unit,
    onSwitchLocale: (String) -> Unit,
    onUserInteraction: () -> Unit,
) {
    val cfg = state.padConfig
    val ayce = state.ayceSession
    val locale = state.activeLocale
    val sym = "¥"  // from regionConfig ideally; simplified here

    Box(modifier = Modifier.fillMaxSize().clickable(indication = null, interactionSource = null) { onUserInteraction() }) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            PadTopBar(
                tableDisplayName = cfg.tableDisplayName.ifBlank { cfg.boundTableId },
                ayceSession = ayce,
                cooldownSeconds = state.roundCooldownSeconds,
                supportedLocales = cfg.supportedLocales,
                activeLocale = locale,
                onLocaleSwitch = onSwitchLocale,
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // ── Left: category tabs + item grid ──────────────────────────
                Column(modifier = Modifier.weight(1.6f).fillMaxHeight()) {
                    // Category row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.categories) { cat ->
                            FilterChip(
                                selected = state.selectedCategory == cat,
                                onClick = { onUserInteraction(); onCategorySelect(cat) },
                                label = { Text(cat, fontSize = 13.sp) },
                            )
                        }
                    }

                    // Menu item grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visibleItems, key = { it.id }) { item ->
                            MenuItemCard(
                                item = item,
                                locale = locale,
                                sym = sym,
                                onAdd = { onUserInteraction(); onAddToCart(item) },
                            )
                        }
                    }
                }

                // ── Right: cart panel ──────────────────────────────────────
                CartPanel(
                    modifier = Modifier.weight(0.9f).fillMaxHeight(),
                    cartItems = state.cartItems,
                    cartTotal = cartTotal,
                    cartCount = cartCount,
                    currencySymbol = sym,
                    canSubmit = canSubmit,
                    ayceSession = ayce,
                    cooldownSeconds = state.roundCooldownSeconds,
                    onRemove = { id -> onUserInteraction(); onRemoveFromCart(id) },
                    onUpdateQty = { id, qty -> onUserInteraction(); onUpdateQty(id, qty) },
                    onSubmit = { onUserInteraction(); onSubmit() },
                    onCallWaiter = { reason -> onUserInteraction(); onCallWaiter(reason) },
                    onShowHistory = { onUserInteraction(); onShowHistory() },
                )
            }
        }

        // ── Success / error banners ──────────────────────────────────────────
        AnimatedVisibility(
            visible = state.successMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(state.successMessage ?: "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Waiter call confirmation ──────────────────────────────────────────
        AnimatedVisibility(
            visible = state.waiterCallSent,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Text(
                        "Service requested — we'll be right there!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun PadTopBar(
    tableDisplayName: String,
    ayceSession: AyceSession?,
    cooldownSeconds: Int,
    supportedLocales: List<String>,
    activeLocale: String,
    onLocaleSwitch: (String) -> Unit,
) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Table name
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(tableDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Language switcher
            if (supportedLocales.size > 1) {
                Spacer(Modifier.width(16.dp))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(activeLocale.uppercase(), style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        supportedLocales.forEach { locale ->
                            DropdownMenuItem(
                                text = { Text(locale.uppercase()) },
                                onClick = { onLocaleSwitch(locale); expanded = false },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // AYCE round indicator
            if (ayceSession != null) {
                AyceRoundBadge(session = ayceSession, cooldownSeconds = cooldownSeconds)
            }
        }
    }
}

@Composable
private fun AyceRoundBadge(session: AyceSession, cooldownSeconds: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (cooldownSeconds > 0) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (cooldownSeconds > 0) Icons.Default.Timer else Icons.Default.Dining,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    buildString {
                        append("Round ${session.currentRound}")
                        session.totalRoundsLimit?.let { append("/$it") }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (cooldownSeconds > 0) {
                    val mins = cooldownSeconds / 60
                    val secs = cooldownSeconds % 60
                    Text(
                        "Next round in %d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// ── Menu item card ───────────────────────────────────────────────────────────

@Composable
private fun MenuItemCard(
    item: MenuItem,
    locale: String,
    sym: String,
    onAdd: () -> Unit,
) {
    val name = item.names[locale] ?: item.names.values.firstOrNull() ?: item.id

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
        onClick = onAdd,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                // Sold-out overlay
                if (item.isSoldOut) {
                    Box(
                        modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.7f)) {
                            Text(stringResource(R.string.pad_sold_out), modifier = Modifier.padding(4.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$sym${item.priceMinorUnit / 100.0}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.isSoldOut) {
                        FilledIconButton(
                            onClick = onAdd,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Cart panel ───────────────────────────────────────────────────────────────

@Composable
private fun CartPanel(
    modifier: Modifier = Modifier,
    cartItems: List<CartEntry>,
    cartTotal: Long,
    cartCount: Int,
    currencySymbol: String,
    canSubmit: Boolean,
    ayceSession: AyceSession?,
    cooldownSeconds: Int,
    onRemove: (String) -> Unit,
    onUpdateQty: (String, Int) -> Unit,
    onSubmit: () -> Unit,
    onCallWaiter: (WaiterCallReason) -> Unit,
    onShowHistory: () -> Unit,
) {
    var showCallMenu by remember { mutableStateOf(false) }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

            Text(stringResource(R.string.pad_your_order), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // AYCE items-remaining indicator
            if (ayceSession != null && ayceSession.maxItemsPerRound > 0) {
                val used = cartItems.sumOf { it.quantity }
                val remaining = ayceSession.maxItemsPerRound - used
                LinearProgressIndicator(
                    progress = { (used.toFloat() / ayceSession.maxItemsPerRound).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "$used / ${ayceSession.maxItemsPerRound} items this round  ($remaining left)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remaining <= 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Cart items list
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (cartItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text(stringResource(R.string.pad_cart_empty), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    cartItems.forEach { entry ->
                        CartItemRow(
                            entry = entry,
                            currencySymbol = currencySymbol,
                            onQtyChange = { onUpdateQty(entry.tempId, it) },
                            onRemove = { onRemove(entry.tempId) },
                        )
                    }
                }
            }

            // Total
            if (cartItems.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pad_subtotal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "$currencySymbol${cartTotal / 100.0}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Submit button
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        cooldownSeconds > 0 -> {
                            val m = cooldownSeconds / 60; val s = cooldownSeconds % 60
                            "Next round in %d:%02d".format(m, s)
                        }
                        cartItems.isEmpty() -> "Add items to order"
                        else -> "Send to Kitchen  ($cartCount)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Order history button
            OutlinedButton(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pad_my_orders))
            }

            Spacer(Modifier.height(6.dp))

            // Call waiter button (Ziosk-style: always visible, dropdown for reason)
            Box {
                Button(
                    onClick = { showCallMenu = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pad_call_waiter), style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(expanded = showCallMenu, onDismissRequest = { showCallMenu = false }) {
                    listOf(
                        WaiterCallReason.GENERAL to "General Service",
                        WaiterCallReason.REQUEST_BILL to "Request Bill",
                        WaiterCallReason.NEED_WATER to "Need Water / Broth",
                        WaiterCallReason.NEED_UTENSILS to "Need Utensils",
                    ).forEach { (reason, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(reason.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { onCallWaiter(reason); showCallMenu = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    entry: CartEntry,
    currencySymbol: String,
    onQtyChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val name = entry.menuItem.names.values.firstOrNull() ?: entry.menuItem.id
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.note.isNotEmpty()) Text(entry.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onQtyChange(entry.quantity - 1) }, modifier = Modifier.size(28.dp)) {
                Icon(if (entry.quantity == 1) Icons.Default.Delete else Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text("${entry.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { onQtyChange(entry.quantity + 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            "$currencySymbol${entry.lineTotalMinorUnit / 100.0}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End,
        )
    }
}

private fun WaiterCallReason.icon() = when (this) {
    WaiterCallReason.GENERAL        -> Icons.Default.NotificationsActive
    WaiterCallReason.REQUEST_BILL   -> Icons.Default.Receipt
    WaiterCallReason.NEED_WATER     -> Icons.Default.LocalDrink
    WaiterCallReason.NEED_UTENSILS  -> Icons.Default.Restaurant
}
