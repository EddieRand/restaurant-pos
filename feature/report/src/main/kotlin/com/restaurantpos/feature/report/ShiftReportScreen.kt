package com.restaurantpos.feature.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.*
import java.text.SimpleDateFormat
import java.util.*

private val dateChipFmt = SimpleDateFormat("MMM d", Locale.US)

@Composable
fun ShiftReportScreen(
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    viewModel: ShiftReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val formatter = remember(state.regionConfig) { AmountFormatter(state.regionConfig) }

    LaunchedEffect(Unit) { if (!state.hasReport) viewModel.generate() }

    Column(Modifier.fillMaxSize().background(PosContentBg)) {
        // Header
        Row(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Text(stringResource(R.string.rpt_subtitle), fontSize = 13.sp, color = PosTextSecondary)
            }
            OutlineAction(stringResource(R.string.report_order_history), Icons.AutoMirrored.Filled.ReceiptLong, onNavigateToHistory)
            Spacer(Modifier.width(8.dp))
            OutlineAction(stringResource(R.string.report_close_shift), Icons.Filled.PointOfSale) { viewModel.openShiftCloseDialog() }
        }
        // Tabs
        Row(Modifier.fillMaxWidth().background(PosShellBg).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(
                R.string.rpt_tab_overview, R.string.rpt_tab_sales, R.string.rpt_tab_orders, R.string.rpt_tab_items,
                R.string.rpt_tab_categories, R.string.rpt_tab_payments, R.string.rpt_tab_employees, R.string.rpt_tab_customers,
            ).forEachIndexed { i, res -> DashTab(stringResource(res), i == 0) {} }
        }
        HorizontalDivider(color = PosHairline)

        // Date range + compare + export
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateChip("${dateChipFmt.format(Date(state.fromEpoch))} – ${dateChipFmt.format(Date(state.toEpoch))}") { showFromPicker = true }
            Spacer(Modifier.width(10.dp))
            DropChip(stringResource(R.string.rpt_compare_to))
            Spacer(Modifier.weight(1f))
            OutlineAction(stringResource(R.string.rpt_export), Icons.Filled.FileDownload) {}
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.clickableNoRipple { viewModel.generate() }) {
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp).size(18.dp))
            }
        }

        state.error?.let { err ->
            Surface(color = PosBadgeSpicyBg, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, color = PosBadgeSpicyFg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) }
                }
            }
        }

        if (state.isLoading && !state.hasReport) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SunmiOrange) }
        } else {
            DashboardBody(state, formatter)
        }
    }

    // Dialogs (Z-report shift close)
    if (state.isShiftCloseDialogOpen) {
        ShiftCloseDialog(state.cashSales, { viewModel.closeShift(it) }, { viewModel.dismissShiftCloseDialog() })
    }
    state.shiftClosePrintResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissShiftCloseResult() },
            title = { Text(stringResource(R.string.report_shift_closed)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.dismissShiftCloseResult() }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    if (showFromPicker) DatePickerModal(state.fromEpoch, { viewModel.setFrom(it); showFromPicker = false; viewModel.generate() }, { showFromPicker = false })
    if (showToPicker) DatePickerModal(state.toEpoch, { viewModel.setTo(it); showToPicker = false; viewModel.generate() }, { showToPicker = false })
}

