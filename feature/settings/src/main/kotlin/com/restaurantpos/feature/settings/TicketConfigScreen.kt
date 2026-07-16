package com.restaurantpos.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.PrintConfig
import com.restaurantpos.core.config.PrinterConnectionType
import com.restaurantpos.core.config.PrinterStation
import com.restaurantpos.core.config.TicketTemplateConfig
import com.restaurantpos.core.model.TicketType
import com.restaurantpos.core.designsystem.ReceiptBorder
import com.restaurantpos.core.designsystem.ReceiptDivider
import com.restaurantpos.core.designsystem.ReceiptInk
import com.restaurantpos.core.designsystem.ReceiptInkLight
import com.restaurantpos.core.designsystem.ReceiptPaper

// ── Entry screen: list of all ticket types ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val printConfig = state.config.printConfig
    var editingType by remember { mutableStateOf<TicketType?>(null) }
    var editingStations by remember { mutableStateOf(false) }

    if (editingType != null) {
        TicketTypeDetailScreen(
            ticketType = editingType!!,
            template = printConfig.templateFor(editingType!!),
            stations = printConfig.stations,
            currencySymbol = state.config.currencySymbol,
            onSave = { updated ->
                viewModel.updateTicketTemplate(updated)
                editingType = null
            },
            onBack = { editingType = null },
        )
        return
    }

    if (editingStations) {
        PrinterStationsScreen(
            stations = printConfig.stations,
            onSave = { stations ->
                viewModel.updatePrinterStations(stations)
                editingStations = false
            },
            onBack = { editingStations = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { editingStations = true }) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_printers))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Configure each ticket type — enable/disable, customise content, and route to a printer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            val groups = mapOf(
                "Order Tickets" to listOf(
                    TicketType.CUSTOMER_RECEIPT,
                    TicketType.PRE_BILL,
                    TicketType.KITCHEN_TICKET,
                    TicketType.BAR_TICKET,
                    TicketType.TAKEAWAY_LABEL,
                ),
                "Transaction Tickets" to listOf(
                    TicketType.REFUND_RECEIPT,
                    TicketType.VOID_RECEIPT,
                ),
                "Reports" to listOf(
                    TicketType.SHIFT_REPORT,
                    TicketType.DAILY_REPORT,
                ),
            )

            groups.forEach { (groupName, types) ->
                item {
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                }
                items(types) { type ->
                    val template = printConfig.templateFor(type)
                    val station = printConfig.stationById(template.printerStationId)
                    TicketTypeRow(
                        type = type,
                        template = template,
                        stationName = station?.name ?: if (template.printerStationId.isEmpty()) "Default Printer" else template.printerStationId,
                        onToggle = { viewModel.updateTicketTemplate(template.copy(enabled = it)) },
                        onClick = { editingType = type },
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketTypeRow(
    type: TicketType,
    template: TicketTemplateConfig,
    stationName: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (template.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = type.icon(),
                contentDescription = null,
                tint = if (template.enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(type.displayName(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(stationName)
                        if (template.copies > 1) append(" · ×${template.copies}")
                        if (template.categoryRoutes.isNotEmpty()) append(" · ${template.categoryRoutes.size} routes")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = template.enabled,
                onCheckedChange = onToggle,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Detail / edit screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketTypeDetailScreen(
    ticketType: TicketType,
    template: TicketTemplateConfig,
    stations: List<PrinterStation>,
    currencySymbol: String,
    onSave: (TicketTemplateConfig) -> Unit,
    onBack: () -> Unit,
) {
    var current by remember(template) { mutableStateOf(template) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticketType.displayName()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(current) }) {
                        Text(stringResource(R.string.settings_save))
                    }
                },
            )
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Left: settings panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Enable + copies
                SectionHeader("Print Settings")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ticket_enable), modifier = Modifier.weight(1f))
                    Switch(checked = current.enabled, onCheckedChange = { current = current.copy(enabled = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ticket_copies), modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (current.copies > 1) current = current.copy(copies = current.copies - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }
                    Text("${current.copies}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { if (current.copies < 5) current = current.copy(copies = current.copies + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                // Printer routing
                SectionHeader("Printer Routing")
                var showStationDropdown by remember { mutableStateOf(false) }
                val selectedStation = stations.find { it.id == current.printerStationId }
                ExposedDropdownMenuBox(
                    expanded = showStationDropdown,
                    onExpandedChange = { showStationDropdown = it },
                ) {
                    OutlinedTextField(
                        value = selectedStation?.name ?: "Default Printer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ticket_printer_station)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStationDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = showStationDropdown,
                        onDismissRequest = { showStationDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ticket_default_printer)) },
                            onClick = { current = current.copy(printerStationId = ""); showStationDropdown = false },
                        )
                        stations.forEach { station ->
                            DropdownMenuItem(
                                text = { Text(station.name) },
                                onClick = { current = current.copy(printerStationId = station.id); showStationDropdown = false },
                            )
                        }
                    }
                }

                // Category routing (kitchen/bar only)
                if (ticketType == TicketType.KITCHEN_TICKET || ticketType == TicketType.BAR_TICKET) {
                    SectionHeader("Category Routes")
                    Text(
                        "Route specific menu categories to different printer stations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CategoryRoutingEditor(
                        routes = current.categoryRoutes,
                        stations = stations,
                        onChange = { current = current.copy(categoryRoutes = it) },
                    )
                }

                // Title override
                SectionHeader("Title")
                OutlinedTextField(
                    value = current.titleOverride,
                    onValueChange = { current = current.copy(titleOverride = it) },
                    label = { Text(stringResource(R.string.ticket_title_override)) },
                    placeholder = { Text(ticketType.defaultTitle()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Header / footer
                SectionHeader("Header & Footer")
                var headerText by remember(current.headerLines) { mutableStateOf(current.headerLines.joinToString("\n")) }
                OutlinedTextField(
                    value = headerText,
                    onValueChange = {
                        headerText = it
                        current = current.copy(headerLines = it.lines().filter(String::isNotBlank))
                    },
                    label = { Text(stringResource(R.string.ticket_header_lines)) },
                    placeholder = { Text(stringResource(R.string.ticket_header_lines_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 5,
                )
                var footerText by remember(current.footerLines) { mutableStateOf(current.footerLines.joinToString("\n")) }
                OutlinedTextField(
                    value = footerText,
                    onValueChange = {
                        footerText = it
                        current = current.copy(footerLines = it.lines().filter(String::isNotBlank))
                    },
                    label = { Text(stringResource(R.string.ticket_footer_lines)) },
                    placeholder = { Text(stringResource(R.string.ticket_footer_lines_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 5,
                )

                // Content toggles
                SectionHeader("Display Fields")
                ContentToggleGrid(current = current, onUpdate = { current = it })
            }

            // Right: live preview
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TicketPreview(
                    template = current,
                    ticketType = ticketType,
                    currencySymbol = currencySymbol,
                )
            }
        }
    }
}

// ── Visual ticket preview ─────────────────────────────────────────────────

@Composable
fun TicketPreview(
    template: TicketTemplateConfig,
    ticketType: TicketType,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    val paperColor = ReceiptPaper
    val lineColor = ReceiptInk

    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(paperColor)
            .border(1.dp, ReceiptBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val mono = FontFamily.Monospace
        val bodyStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = mono, color = lineColor, fontSize = 11.sp,
        )
        val boldStyle = bodyStyle.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
        val smallStyle = bodyStyle.copy(fontSize = 9.sp, color = ReceiptInkLight)

        // Header lines
        template.headerLines.forEach { line ->
            Text(line, style = boldStyle, textAlign = TextAlign.Center)
        }
        if (template.headerLines.isNotEmpty()) {
            PreviewDivider()
        }

        // Title
        val title = template.titleOverride.ifBlank { ticketType.defaultTitle() }
        Text(title, style = boldStyle.copy(fontSize = 15.sp), textAlign = TextAlign.Center)

        PreviewDivider()

        // Meta fields
        if (template.showTimestamp) PreviewRow("Date/Time:", "06/06/2026 12:34", bodyStyle)
        if (template.showOrderId) PreviewRow("Order #:", "A-0042", bodyStyle)
        if (template.showTableNumber) PreviewRow("Table:", "T-05", bodyStyle)
        if (template.showOrderType) PreviewRow("Type:", "Dine-In", bodyStyle)
        if (template.showServerName) PreviewRow("Server:", "Alice", bodyStyle)
        if (template.showCustomerName) PreviewRow("Customer:", "John D.", bodyStyle)

        if (template.showOrderId || template.showTableNumber) PreviewDivider()

        // Items
        Text(stringResource(R.string.ticket_preview_items), style = smallStyle, modifier = Modifier.fillMaxWidth())
        PreviewItemRow("Burger ×2", "${currencySymbol}24.00", bodyStyle, template.showItemPrices)
        PreviewItemRow("  + Extra Cheese", "+${currencySymbol}2.00", bodyStyle, template.showItemPrices)
        PreviewItemRow("Fries ×1", "${currencySymbol}5.00", bodyStyle, template.showItemPrices)
        PreviewItemRow("Coke ×2", "${currencySymbol}6.00", bodyStyle, template.showItemPrices)
        if (template.showNotes) {
            Text(stringResource(R.string.ticket_preview_note), style = smallStyle, modifier = Modifier.fillMaxWidth())
        }

        if (template.showSubtotal || template.showTax || template.showDiscount || template.showTotal) {
            PreviewDivider()
        }

        // Totals
        if (template.showSubtotal) PreviewRow("Subtotal:", "${currencySymbol}37.00", bodyStyle)
        if (template.showDiscount) PreviewRow("Discount:", "-${currencySymbol}2.00", bodyStyle)
        if (template.showServiceCharge) PreviewRow("Service:", "${currencySymbol}1.85", bodyStyle)
        if (template.showTax) PreviewRow("Tax (10%):", "${currencySymbol}3.70", bodyStyle)
        if (template.showTotal) {
            PreviewDivider()
            PreviewRow("TOTAL:", "${currencySymbol}40.55", boldStyle.copy(fontSize = 14.sp))
        }
        if (template.showPaymentMethod) {
            PreviewDivider()
            PreviewRow("Payment:", "Cash", bodyStyle)
            if (template.showChangeAmount) PreviewRow("Change:", "${currencySymbol}9.45", bodyStyle)
        }

        // Tax ID
        if (template.showTaxId && template.taxId.isNotBlank()) {
            PreviewDivider()
            Text(stringResource(R.string.ticket_preview_tax_id, template.taxId), style = smallStyle, textAlign = TextAlign.Center)
        }

        // QR code placeholder
        if (template.showQrCode) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, lineColor)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.ticket_preview_qr), style = smallStyle)
            }
        }

        // Footer lines
        if (template.footerLines.isNotEmpty()) {
            PreviewDivider()
            template.footerLines.forEach { line ->
                Text(line, style = smallStyle, textAlign = TextAlign.Center)
            }
        }

        // Tear perforation visual
        Spacer(Modifier.height(6.dp))
        Text("- - - - - - - - - - - - - - - -", style = smallStyle)
    }
}

@Composable
private fun PreviewDivider() {
    HorizontalDivider(color = ReceiptDivider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun PreviewRow(label: String, value: String, style: androidx.compose.ui.text.TextStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, textAlign = TextAlign.End)
    }
}

@Composable
private fun PreviewItemRow(name: String, price: String, style: androidx.compose.ui.text.TextStyle, showPrice: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = style, modifier = Modifier.weight(1f))
        if (showPrice) Text(price, style = style, textAlign = TextAlign.End)
    }
}

