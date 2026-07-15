package com.restaurantpos.feature.tables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.model.Order
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.OrderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dtFormat = SimpleDateFormat("HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeawayQueueScreen(
    onBack: () -> Unit,
    onOrderTap: (orderId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TakeawayQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.takeaway_queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.orders.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.takeaway_queue_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.orders, key = { it.id }) { order ->
                TakeawayOrderCard(
                    order = order,
                    formatter = formatter,
                    onClick = { onOrderTap(order.id) },
                )
            }
        }
    }
}

@Composable
private fun TakeawayOrderCard(
    order: Order,
    formatter: AmountFormatter,
    onClick: () -> Unit,
) {
    val statusColor = when (order.status) {
        OrderStatus.DRAFT, OrderStatus.IN_PROGRESS -> MaterialTheme.colorScheme.surfaceVariant
        OrderStatus.PLACED -> MaterialTheme.colorScheme.primaryContainer
        OrderStatus.READY -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val typeLabel = when (order.type) {
        OrderType.TAKEAWAY -> "Takeaway"
        OrderType.DELIVERY -> "Delivery"
        else -> order.type.name
    }
    val shortId = order.id.takeLast(6).uppercase()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "#$shortId",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = dtFormat.format(Date(order.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = typeLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = order.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatter.format(order.totalMinorUnit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
