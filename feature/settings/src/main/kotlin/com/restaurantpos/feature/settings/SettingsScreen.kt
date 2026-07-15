package com.restaurantpos.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.RegionConfig
import com.restaurantpos.core.config.TaxMode
import com.restaurantpos.core.config.TaxRate
import com.restaurantpos.core.designsystem.*
import java.util.UUID

/** Which subset of fields a category's detail form should show — see [SettingsForm]. */
private enum class SettingsFormCategory { BUSINESS, POS, USERS, PAYMENT, SYSTEM }

private data class SettingsCategory(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val tint: Color,
    /** Non-null for categories that open [SettingsForm]; null for dead/unimplemented ones. */
    val formCategory: SettingsFormCategory? = null,
    val opensTicketConfig: Boolean = false,
    val badgeRes: Int? = null,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToTicketConfig: () -> Unit = {},
    onNavigateToPadConfig: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // Regression: this used to be a single Boolean, so every category card opened the exact
    // same generic form regardless of which one was tapped — found via user report. Tracking
    // which category was opened lets SettingsForm render only that category's fields.
    var openCategory by remember { mutableStateOf<SettingsFormCategory?>(null) }
    Box(Modifier.fillMaxSize()) {
        SettingsHub(onOpenForm = { openCategory = it }, onNavigateToTicketConfig = onNavigateToTicketConfig)
        openCategory?.let { category ->
            SettingsForm(
                category = category,
                onBack = { openCategory = null },
                onNavigateToTicketConfig = onNavigateToTicketConfig,
                onNavigateToPadConfig = onNavigateToPadConfig,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun SettingsHub(onOpenForm: (SettingsFormCategory) -> Unit, onNavigateToTicketConfig: () -> Unit) {
    val categories = listOf(
        SettingsCategory(R.string.set_business, R.string.set_business_desc, Icons.Filled.Storefront, SunmiOrange, SettingsFormCategory.BUSINESS),
        SettingsCategory(R.string.set_pos, R.string.set_pos_desc, Icons.Filled.PointOfSale, PosChartBlue, SettingsFormCategory.POS),
        SettingsCategory(R.string.set_users, R.string.set_users_desc, Icons.Filled.Group, PosChartPurple, SettingsFormCategory.USERS),
        SettingsCategory(R.string.set_printers, R.string.set_printers_desc, Icons.Filled.Print, PosChartGreen, opensTicketConfig = true),
        SettingsCategory(R.string.set_payment, R.string.set_payment_desc, Icons.Filled.CreditCard, PosChartAmber, SettingsFormCategory.PAYMENT),
        SettingsCategory(R.string.set_notifications, R.string.set_notifications_desc, Icons.Filled.Notifications, PosChartBlue),
        SettingsCategory(R.string.set_security, R.string.set_security_desc, Icons.Filled.Security, PosBadgeSpicyFg),
        SettingsCategory(R.string.set_backup, R.string.set_backup_desc, Icons.Filled.Backup, PosChartGreen),
        SettingsCategory(R.string.set_integrations, R.string.set_integrations_desc, Icons.Filled.Extension, PosChartGreen),
        SettingsCategory(R.string.set_system, R.string.set_system_desc, Icons.Filled.Settings, PosChartGrey, SettingsFormCategory.SYSTEM),
        SettingsCategory(R.string.set_subscription, R.string.set_subscription_desc, Icons.Filled.ReceiptLong, PosChartPurple, badgeRes = R.string.set_pro_plan),
    )

    Column(Modifier.fillMaxSize().background(PosContentBg)) {
        Column(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Text(stringResource(R.string.settings_subtitle), fontSize = 13.sp, color = PosTextSecondary)
        }
        HorizontalDivider(color = PosHairline)

        Row(Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left: search + category grid
            Column(Modifier.weight(1.7f)) {
                HubSearch()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_categories), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Spacer(Modifier.height(10.dp))
                categories.chunked(2).forEach { rowCats ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowCats.forEach { c ->
                            CategoryCard(c, Modifier.weight(1f)) {
                                when {
                                    c.opensTicketConfig -> onNavigateToTicketConfig()
                                    c.formCategory != null -> onOpenForm(c.formCategory)
                                }
                            }
                        }
                        if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            // Right column
            Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CompanyInfoCard()
                BusinessHoursCard()
                QuickSettingsCard()
                Text("${stringResource(R.string.set_footer)}  ·  ${stringResource(R.string.set_version)}", fontSize = 11.sp, color = PosTextMuted)
            }
        }
    }
}

@Composable
private fun CategoryCard(c: SettingsCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = PosCardBg, border = BorderStroke(1.dp, PosHairline), modifier = modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(c.icon, contentDescription = null, tint = c.tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(c.titleRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                    c.badgeRes?.let {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = PosBadgeVeganBg) {
                            Text(stringResource(it), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PosBadgeVeganFg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(c.descRes), fontSize = 12.sp, color = PosTextSecondary, maxLines = 2)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HubSearch() {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_search), fontSize = 14.sp, color = PosTextMuted)
        }
    }
}

@Composable
private fun HubCard(title: String, action: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = PosCardBg, border = BorderStroke(1.dp, PosHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                action?.let { Text(it, fontSize = 13.sp, color = SunmiOrange) }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CompanyInfoCard() {
    HubCard(stringResource(R.string.set_company_info), stringResource(R.string.set_edit)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SunmiOrangeContainer), contentAlignment = Alignment.Center) {
                Text("☕", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SUNMI Café", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = PosBadgeVeganBg) {
                        Text(stringResource(R.string.set_active), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PosBadgeVeganFg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("123 Coffee Street", fontSize = 13.sp, color = PosTextSecondary)
                Text("San Francisco, CA 94107, USA", fontSize = 13.sp, color = PosTextSecondary)
                Spacer(Modifier.height(4.dp))
                Text("+1 (415) 123-4567", fontSize = 13.sp, color = PosTextSecondary)
                Text("hello@sunmicafe.com", fontSize = 13.sp, color = PosTextSecondary)
            }
        }
    }
}

@Composable
private fun BusinessHoursCard() {
    HubCard(stringResource(R.string.set_business_hours), stringResource(R.string.set_edit)) {
        val hours = listOf(
            "Monday" to "8:00 AM – 10:00 PM", "Tuesday" to "8:00 AM – 10:00 PM",
            "Wednesday" to "8:00 AM – 10:00 PM", "Thursday" to "8:00 AM – 11:00 PM",
            "Friday" to "8:00 AM – 11:00 PM", "Saturday" to "9:00 AM – 11:00 PM",
            "Sunday" to "9:00 AM – 9:00 PM",
        )
        hours.forEach { (d, h) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d, fontSize = 13.sp, color = PosTextSecondary)
                Text(h, fontSize = 13.sp, color = PosTextPrimary)
            }
        }
    }
}

@Composable
private fun QuickSettingsCard() {
    var serviceCharge by remember { mutableStateOf(true) }
    var tax by remember { mutableStateOf(true) }
    var kitchen by remember { mutableStateOf(true) }
    var lowStock by remember { mutableStateOf(true) }
    var customerDisplay by remember { mutableStateOf(false) }
    HubCard(stringResource(R.string.set_quick_settings)) {
        QsToggle(stringResource(R.string.set_qs_service_charge), serviceCharge) { serviceCharge = it }
        QsToggle(stringResource(R.string.set_qs_tax), tax) { tax = it }
        QsToggle(stringResource(R.string.set_qs_kitchen_display), kitchen) { kitchen = it }
        QsToggle(stringResource(R.string.set_qs_low_stock), lowStock) { lowStock = it }
        QsToggle(stringResource(R.string.set_qs_customer_display), customerDisplay) { customerDisplay = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.set_qs_auto_logout), fontSize = 14.sp, color = PosTextPrimary)
            Surface(shape = RoundedCornerShape(8.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("30 min", fontSize = 13.sp, color = PosTextPrimary)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun QsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = PosTextPrimary)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = SunmiOrange))
    }
}

@Composable
private fun SettingsForm(
    category: SettingsFormCategory,
    onBack: () -> Unit,
    onNavigateToTicketConfig: () -> Unit = {},
    onNavigateToPadConfig: () -> Unit = {},
    viewModel: SettingsViewModel,
) {
    val state by viewModel.state.collectAsState()
    val cfg = state.config

    var showAddTax by remember { mutableStateOf(false) }
    var showAddUser by remember { mutableStateOf(false) }

    if (state.saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            viewModel.dismissSaved()
        }
    }

    Column(Modifier.fillMaxSize().background(PosContentBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(22.dp).clickableNoRipple(onBack))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Spacer(Modifier.weight(1f))
            if (state.saved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PosBadgeVeganFg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_saved), fontSize = 14.sp, color = PosBadgeVeganFg)
                    Spacer(Modifier.width(14.dp))
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.clickableNoRipple { viewModel.save() }) {
                Text(stringResource(R.string.settings_save), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        HorizontalDivider(color = PosHairline)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Format preview — only relevant where currency is actually shown.
            if (category == SettingsFormCategory.PAYMENT) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = SunmiOrange, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_preview), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.height(4.dp))
                            Text(state.previewAmount, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            if (category == SettingsFormCategory.BUSINESS) {
                item {
                    SettingsCard(stringResource(R.string.settings_terminal_id)) {
                        OutlinedTextField(
                            value = cfg.terminalId,
                            onValueChange = viewModel::setTerminalId,
                            label = { Text(stringResource(R.string.settings_terminal_id)) },
                            placeholder = { Text(stringResource(R.string.settings_terminal_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                item {
                    SettingsCard(stringResource(R.string.settings_locale)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cfg.locale, viewModel::setLocale, label = { Text(stringResource(R.string.settings_locale_hint)) }, placeholder = { Text(stringResource(R.string.settings_locale_placeholder)) }, modifier = Modifier.weight(1f))
                            OutlinedTextField(cfg.timeZone, viewModel::setTimeZone, label = { Text(stringResource(R.string.settings_timezone)) }, placeholder = { Text(stringResource(R.string.settings_timezone_placeholder)) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (category == SettingsFormCategory.PAYMENT) {
                item {
                    SettingsCard(stringResource(R.string.settings_currency)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cfg.currencyCode, viewModel::setCurrencyCode, label = { Text(stringResource(R.string.settings_currency_code)) }, modifier = Modifier.weight(1f))
                            OutlinedTextField(cfg.currencySymbol, viewModel::setCurrencySymbol, label = { Text(stringResource(R.string.settings_currency_symbol)) }, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                cfg.currencyMinorDigits.toString(),
                                { viewModel.setCurrencyMinorDigits(it.toIntOrNull() ?: cfg.currencyMinorDigits) },
                                label = { Text(stringResource(R.string.settings_minor_digits)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    SettingsCard(stringResource(R.string.settings_tax)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_tax_mode), fontSize = 14.sp, color = PosTextSecondary)
                            Spacer(Modifier.weight(1f))
                            TaxMode.entries.forEach { mode ->
                                Pill(mode.name, cfg.defaultTaxMode == mode) { viewModel.setTaxMode(mode) }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        cfg.availableTaxRates.forEach { rate ->
                            HorizontalDivider(color = PosHairline, modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${rate.name}  (${rate.ratePermille / 10.0}%)", fontSize = 14.sp, color = PosTextPrimary, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Delete, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(20.dp).clickableNoRipple { viewModel.removeTaxRate(rate.id) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        AddRow(stringResource(R.string.settings_add_tax)) { showAddTax = true }
                    }
                }
            }

            if (category == SettingsFormCategory.SYSTEM) {
                item {
                    SettingsCard(stringResource(R.string.settings_number_format)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(cfg.thousandsSeparator.toString(), { if (it.length == 1) viewModel.setThousandsSeparator(it[0]) }, label = { Text(stringResource(R.string.settings_thousands_sep)) }, modifier = Modifier.weight(1f))
                            OutlinedTextField(cfg.decimalSeparator.toString(), { if (it.length == 1) viewModel.setDecimalSeparator(it[0]) }, label = { Text(stringResource(R.string.settings_decimal_sep)) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (category == SettingsFormCategory.POS) {
                item {
                    SettingsCard(stringResource(R.string.settings_receipt_format)) {
                        ReceiptFormatSection(cfg = cfg, viewModel = viewModel)
                    }
                }

                item {
                    SettingsCard(stringResource(R.string.settings_hardware)) {
                        NavigationRow(Icons.Default.Print, stringResource(R.string.settings_ticket_printer), stringResource(R.string.settings_ticket_printer_desc), onNavigateToTicketConfig)
                        Spacer(Modifier.height(8.dp))
                        NavigationRow(Icons.Default.TabletAndroid, stringResource(R.string.settings_pad), stringResource(R.string.settings_pad_desc), onNavigateToPadConfig)
                    }
                }
            }

            if (category == SettingsFormCategory.USERS) {
                item {
                    SettingsCard(stringResource(R.string.settings_staff)) {
                        state.users.forEachIndexed { i, user ->
                            if (i > 0) HorizontalDivider(color = PosHairline, modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(SunmiOrangeContainer), contentAlignment = Alignment.Center) {
                                    Text(user.displayName.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SunmiOrange)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PosTextPrimary)
                                    Text(user.roleId, fontSize = 12.sp, color = PosTextSecondary)
                                }
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_deactivate), tint = PosTextMuted, modifier = Modifier.size(20.dp).clickableNoRipple { viewModel.deactivateUser(user.id) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        AddRow(stringResource(R.string.settings_add_staff)) { showAddUser = true }
                    }
                }
            }
        }
    }

    if (showAddTax) {
        AddTaxRateDialog(
            onConfirm = { name, permille ->
                viewModel.addTaxRate(TaxRate(id = UUID.randomUUID().toString(), name = name, ratePermille = permille))
                showAddTax = false
            },
            onDismiss = { showAddTax = false },
        )
    }

    if (showAddUser) {
        AddStaffDialog(
            onConfirm = { name, pin, role ->
                viewModel.addUser(name, pin, role)
                showAddUser = false
            },
            onDismiss = { showAddUser = false },
        )
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = PosCardBg, border = BorderStroke(1.dp, PosHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SunmiOrange else PosShellBg,
        border = if (selected) null else BorderStroke(1.dp, PosChipBorder),
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else PosTextSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PosShellBg,
        border = BorderStroke(1.dp, PosChipBorder),
        modifier = Modifier.fillMaxWidth().clickableNoRipple(onClick),
    ) {
        Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SunmiOrange)
        }
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PosContentBg,
        modifier = Modifier.fillMaxWidth().clickableNoRipple(onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(SunmiOrangeContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = SunmiOrange)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                Text(description, fontSize = 12.sp, color = PosTextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PosTextMuted)
        }
    }
}

@Composable
private fun ReceiptFormatSection(cfg: RegionConfig, viewModel: SettingsViewModel) {
    val rc = cfg.receiptConfig
    var headerText by remember(rc.headerLines) { mutableStateOf(rc.headerLines.joinToString("\n")) }
    var footerText by remember(rc.footerLines) { mutableStateOf(rc.footerLines.joinToString("\n")) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = headerText,
            onValueChange = {
                headerText = it
                viewModel.setReceiptHeader(it.lines().filter(String::isNotBlank))
            },
            label = { Text(stringResource(R.string.settings_receipt_header)) },
            placeholder = { Text(stringResource(R.string.settings_receipt_header_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        OutlinedTextField(
            value = footerText,
            onValueChange = {
                footerText = it
                viewModel.setReceiptFooter(it.lines().filter(String::isNotBlank))
            },
            label = { Text(stringResource(R.string.settings_receipt_footer)) },
            placeholder = { Text(stringResource(R.string.settings_receipt_footer_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_show_tax_id),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = rc.showTaxId,
                onCheckedChange = viewModel::setShowTaxId,
                colors = SwitchDefaults.colors(checkedTrackColor = SunmiOrange),
            )
        }

        if (rc.showTaxId) {
            OutlinedTextField(
                value = rc.taxId,
                onValueChange = viewModel::setTaxId,
                label = { Text(stringResource(R.string.settings_tax_id)) },
                placeholder = { Text(stringResource(R.string.settings_tax_id_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun AddTaxRateDialog(
    onConfirm: (name: String, ratePermille: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_tax)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_tax_name)) },
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.settings_tax_rate_pct)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val permille = ((rate.toDoubleOrNull() ?: 0.0) * 10).toLong()
                    if (name.isNotBlank()) onConfirm(name, permille)
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun AddStaffDialog(
    onConfirm: (name: String, pin: String, roleId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var selectedRoleId by remember { mutableStateOf("waiter") }

    val roleDisplayNames = mapOf(
        "admin" to "管理员",
        "manager" to "经理",
        "cashier" to "收银员",
        "waiter" to "服务员",
    )
    val roleIds = listOf("admin", "manager", "cashier", "waiter")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_staff)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.settings_staff_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text(stringResource(R.string.settings_staff_pin)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.settings_staff_role), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    roleIds.forEach { rid ->
                        FilterChip(
                            selected = selectedRoleId == rid,
                            onClick = { selectedRoleId = rid },
                            label = { Text(roleDisplayNames[rid] ?: rid, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (displayName.isNotBlank() && pin.length >= 4) onConfirm(displayName, pin, selectedRoleId) },
                enabled = displayName.isNotBlank() && pin.length >= 4,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
