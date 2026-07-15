package com.restaurantpos.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.domain.usecase.DailyReportUseCase
import com.restaurantpos.core.domain.usecase.ShiftReportUseCase
import com.restaurantpos.core.hardware.PrinterPort
import com.restaurantpos.core.hardware.ReceiptData
import com.restaurantpos.core.hardware.ReceiptLine
import com.restaurantpos.core.model.PaymentMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MethodRow(
    val method: PaymentMethod,
    val gross: String,
    val refunds: String,
    val net: String,
    val netMinorUnit: Long = 0L,
)

data class TopItemRow(
    val rank: Int,
    val name: String,
    val quantity: Int,
    val revenue: String,
    val revenueMinorUnit: Long = 0L,
)

data class ReportUiState(
    val fromEpoch: Long = 0L,
    val toEpoch: Long = 0L,
    val isLoading: Boolean = false,
    val orderCount: Int = 0,
    val gross: String = "-",
    val refunds: String = "-",
    val net: String = "-",
    val cashSales: Long = 0L,
    val byMethod: List<MethodRow> = emptyList(),
    val topItems: List<TopItemRow> = emptyList(),
    val error: String? = null,
    val hasReport: Boolean = false,
    val isShiftCloseDialogOpen: Boolean = false,
    val shiftClosePrintResult: String? = null,
    // Enhanced metrics
    val totalDiscount: String = "-",
    val totalTip: String = "-",
    val totalServiceCharge: String = "-",
    val totalTax: String = "-",
    val totalGuestCount: Int = 0,
    val averageOrderValue: String = "-",
    val averageSpendPerGuest: String = "-",
    // 昨日对比
    val showYesterdayComparison: Boolean = false,
    val yesterdayNet: String = "-",
    val yesterdayOrderCount: String = "-",
    val yesterdayGuestCount: String = "-",
    val netChangePercent: Double = 0.0,
    val orderCountChangePercent: Double = 0.0,
    val guestCountChangePercent: Double = 0.0,
    // Raw values for charts/formatting (the analytics dashboard)
    val netRevenueMinorUnit: Long = 0L,
    val grossRevenueMinorUnit: Long = 0L,
    val totalTaxMinorUnit: Long = 0L,
    val totalDiscountMinorUnit: Long = 0L,
    val totalRefundsMinorUnit: Long = 0L,
    val regionConfig: com.restaurantpos.core.config.RegionConfig = com.restaurantpos.core.config.DefaultRegionConfig,
)

