package com.restaurantpos.app.cashier

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.model.WaiterCall
import com.restaurantpos.core.model.WaiterCallReason

/**
 * Persistent top-of-screen notification bar for tableside PAD waiter calls.
 * Appears when any table calls for service; each chip shows the table and reason.
 * Staff taps to acknowledge.
 */
@Composable
fun WaiterCallBar(
    modifier: Modifier = Modifier,
    viewModel: WaiterCallViewModel = hiltViewModel(),
) {
    val calls by viewModel.pendingCalls.collectAsState()

    AnimatedVisibility(
        visible = calls.isNotEmpty(),
        enter = slideInVertically { -it } + expandVertically(),
        exit = slideOutVertically { -it } + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.waiter_calls_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(calls, key = { it.id }) { call ->
                        WaiterCallChip(call = call, onAck = { viewModel.acknowledge(call) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WaiterCallChip(call: WaiterCall, onAck: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onAck,
        label = {
            Text(
                "${call.tableDisplayName} · ${call.reason.label()}",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Icon(call.reason.icon(), contentDescription = null, modifier = Modifier.size(14.dp))
        },
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.waiter_call_acknowledge), modifier = Modifier.size(14.dp))
        },
        shape = RoundedCornerShape(20.dp),
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.error,
            labelColor = MaterialTheme.colorScheme.onError,
            leadingIconColor = MaterialTheme.colorScheme.onError,
            trailingIconColor = MaterialTheme.colorScheme.onError,
        ),
    )
}

@Composable
private fun WaiterCallReason.label() = when (this) {
    WaiterCallReason.GENERAL        -> stringResource(R.string.waiter_call_service)
    WaiterCallReason.REQUEST_BILL   -> stringResource(R.string.waiter_call_bill)
    WaiterCallReason.NEED_WATER     -> stringResource(R.string.waiter_call_water)
    WaiterCallReason.NEED_UTENSILS  -> stringResource(R.string.waiter_call_utensils)
}

private fun WaiterCallReason.icon() = when (this) {
    WaiterCallReason.GENERAL        -> Icons.Default.NotificationsActive
    WaiterCallReason.REQUEST_BILL   -> Icons.Default.Receipt
    WaiterCallReason.NEED_WATER     -> Icons.Default.LocalDrink
    WaiterCallReason.NEED_UTENSILS  -> Icons.Default.Restaurant
}
