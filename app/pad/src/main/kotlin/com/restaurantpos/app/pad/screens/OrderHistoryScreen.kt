package com.restaurantpos.app.pad.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.restaurantpos.app.pad.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantpos.app.pad.AyceRound
import com.restaurantpos.core.model.OrderItem
import com.restaurantpos.core.model.OrderItemStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shows all items ordered this session, grouped by round.
 * Inspired by Jamezz: guests can see their order history and status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    submittedRounds: List<AyceRound>,
    submittedItems: List<OrderItem>,
    locale: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pad_my_orders)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (submittedItems.isEmpty() && submittedRounds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(stringResource(R.string.pad_no_orders), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (submittedRounds.isNotEmpty()) {
                // AYCE mode: group by round
                submittedRounds.sortedByDescending { it.roundNumber }.forEach { round ->
                    item {
                        RoundSection(round = round, locale = locale)
                    }
                }
            } else {
                // Standard mode: flat list by status
                val grouped = submittedItems.groupBy { it.status }
                grouped.entries.sortedBy { it.key.ordinal }.forEach { (status, items) ->
                    item {
                        StatusSection(status = status, items = items, locale = locale)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundSection(round: AyceRound, locale: String) {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "Round ${round.roundNumber}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    fmt.format(Date(round.submittedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            round.items.forEach { item ->
                OrderItemRow(item = item, locale = locale)
            }
        }
    }
}

@Composable
private fun StatusSection(status: OrderItemStatus, items: List<OrderItem>, locale: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(status.icon(), contentDescription = null, modifier = Modifier.size(18.dp), tint = status.color())
                Text(status.displayLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items.forEach { item -> OrderItemRow(item = item, locale = locale) }
        }
    }
}

@Composable
private fun OrderItemRow(item: OrderItem, locale: String) {
    val name = item.menuItemNameSnapshot[locale]
 ?: item.menuItemNameSnapshot.values.firstOrNull() ?: "Item"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.status.icon(), contentDescription = null, modifier = Modifier.size(14.dp), tint = item.status.color())
        Spacer(Modifier.width(6.dp))
        Text("${name} ×${item.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (item.notes.isNotBlank()) {
            Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OrderItemStatus.icon() = when (this) {
    OrderItemStatus.PENDING  -> Icons.Default.Schedule
    OrderItemStatus.PLACED   -> Icons.Default.CheckCircleOutline
    OrderItemStatus.PREPARING -> Icons.Default.OutdoorGrill
    OrderItemStatus.SERVED   -> Icons.Default.DoneAll
    else -> Icons.Default.Circle
}

@Composable
private fun OrderItemStatus.color() = when (this) {
    OrderItemStatus.PENDING   -> MaterialTheme.colorScheme.onSurfaceVariant
    OrderItemStatus.PLACED    -> MaterialTheme.colorScheme.primary
    OrderItemStatus.PREPARING -> MaterialTheme.colorScheme.tertiary
    OrderItemStatus.SERVED    -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun OrderItemStatus.displayLabel() = when (this) {
    OrderItemStatus.PENDING   -> "Pending"
    OrderItemStatus.PLACED    -> "Sent to Kitchen"
    OrderItemStatus.PREPARING -> "Being Prepared"
    OrderItemStatus.SERVED    -> "Served"
    else -> name
}