@HiltViewModel
class ShiftReportViewModel @Inject constructor(
    private val shiftReport: ShiftReportUseCase,
    private val dailyReport: DailyReportUseCase,
    private val configRepo: ConfigRepository,
    private val printer: PrinterPort,
    private val reportRepo: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(defaultDateRange())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private fun defaultDateRange(): ReportUiState {
        val zone = runCatching { ZoneId.of(configRepo.current().timeZone) }.getOrElse { ZoneId.of("UTC") }
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return ReportUiState(fromEpoch = from, toEpoch = to)
    }

    fun setFrom(epoch: Long) = _state.update { it.copy(fromEpoch = epoch, hasReport = false) }
    fun setTo(epoch: Long) = _state.update { it.copy(toEpoch = epoch, hasReport = false) }

    fun generate() {
        val s = _state.value
        if (s.toEpoch <= s.fromEpoch) {
            _state.update { it.copy(error = "End time must be after start time") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val report = shiftReport(s.fromEpoch, s.toEpoch)
                val fmt = AmountFormatter(configRepo.current())
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasReport = true,
                        orderCount = report.orderCount,
                        gross = fmt.format(report.grossRevenueMinorUnit),
                        refunds = fmt.format(report.totalRefundsMinorUnit),
                        net = fmt.format(report.netRevenueMinorUnit),
                        cashSales = report.byMethod
                            .firstOrNull { it.method == PaymentMethod.CASH }
                            ?.netMinorUnit ?: 0L,
                        byMethod = report.byMethod.map { m ->
                            MethodRow(
                                method = m.method,
                                gross = fmt.format(m.grossMinorUnit),
                                refunds = fmt.format(m.refundsMinorUnit),
                                net = fmt.format(m.netMinorUnit),
                                netMinorUnit = m.netMinorUnit,
                            )
                        },
                        topItems = report.topItems.mapIndexed { idx, item ->
                            TopItemRow(
                                rank = idx + 1,
                                name = item.name,
                                quantity = item.quantitySold,
                                revenue = fmt.format(item.revenueMinorUnit),
                                revenueMinorUnit = item.revenueMinorUnit,
                            )
                        },
                        totalDiscount = if (report.totalDiscountMinorUnit > 0) fmt.format(report.totalDiscountMinorUnit) else "-",
                        totalTip = if (report.totalTipMinorUnit > 0) fmt.format(report.totalTipMinorUnit) else "-",
                        totalServiceCharge = if (report.totalServiceChargeMinorUnit > 0) fmt.format(report.totalServiceChargeMinorUnit) else "-",
                        totalTax = fmt.format(report.totalTaxMinorUnit),
                        totalGuestCount = report.totalGuestCount,
                        averageOrderValue = if (report.orderCount > 0) fmt.format(report.averageOrderValueMinorUnit) else "-",
                        averageSpendPerGuest = if (report.totalGuestCount > 0) fmt.format(report.averageSpendPerGuestMinorUnit) else "-",
                        // 昨日对比
                        showYesterdayComparison = report.yesterdayComparison != null,
                        yesterdayNet = report.yesterdayComparison?.let { fmt.format(it.yesterdayNetRevenueMinorUnit) } ?: "-",
                        yesterdayOrderCount = report.yesterdayComparison?.yesterdayOrderCount?.toString() ?: "-",
                        yesterdayGuestCount = report.yesterdayComparison?.yesterdayGuestCount?.toString() ?: "-",
                        netChangePercent = report.yesterdayComparison?.netRevenueChangePercent ?: 0.0,
                        orderCountChangePercent = report.yesterdayComparison?.orderCountChangePercent ?: 0.0,
                        guestCountChangePercent = report.yesterdayComparison?.guestCountChangePercent ?: 0.0,
                        netRevenueMinorUnit = report.netRevenueMinorUnit,
                        grossRevenueMinorUnit = report.grossRevenueMinorUnit,
                        totalTaxMinorUnit = report.totalTaxMinorUnit,
                        totalDiscountMinorUnit = report.totalDiscountMinorUnit,
                        totalRefundsMinorUnit = report.totalRefundsMinorUnit,
                        regionConfig = configRepo.current(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun openShiftCloseDialog() {
        if (!_state.value.hasReport) generate()
        _state.update { it.copy(isShiftCloseDialogOpen = true) }
    }

    fun dismissShiftCloseDialog() {
        _state.update { it.copy(isShiftCloseDialogOpen = false) }
    }

    fun closeShift(actualCashMinorUnit: Long) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isShiftCloseDialogOpen = false) }
            try {
                // Save DailySnapshot for today (ensures yesterday comparison works)
                val zone = runCatching { ZoneId.of(configRepo.current().timeZone) }.getOrElse { ZoneId.of("UTC") }
                val today = LocalDate.now(zone).toString()
                dailyReport.generateDailySnapshot(today)

                val cfg = configRepo.current()
                val fmt = AmountFormatter(cfg)
                val variance = actualCashMinorUnit - s.cashSales
                val lines = buildList {
                    add(ReceiptLine("=== Z-REPORT ===", 0L))
                    add(ReceiptLine("Orders", s.orderCount.toLong()))
                    add(ReceiptLine("Gross", 0L))
                    s.byMethod.forEach { m -> add(ReceiptLine("  ${m.method.name}", 0L)) }
                    add(ReceiptLine("Net Revenue", 0L))
                    add(ReceiptLine("Expected Cash", s.cashSales))
                    add(ReceiptLine("Actual Cash", actualCashMinorUnit))
                    add(ReceiptLine("Variance", variance))
                }
                val receiptData = ReceiptData(
                    orderId = "ZREPORT-${System.currentTimeMillis()}",
                    lines = lines,
                    totalMinorUnit = s.cashSales,
                    currencySymbol = cfg.currencySymbol,
                    currencyMinorDigits = cfg.currencyMinorDigits,
                )
                val printResult = printer.printReceipt(receiptData)
                val resultMsg = when (printResult) {
                    is com.restaurantpos.core.hardware.PrintResult.Success ->
                        "Z-report printed. Variance: ${fmt.format(variance)}"
                    is com.restaurantpos.core.hardware.PrintResult.Failure ->
                        "Print failed: ${printResult.reason}. Variance: ${fmt.format(variance)}"
                }
                _state.update { it.copy(isLoading = false, shiftClosePrintResult = resultMsg) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissShiftCloseResult() = _state.update { it.copy(shiftClosePrintResult = null) }
}
