package com.restaurantpos.app.kiosk

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrderConfirmationScreen(
    orderId: String,
    autoReturnCountdown: Int?,
    pickupCode: String?,
    onNewOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortId = orderId.takeLast(6).uppercase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.kiosk_order_placed),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (pickupCode != null) stringResource(R.string.kiosk_pickup_code_label)
                   else stringResource(R.string.kiosk_order_number_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
        ) {
            Text(
                text = pickupCode ?: "#$shortId",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // QR code encodes the full orderId
        QrCodeImage(
            content = orderId,
            modifier = Modifier.size(200.dp),
            darkColor = MaterialTheme.colorScheme.onSurface,
            lightColor = MaterialTheme.colorScheme.surface,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.kiosk_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.kiosk_wait_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNewOrder,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(52.dp),
        ) {
            Text(
                text = if (autoReturnCountdown != null && autoReturnCountdown > 0)
                    stringResource(R.string.kiosk_new_order_countdown, autoReturnCountdown)
                else
                    stringResource(R.string.kiosk_new_order),
                fontSize = 17.sp,
            )
        }
    }
}
