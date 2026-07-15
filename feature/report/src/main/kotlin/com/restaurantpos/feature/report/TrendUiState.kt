package com.restaurantpos.feature.report

/**
 * RevenueTrendScreen 的 UI 状态
 */
data class TrendUiState(
    val startDate: String = "",
    val endDate: String = "",
    val startEpoch: Long = 0L,
    val endEpoch: Long = 0L,
    val isLoading: Boolean = false,
    val dataPoints: List<com.restaurantpos.core.model.TrendDataPoint> = emptyList(),
    val totalRevenue: Long = 0L,
    val totalOrders: Int = 0,
    val avgDailyRevenue: Long = 0L,
    val revenueChangePercent: Double = 0.0,
    val error: String? = null,
    // 预格式化的金额字符串，避免 Screen 层直接用 AmountFormatter
    val totalRevenueFormatted: String = "-",
    val avgDailyRevenueFormatted: String = "-",
)
