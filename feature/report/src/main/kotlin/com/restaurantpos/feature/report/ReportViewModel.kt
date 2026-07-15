package com.restaurantpos.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurantpos.core.domain.usecase.DailyReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val dailyReportUseCase: DailyReportUseCase,
    private val configRepo: com.restaurantpos.core.config.ConfigRepository,
) : ViewModel() {

    private val _trendUiState = MutableStateFlow(TrendUiState())
    val trendUiState: StateFlow<TrendUiState> = _trendUiState.asStateFlow()

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        // 默认最近7天
        setLastNDays(7)
    }

    fun setStartEpoch(epoch: Long) {
        val date = sdf.format(Date(epoch))
        _trendUiState.update { it.copy(startEpoch = epoch, startDate = date) }
    }

    fun setEndEpoch(epoch: Long) {
        val date = sdf.format(Date(epoch))
        _trendUiState.update { it.copy(endEpoch = epoch, endDate = date) }
    }

    fun setLastNDays(days: Int) {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val start = today.minusDays(days.toLong() - 1)
        val startEpoch = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endEpoch = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        _trendUiState.update {
            it.copy(
                startEpoch = startEpoch,
                endEpoch = endEpoch,
                startDate = start.toString(),
                endDate = today.toString(),
            )
        }
        loadTrend()
    }

    fun loadTrend() {
        val s = _trendUiState.value
        if (s.startDate.isBlank() || s.endDate.isBlank()) return

        viewModelScope.launch {
            _trendUiState.update { it.copy(isLoading = true, error = null) }
            try {
                val report = dailyReportUseCase.getTrendReport(s.startDate, s.endDate)
                val fmt = com.restaurantpos.core.config.AmountFormatter(configRepo.current())
                _trendUiState.update {
                    it.copy(
                        isLoading = false,
                        dataPoints = report.dataPoints,
                        totalRevenue = report.summary.totalRevenue,
                        totalOrders = report.summary.totalOrders,
                        avgDailyRevenue = report.summary.avgDailyRevenue,
                        revenueChangePercent = report.summary.revenueChangePercent,
                        totalRevenueFormatted = fmt.format(report.summary.totalRevenue),
                        avgDailyRevenueFormatted = fmt.format(report.summary.avgDailyRevenue),
                    )
                }
            } catch (e: Exception) {
                _trendUiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