// ── Content toggle grid ───────────────────────────────────────────────────

@Composable
private fun ContentToggleGrid(
    current: TicketTemplateConfig,
    onUpdate: (TicketTemplateConfig) -> Unit,
) {
    val toggles = listOf(
        Triple("Merchant Name", current.showMerchantName) { v: Boolean -> onUpdate(current.copy(showMerchantName = v)) },
        Triple("Merchant Address", current.showMerchantAddress) { v: Boolean -> onUpdate(current.copy(showMerchantAddress = v)) },
        Triple("Phone", current.showMerchantPhone) { v: Boolean -> onUpdate(current.copy(showMerchantPhone = v)) },
        Triple("Order ID", current.showOrderId) { v: Boolean -> onUpdate(current.copy(showOrderId = v)) },
        Triple("Order Type", current.showOrderType) { v: Boolean -> onUpdate(current.copy(showOrderType = v)) },
        Triple("Table Number", current.showTableNumber) { v: Boolean -> onUpdate(current.copy(showTableNumber = v)) },
        Triple("Server Name", current.showServerName) { v: Boolean -> onUpdate(current.copy(showServerName = v)) },
        Triple("Timestamp", current.showTimestamp) { v: Boolean -> onUpdate(current.copy(showTimestamp = v)) },
        Triple("Item Prices", current.showItemPrices) { v: Boolean -> onUpdate(current.copy(showItemPrices = v)) },
        Triple("Subtotal", current.showSubtotal) { v: Boolean -> onUpdate(current.copy(showSubtotal = v)) },
        Triple("Tax", current.showTax) { v: Boolean -> onUpdate(current.copy(showTax = v)) },
        Triple("Discount", current.showDiscount) { v: Boolean -> onUpdate(current.copy(showDiscount = v)) },
        Triple("Service Charge", current.showServiceCharge) { v: Boolean -> onUpdate(current.copy(showServiceCharge = v)) },
        Triple("Total", current.showTotal) { v: Boolean -> onUpdate(current.copy(showTotal = v)) },
        Triple("Payment Method", current.showPaymentMethod) { v: Boolean -> onUpdate(current.copy(showPaymentMethod = v)) },
        Triple("Change Amount", current.showChangeAmount) { v: Boolean -> onUpdate(current.copy(showChangeAmount = v)) },
        Triple("Customer Name", current.showCustomerName) { v: Boolean -> onUpdate(current.copy(showCustomerName = v)) },
        Triple("Notes", current.showNotes) { v: Boolean -> onUpdate(current.copy(showNotes = v)) },
        Triple("Tax ID", current.showTaxId) { v: Boolean -> onUpdate(current.copy(showTaxId = v)) },
        Triple("QR Code", current.showQrCode) { v: Boolean -> onUpdate(current.copy(showQrCode = v)) },
    )

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        toggles.forEach { (label, checked, onChange) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(!checked) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    modifier = Modifier.height(24.dp),
                )
            }
        }
    }

    AnimatedVisibility(current.showTaxId) {
        OutlinedTextField(
            value = current.taxId,
            onValueChange = { onUpdate(current.copy(taxId = it)) },
            label = { Text(stringResource(R.string.ticket_tax_id_number)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    AnimatedVisibility(current.showQrCode) {
        OutlinedTextField(
            value = current.qrCodeTemplate,
            onValueChange = { onUpdate(current.copy(qrCodeTemplate = it)) },
            label = { Text(stringResource(R.string.ticket_qr_content)) },
            placeholder = { Text(stringResource(R.string.ticket_qr_content_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

// ── Category routing editor ───────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CategoryRoutingEditor(
    routes: Map<String, String>,
    stations: List<PrinterStation>,
    onChange: (Map<String, String>) -> Unit,
) {
    var newCategory by remember { mutableStateOf("") }
    var newStation by remember { mutableStateOf(stations.firstOrNull()?.id ?: "") }

    routes.entries.forEach { (catId, stationId) ->
        val stationName = stations.find { it.id == stationId }?.name ?: stationId
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(catId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ticket_routed_to, stationName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { onChange(routes - catId) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newCategory,
            onValueChange = { newCategory = it },
            label = { Text(stringResource(R.string.ticket_category_id)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Text("→")
        var dropdownOpen by remember { mutableStateOf(false) }
        val selectedName = stations.find { it.id == newStation }?.name ?: newStation
        ExposedDropdownMenuBox(expanded = dropdownOpen, onExpandedChange = { dropdownOpen = it }) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f).menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
            )
            ExposedDropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                stations.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name) }, onClick = { newStation = s.id; dropdownOpen = false })
                }
            }
        }
        IconButton(
            onClick = {
                if (newCategory.isNotBlank() && newStation.isNotBlank()) {
                    onChange(routes + (newCategory.trim() to newStation))
                    newCategory = ""
                }
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ticket_add_route))
        }
    }
}

// ── Printer stations screen ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterStationsScreen(
    stations: List<PrinterStation>,
    onSave: (List<PrinterStation>) -> Unit,
    onBack: () -> Unit,
) {
    var current by remember(stations) { mutableStateOf(stations) }
    var editingStation by remember { mutableStateOf<PrinterStation?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_printer_stations)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { onSave(current) }) { Text(stringResource(R.string.settings_save)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ticket_add_printer))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(current, key = { it.id }) { station ->
                Card(modifier = Modifier.fillMaxWidth().clickable { editingStation = station }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(station.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "${station.connectionType.name} · ${station.paperWidthMm}mm" +
                                    if (station.address.isNotEmpty()) " · ${station.address}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { current = current.filter { it.id != station.id } }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingStation != null) {
        val initial = editingStation ?: PrinterStation(
            id = "station_${System.currentTimeMillis()}",
            name = "",
        )
        PrinterStationDialog(
            station = initial,
            onConfirm = { s ->
                current = if (current.any { it.id == s.id }) {
                    current.map { if (it.id == s.id) s else it }
                } else {
                    current + s
                }
                showAddDialog = false
                editingStation = null
            },
            onDismiss = { showAddDialog = false; editingStation = null },
        )
    }
}

@Composable
private fun PrinterStationDialog(
    station: PrinterStation,
    onConfirm: (PrinterStation) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(station.name) }
    var address by remember { mutableStateOf(station.address) }
    var connType by remember { mutableStateOf(station.connectionType) }
    var paperWidth by remember { mutableStateOf(station.paperWidthMm.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station.name.isEmpty()) "Add Printer" else "Edit Printer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ticket_station_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrinterConnectionType.entries.forEach { type ->
                        FilterChip(
                            selected = connType == type,
                            onClick = { connType = type },
                            label = { Text(type.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (connType != PrinterConnectionType.BUILT_IN) {
                    OutlinedTextField(
                        value = address, onValueChange = { address = it },
                        label = { Text(if (connType == PrinterConnectionType.NETWORK) "IP Address" else "Address") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(58, 80).forEach { w ->
                        FilterChip(
                            selected = paperWidth == w.toString(),
                            onClick = { paperWidth = w.toString() },
                            label = { Text(stringResource(R.string.ticket_paper_width_mm, w)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(station.copy(
                        name = name.trim(),
                        address = address.trim(),
                        connectionType = connType,
                        paperWidthMm = paperWidth.toIntOrNull() ?: 58,
                    ))
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ── Section header helper ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

// ── Extension helpers ─────────────────────────────────────────────────────

@Composable
fun TicketType.displayName(): String = when (this) {
    TicketType.CUSTOMER_RECEIPT -> stringResource(R.string.ticket_type_customer_receipt)
    TicketType.KITCHEN_TICKET   -> stringResource(R.string.ticket_type_kitchen)
    TicketType.BAR_TICKET       -> stringResource(R.string.ticket_type_bar)
    TicketType.TAKEAWAY_LABEL   -> stringResource(R.string.ticket_type_takeaway_label)
    TicketType.PRE_BILL         -> stringResource(R.string.ticket_type_pre_bill)
    TicketType.REFUND_RECEIPT   -> stringResource(R.string.ticket_type_refund)
    TicketType.VOID_RECEIPT     -> stringResource(R.string.ticket_type_void)
    TicketType.SHIFT_REPORT     -> stringResource(R.string.ticket_type_shift_report)
    TicketType.DAILY_REPORT     -> stringResource(R.string.ticket_type_daily_report)
}

@Composable
fun TicketType.defaultTitle(): String = when (this) {
    TicketType.CUSTOMER_RECEIPT -> stringResource(R.string.ticket_title_receipt)
    TicketType.KITCHEN_TICKET   -> stringResource(R.string.ticket_title_kitchen)
    TicketType.BAR_TICKET       -> stringResource(R.string.ticket_title_bar)
    TicketType.TAKEAWAY_LABEL   -> stringResource(R.string.ticket_title_takeaway)
    TicketType.PRE_BILL         -> stringResource(R.string.ticket_title_pre_bill)
    TicketType.REFUND_RECEIPT   -> stringResource(R.string.ticket_title_refund)
    TicketType.VOID_RECEIPT     -> stringResource(R.string.ticket_title_void)
    TicketType.SHIFT_REPORT     -> stringResource(R.string.ticket_title_shift_report)
    TicketType.DAILY_REPORT     -> stringResource(R.string.ticket_title_daily_report)
}

@Composable
fun TicketType.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    TicketType.CUSTOMER_RECEIPT -> Icons.Default.Receipt
    TicketType.KITCHEN_TICKET   -> Icons.Default.Restaurant
    TicketType.BAR_TICKET       -> Icons.Default.LocalBar
    TicketType.TAKEAWAY_LABEL   -> Icons.Default.ShoppingBag
    TicketType.PRE_BILL         -> Icons.Default.ReceiptLong
    TicketType.REFUND_RECEIPT   -> Icons.Default.CurrencyExchange
    TicketType.VOID_RECEIPT     -> Icons.Default.Cancel
    TicketType.SHIFT_REPORT     -> Icons.Default.SwapHoriz
    TicketType.DAILY_REPORT     -> Icons.Default.BarChart
}