@Composable
private fun DashboardBody(state: ReportUiState, formatter: AmountFormatter) {
    val net = state.netRevenueMinorUnit
    val grossProfit = (net * 0.52).toLong()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // KPI row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(stringResource(R.string.rpt_kpi_total_sales), state.net, state.netChangePercent, Icons.Filled.AttachMoney, Modifier.weight(1f))
            KpiCard(stringResource(R.string.rpt_kpi_orders), state.orderCount.toString(), state.orderCountChangePercent, Icons.AutoMirrored.Filled.ReceiptLong, Modifier.weight(1f))
            KpiCard(stringResource(R.string.rpt_kpi_aov), state.averageOrderValue, state.netChangePercent - state.orderCountChangePercent, Icons.Filled.TrendingUp, Modifier.weight(1f))
            KpiCard(stringResource(R.string.rpt_kpi_gross_profit), formatter.format(grossProfit), state.netChangePercent * 1.1, Icons.Filled.ShowChart, Modifier.weight(1f))
        }

        // Sales over time + (business summary / time of day)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(stringResource(R.string.rpt_sales_over_time), Modifier.weight(2f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallToggle(stringResource(R.string.rpt_daily), true)
                    SmallToggle(stringResource(R.string.rpt_weekly), false)
                    SmallToggle(stringResource(R.string.rpt_monthly), false)
                }
                Spacer(Modifier.height(12.dp))
                val thisP = listOf(0.55f, 0.85f, 0.78f, 1f, 0.66f, 0.9f, 1.05f)
                val lastP = thisP.map { it * 0.82f }
                LineChart(listOf(LineSeries(thisP, SunmiOrange), LineSeries(lastP, PosChartGrey, dashed = true)))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, fontSize = 11.sp, color = PosTextMuted) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionCard(stringResource(R.string.rpt_business_summary)) {
                    SummaryLine(stringResource(R.string.rpt_net_sales), state.gross, PosTextPrimary)
                    SummaryLine(stringResource(R.string.rpt_tax), state.totalTax, PosTextPrimary)
                    SummaryLine(stringResource(R.string.rpt_discounts), "- ${if (state.totalDiscount == "-") formatter.format(0) else state.totalDiscount}", PosTextSecondary)
                    SummaryLine(stringResource(R.string.rpt_refunds), "- ${state.refunds}", PosTextSecondary)
                    HorizontalDivider(color = PosHairline, modifier = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.rpt_net_revenue), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                        Text(state.net, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    }
                }
                SectionCard(stringResource(R.string.rpt_time_of_day)) {
                    val hours = listOf(0.05f, 0.03f, 0.02f, 0.04f, 0.1f, 0.3f, 0.6f, 0.8f, 0.7f, 0.5f, 0.45f, 0.9f, 1f, 0.85f, 0.5f, 0.4f, 0.55f, 0.8f, 0.95f, 0.9f, 0.6f, 0.35f, 0.2f, 0.1f)
                    BarChart(hours, SunmiOrange)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("12a", "6a", "12p", "6p", "11p").forEach { Text(it, fontSize = 10.sp, color = PosTextMuted) }
                    }
                }
            }
        }

        // Category donut / top items / payment donut
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(stringResource(R.string.rpt_sales_by_category), Modifier.weight(1f)) {
                val cats = listOf(
                    Triple(stringResource(R.string.rpt_cat_food), 0.57f, SunmiOrange),
                    Triple(stringResource(R.string.rpt_cat_beverages), 0.20f, PosChartBlue),
                    Triple(stringResource(R.string.rpt_cat_desserts), 0.10f, PosChartGreen),
                    Triple(stringResource(R.string.rpt_cat_alcohol), 0.09f, PosChartPurple),
                    Triple(stringResource(R.string.rpt_cat_others), 0.04f, PosChartGrey),
                )
                DonutChart(cats.map { DonutSegment(it.second, it.third) })
                Spacer(Modifier.height(10.dp))
                cats.forEach { (label, frac, color) -> LegendRow(color, label, "${(frac * 100).toInt()}%") }
            }
            SectionCard(stringResource(R.string.rpt_top_selling), Modifier.weight(1f)) {
                if (state.topItems.isEmpty()) Text("—", fontSize = 13.sp, color = PosTextMuted)
                state.topItems.take(5).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.rank}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SunmiOrange, modifier = Modifier.width(20.dp))
                        Text(item.name, fontSize = 13.sp, color = PosTextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("×${item.quantity}", fontSize = 12.sp, color = PosTextSecondary, modifier = Modifier.padding(end = 10.dp))
                        Text(item.revenue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                    }
                }
            }
            SectionCard(stringResource(R.string.rpt_payment_methods), Modifier.weight(1f)) {
                val palette = listOf(PosChartBlue, PosChartGreen, PosChartPurple, SunmiOrange, PosChartGrey)
                val methods = state.byMethod.filter { it.netMinorUnit > 0 }
                val segs = if (methods.isNotEmpty()) methods.mapIndexed { i, m -> Triple(m.method.name, m.netMinorUnit.toFloat(), palette[i % palette.size]) }
                else listOf(Triple("CARD", 0.62f, PosChartBlue), Triple("CASH", 0.25f, PosChartGreen), Triple("MOBILE", 0.13f, PosChartPurple))
                DonutChart(segs.map { DonutSegment(it.second, it.third) })
                Spacer(Modifier.height(10.dp))
                val tot = segs.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
                segs.forEach { (label, v, color) -> LegendRow(color, label, "${(v / tot * 100).toInt()}%") }
            }
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun KpiCard(label: String, value: String, changePct: Double, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = PosCardBg, border = BorderStroke(1.dp, PosHairline), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = PosTextSecondary, maxLines = 1)
                Surface(shape = CircleShape, color = PosContentBg) {
                    Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.padding(6.dp).size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            val up = changePct >= 0
            val color = if (up) PosChartGreen else PosError
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (up) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("%+.1f%%".format(changePct), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = PosCardBg, border = BorderStroke(1.dp, PosHairline), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PosTextSecondary)
        Text(value, fontSize = 13.sp, color = valueColor)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, pct: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = PosTextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Text(pct, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PosTextSecondary)
    }
}

@Composable
private fun DashTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickableNoRipple(onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) SunmiOrange else PosTextSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(if (selected) 24.dp else 0.dp).background(SunmiOrange))
    }
}

@Composable
private fun SmallToggle(label: String, selected: Boolean) {
    Surface(shape = RoundedCornerShape(8.dp), color = if (selected) SunmiOrangeContainer else PosContentBg) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) SunmiOrange else PosTextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 14.sp, color = PosTextPrimary)
        }
    }
}

@Composable
private fun DropChip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple {}) {
        Text(text, fontSize = 13.sp, color = PosTextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun OutlineAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple(onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 14.sp, color = PosTextPrimary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerModal(initialEpoch: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpoch)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onConfirm(it) } }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) { DatePicker(state = datePickerState) }
}

@Composable
private fun ShiftCloseDialog(expectedCashMinorUnit: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var actualText by remember { mutableStateOf("") }
    val expectedMajor = expectedCashMinorUnit / 100.0
    val actualMinorUnit = actualText.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
    val variance = actualMinorUnit - expectedCashMinorUnit
    val varianceColor = when { variance > 0 -> PosSuccess; variance < 0 -> PosError; else -> Color.Unspecified }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_close_shift)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.report_expected_cash), style = MaterialTheme.typography.bodyMedium)
                    Text("%.2f".format(expectedMajor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = actualText, onValueChange = { actualText = it },
                    label = { Text(stringResource(R.string.report_actual_cash)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                if (actualText.isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.report_variance), style = MaterialTheme.typography.bodyMedium)
                        Text("%+.2f".format(variance / 100.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = varianceColor)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(actualMinorUnit) }, enabled = actualText.isNotBlank() && actualText.toDoubleOrNull() != null) { Text(stringResource(R.string.report_confirm_close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
