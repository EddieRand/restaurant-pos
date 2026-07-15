package com.restaurantpos.feature.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.model.KitchenTicketStatus
import com.restaurantpos.core.model.localizedName
import kotlinx.coroutines.delay

@Composable
fun KdsScreen(
    modifier: Modifier = Modifier,
    viewModel: KdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Station tab rail (left side)
        if (state.stations.size > 1) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Spacer(Modifier.height(16.dp))
                state.stations.forEach { station ->
                    NavigationRailItem(
                        selected = state.selectedStation == station,
                        onClick = { viewModel.selectStation(station) },
                        icon = {},
                        label = {
                            Text(
                                station.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // Ticket grid
        val currentStation = state.selectedStation
        val tickets = if (currentStation != null) {
            state.ticketsByStation[currentStation] ?: emptyList()
        } else {
            state.ticketsByStation.values.flatten()
        }

        if (tickets.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.kds_all_clear),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tickets, key = { it.ticket.id }) { card ->
                    TicketCard(
                        card = card,
                        locale = state.locale,
                        urgentAfterSeconds = state.urgentAfterSeconds,
                        criticalAfterSeconds = state.criticalAfterSeconds,
                        onBump = { viewModel.bump(card.ticket.id) },
                        onRecall = { viewModel.recall(card.ticket.id) },
                    )
                }
            }
        }
    }

    state.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000L)
            viewModel.dismissError()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) { Text(msg) }
    }
}

@Composable
private fun TicketCard(
    card: com.restaurantpos.feature.kds.TicketCard,
    locale: String,
    urgentAfterSeconds: Long,
    criticalAfterSeconds: Long,
    onBump: () -> Unit,
    onRecall: () -> Unit,
) {
    val isUrgent = card.elapsedSeconds >= urgentAfterSeconds
    val isCritical = card.elapsedSeconds >= criticalAfterSeconds

    val containerColor by animateColorAsState(
        targetValue = when {
            card.ticket.status == KitchenTicketStatus.RECALLED -> MaterialTheme.colorScheme.tertiaryContainer
            isCritical -> MaterialTheme.colorScheme.errorContainer
            isUrgent -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(500),
        label = "ticketColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.kds_order_prefix) + " " + card.ticket.orderId.takeLast(6).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.kds_course_prefix) + " ${card.ticket.course}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Elapsed timer — prominent and color-coded
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isCritical) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                stringResource(R.string.kds_overdue),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = formatElapsed(card.elapsedSeconds),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isCritical -> MaterialTheme.colorScheme.error
                            isUrgent -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (card.ticket.status == KitchenTicketStatus.RECALLED) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                stringResource(R.string.kds_recalled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Items
            card.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${item.quantity}×  ${item.menuItemNameSnapshot.localizedName(locale).ifBlank { item.menuItemId }}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (item.selectedModifiers.isNotEmpty()) {
                    item.selectedModifiers.forEach { mod ->
                        Text(
                            text = "  ↳ ${mod.nameSnapshot.values.firstOrNull() ?: mod.modifierId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.notes.isNotBlank()) {
                    Text(
                        text = "  ✎ ${item.notes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (item.allergenSnapshot.isNotEmpty()) {
                    Text(
                        text = "  ⚠ ${item.allergenSnapshot.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onBump,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kds_bump))
                }
                OutlinedButton(
                    onClick = onRecall,
                    modifier = Modifier.weight(0.6f),
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.kds_recall))
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
