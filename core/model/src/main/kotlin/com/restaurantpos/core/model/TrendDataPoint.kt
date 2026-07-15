package com.restaurantpos.core.model

/**
 * 趋势数据点 - 用于时序数据展示
 * 
 * @param date 日期字符串 (yyyy-MM-dd)
 * @param netRevenueMinorUnit 净营收
 * @param orderCount 订单数
 * @param guestCount 客人数
 * @param avgCheckMinorUnit 均单
 * @param avgPerGuestMinorUnit 人均
 */
data class TrendDataPoint(
    val date: String,
    val netRevenueMinorUnit: Long = 0L,
    val orderCount: Int = 0,
    val guestCount: Int = 0,
    val avgCheckMinorUnit: Long = 0L,
    val avgPerGuestMinorUnit: Long = 0L
)

/**
 * 趋势报表 - 包含时间序列数据和汇总信息
 * 
 * @param dataPoints 时序数据点列表
 * @param summary 汇总统计
 */
data class TrendReport(
    val dataPoints: List<TrendDataPoint>,
    val summary: TrendSummary
)

/**
 * 趋势汇总统计
 */
data class TrendSummary(
    val totalRevenue: Long,
    val totalOrders: Int,
    val totalGuests: Int,
    val avgDailyRevenue: Long,
    val revenueChangePercent: Double, // 同比/环比变化百分比
    val orderChangePercent: Double
)
