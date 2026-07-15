package com.restaurantpos.app.pad.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantpos.core.config.AycePackage

/**
 * Package selection screen shown at session start in AYCE mode.
 * Inspired by Jamezz: guests choose their dining package before ordering starts.
 *
 * e.g.  [Standard – 5 rounds ¥88]  [Premium – 8 rounds ¥128]  [Unlimited – ¥168]
 */
@Composable
fun AycePackageScreen(
    packages: List<AycePackage>,
    onSelect: (AycePackage) -> Unit,
    onSkip: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.Dining,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Choose Your Dining Package",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Select a package to get started. Each package includes different round limits and item allowances.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(packages, key = { it.id }) { pkg ->
                    AycePackageCard(pkg = pkg, onSelect = { onSelect(pkg) })
                }
            }

            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.pad_continue_without_package))
            }
        }
    }
}

@Composable
private fun AycePackageCard(pkg: AycePackage, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.width(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pkg.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            if (pkg.description.isNotBlank()) {
                Text(pkg.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PackageDetailRow(
                    icon = Icons.Default.Repeat,
                    label = if (pkg.totalRoundsLimit == null) "Unlimited rounds" else "${pkg.totalRoundsLimit} rounds",
                )
                PackageDetailRow(
                    icon = Icons.Default.Restaurant,
                    label = if (pkg.maxItemsPerRound == 0) "No item limit" else "${pkg.maxItemsPerRound} items/round",
                )
                PackageDetailRow(
                    icon = Icons.Default.Timer,
                    label = "${pkg.minMinutesBetweenRounds} min between rounds",
                )
            }

            HorizontalDivider()

            if (pkg.priceMinorUnit > 0L) {
                Text(
                    "¥${pkg.priceMinorUnit / 100.0}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                )
                Text(stringResource(R.string.pad_per_person), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pad_select))
            }
        }
    }
}

@Composable
private fun PackageDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
