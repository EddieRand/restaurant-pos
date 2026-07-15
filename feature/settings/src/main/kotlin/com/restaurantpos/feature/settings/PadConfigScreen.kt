package com.restaurantpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AycePackage
import java.util.UUID

/**
 * Cashier-side configuration screen for tableside PAD devices.
 * Allows binding a PAD to a table, enabling AYCE mode, and managing packages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pad = state.config.padConfig

    var showAddPackage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pad_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(); onBack() }) {
                        Text(stringResource(R.string.settings_save))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {

            // ── Table binding ─────────────────────────────────────────────────
            item { SectionLabel("Table Binding") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pad.boundTableId,
                        onValueChange = { viewModel.updatePadConfig { p -> p.copy(boundTableId = it.take(64)) } },
                        label = { Text(stringResource(R.string.pad_table_id)) },
                        placeholder = { Text(stringResource(R.string.pad_table_id_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.TableRestaurant, contentDescription = null) },
                    )
                    OutlinedTextField(
                        value = pad.tableDisplayName,
                        onValueChange = { viewModel.updatePadConfig { p -> p.copy(tableDisplayName = it.take(64)) } },
                        label = { Text(stringResource(R.string.pad_display_name)) },
                        placeholder = { Text(stringResource(R.string.pad_display_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (pad.boundTableId.isBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "PAD will show \"not configured\" until a Table ID is set.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            // ── Idle screen ───────────────────────────────────────────────────
            item { SectionLabel("Idle Screen") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pad_idle_timeout), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Screensaver activates after this many seconds of inactivity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = pad.idleTimeoutSeconds.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { secs ->
                                    viewModel.updatePadConfig { p -> p.copy(idleTimeoutSeconds = secs.coerceIn(15, 600)) }
                                }
                            },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(stringResource(R.string.pad_seconds_suffix)) },
                        )
                    }

                    var promoText by remember(pad.idlePromoLines) {
                        mutableStateOf(pad.idlePromoLines.joinToString("\n"))
                    }
                    OutlinedTextField(
                        value = promoText,
                        onValueChange = {
                            promoText = it
                            viewModel.updatePadConfig { p ->
                                p.copy(idlePromoLines = it.lines().filter(String::isNotBlank))
                            }
                        },
                        label = { Text(stringResource(R.string.pad_promo_lines)) },
                        placeholder = { Text(stringResource(R.string.pad_promo_lines_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            }

            // ── Ordering options ──────────────────────────────────────────────
            item { SectionLabel("Ordering Options") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleRow(
                        label = "Allow item notes",
                        description = "Guest can add free-text notes to each item",
                        checked = pad.allowItemNotes,
                        onCheckedChange = { viewModel.updatePadConfig { p -> p.copy(allowItemNotes = it) } },
                    )
                    ToggleRow(
                        label = "Show running total",
                        description = "Displays cart subtotal in real time",
                        checked = pad.showRunningTotal,
                        onCheckedChange = { viewModel.updatePadConfig { p -> p.copy(showRunningTotal = it) } },
                    )
                    ToggleRow(
                        label = "Show order history",
                        description = "Guest can review previously submitted items",
                        checked = pad.showOrderHistory,
                        onCheckedChange = { viewModel.updatePadConfig { p -> p.copy(showOrderHistory = it) } },
                    )
                }
            }

            // ── AYCE (All-You-Can-Eat) ────────────────────────────────────────
            item { SectionLabel("All-You-Can-Eat (AYCE)") }

            item {
                ToggleRow(
                    label = "Enable AYCE mode",
                    description = "Activates round-based ordering with cooldown timers",
                    checked = pad.ayceEnabled,
                    onCheckedChange = { viewModel.updatePadConfig { p -> p.copy(ayceEnabled = it) } },
                )
            }

            if (pad.ayceEnabled) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.pad_ayce_default_rules), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "These apply when no package is selected. Per-package settings override these.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = pad.maxItemsPerRound.toString(),
                                    onValueChange = { v ->
                                        v.toIntOrNull()?.let { n ->
                                            viewModel.updatePadConfig { p -> p.copy(maxItemsPerRound = n.coerceIn(1, 99)) }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.pad_ayce_max_items)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                OutlinedTextField(
                                    value = pad.minMinutesBetweenRounds.toString(),
                                    onValueChange = { v ->
                                        v.toIntOrNull()?.let { n ->
                                            viewModel.updatePadConfig { p -> p.copy(minMinutesBetweenRounds = n.coerceIn(0, 120)) }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.pad_ayce_min_minutes)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }

                            OutlinedTextField(
                                value = pad.totalRoundsLimit?.toString() ?: "",
                                onValueChange = { v ->
                                    viewModel.updatePadConfig { p ->
                                        p.copy(totalRoundsLimit = v.toIntOrNull()?.coerceIn(1, 20))
                                    }
                                },
                                label = { Text(stringResource(R.string.pad_ayce_max_rounds)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text(stringResource(R.string.pad_ayce_unlimited)) },
                            )
                        }
                    }
                }

                // AYCE packages
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Packages",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showAddPackage = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.pad_ayce_add_package))
                        }
                    }
                }

                if (pad.aycePackages.isEmpty()) {
                    item {
                        Text(
                            "No packages yet — guests will use the default rules above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(pad.aycePackages, key = { it.id }) { pkg ->
                        AycePackageCard(
                            pkg = pkg,
                            onDelete = {
                                viewModel.updatePadConfig { p ->
                                    p.copy(aycePackages = p.aycePackages.filter { it.id != pkg.id })
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddPackage) {
        AddAycePackageDialog(
            onConfirm = { pkg ->
                viewModel.updatePadConfig { p -> p.copy(aycePackages = p.aycePackages + pkg) }
                showAddPackage = false
            },
            onDismiss = { showAddPackage = false },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AycePackageCard(pkg: AycePackage, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pkg.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (pkg.description.isNotBlank()) {
                    Text(pkg.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.pad_ayce_chip_items, pkg.maxItemsPerRound), style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.pad_ayce_chip_cooldown, pkg.minMinutesBetweenRounds), style = MaterialTheme.typography.labelSmall) },
                    )
                    pkg.totalRoundsLimit?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.pad_ayce_chip_max_rounds, it), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun AddAycePackageDialog(
    onConfirm: (AycePackage) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxItems by remember { mutableStateOf("8") }
    var cooldown by remember { mutableStateOf("15") }
    var rounds by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pad_ayce_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pad_ayce_package_name)) },
                    placeholder = { Text(stringResource(R.string.pad_ayce_package_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.pad_ayce_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxItems,
                        onValueChange = { maxItems = it },
                        label = { Text(stringResource(R.string.pad_ayce_max_items)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = cooldown,
                        onValueChange = { cooldown = it },
                        label = { Text(stringResource(R.string.pad_ayce_cooldown_min)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = rounds,
                    onValueChange = { rounds = it },
                    label = { Text(stringResource(R.string.pad_ayce_max_rounds)) },
                    placeholder = { Text(stringResource(R.string.pad_ayce_unlimited)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(AycePackage(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            description = description.trim(),
                            maxItemsPerRound = maxItems.toIntOrNull()?.coerceIn(1, 99) ?: 8,
                            minMinutesBetweenRounds = cooldown.toIntOrNull()?.coerceIn(0, 120) ?: 15,
                            totalRoundsLimit = rounds.toIntOrNull()?.coerceIn(1, 20),
                        ))
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
