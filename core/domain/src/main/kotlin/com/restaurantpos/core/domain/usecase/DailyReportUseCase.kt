package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.model.DailySnapshot
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.PaymentStatus
import com.restaurantpos.core.model.TrendDataPoint
import com.restaurantpos.core.model.TrendReport
import com.restaurantpos.core.model.TrendSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日报表 UseCase - 用于生成时序数据和趋势分析
 * 
 * @param orderRepo 订单仓库
 * @param paymentRepo 支付仓库
 * @param reportRepo 报表数据仓库（用于缓存预计算结果）
 */
class DailyReportUseCase(
    private val orderRepo: OrderRepository,
    private val paymentRepo: PaymentRepository,
    private val reportRepo: ReportRepository,
    private val remoteTrendReport: (suspend (String, String, String) -> RemoteTrendReport?)? = null,
) {

    /**
     * 远程服务端返回的趋势报表（跨所有终端聚合）。
     */
    data class RemoteTrendReport(
        val dataPoints: List<RemoteTrendDataPoint>,
        val totalGrossRevenue: Long,
        val totalNetRevenue: Long,
        val totalOrderCount: Int,
        val totalGuestCount: Int,
        val avgOrderValue: Long,
        val avgSpendPerGuest: Long,
    )

    data class RemoteTrendDataPoint(
        val date: String,
        val grossRevenueMinorUnit: Long,
        val netRevenueMinorUnit: Long,
        val orderCount: Int,
        val guestCount: Int,
        val averageOrderValueMinorUnit: Long,
        val averageSpendPerGuestMinorUnit: Long,
    )
    
    /**
     * 生成指定日期的日快照
     * 
     * @param date 日期字符串 (yyyy-MM-dd)
     * @return 日快照对象
     */
    suspend fun generateDailySnapshot(date: String): DailySnapshot {
        val (fromEpoch, toEpoch) = dateToEpochRange(date)
        
        val orders = orderRepo.getClosedInRange(fromEpoch, toEpoch)
        val allPayments = orders.flatMap { paymentRepo.getByOrder(it.id) }
            .filter { it.createdAt in fromEpoch..toEpoch }
        
        val paid = allPayments.filter { it.status == PaymentStatus.PAID }
        val refunded = allPayments.filter { it.status == PaymentStatus.REFUNDED }
        
        val gross = paid.sumOf { it.amountMinorUnit }
        val refunds = refunded.sumOf { it.amountMinorUnit }
        val net = gross - refunds
        
        val closedOrders = orders.filter { it.status == OrderStatus.CLOSED }
        val orderCount = closedOrders.size
        val guestCount = closedOrders.sumOf { it.guestCount }
        
        val avgCheck = if (orderCount > 0) net / orderCount else 0L
        val avgPerGuest = if (guestCount > 0) net / guestCount else 0L
        
        val discountTotal = closedOrders.sumOf { it.discountMinorUnit }
        val taxTotal = closedOrders.sumOf { it.taxTotalMinorUnit }
        val serviceChargeTotal = closedOrders.sumOf { it.serviceChargeMinorUnit }
        val tipTotal = closedOrders.sumOf { it.tipMinorUnit }
        
        // 构建支付方式分布 JSON
        val paymentBreakdown = paid.groupBy { it.method.name }
            .mapValues { it.value.sumOf { payment -> payment.amountMinorUnit } }
        val paymentBreakdownJson = paymentBreakdown.entries.joinToString(",") { 
            "\"${it.key}\":${it.value}" 
        }
        
        val snapshot = DailySnapshot(
            date = date,
            netRevenueMinorUnit = net,
            grossRevenueMinorUnit = gross,
            orderCount = orderCount,
            guestCount = guestCount,
            avgCheckMinorUnit = avgCheck,
            avgPerGuestMinorUnit = avgPerGuest,
            discountTotalMinorUnit = discountTotal,
            taxTotalMinorUnit = taxTotal,
            serviceChargeTotalMinorUnit = serviceChargeTotal,
            tipTotalMinorUnit = tipTotal,
            paymentBreakdownJson = "{$paymentBreakdownJson}",
            updatedAt = System.currentTimeMillis()
        )
        
        // 保存到仓库
        reportRepo.saveDailySnapshot(snapshot)
        
        return snapshot
    }
    
    /**
     * 获取趋势数据
     * 
     * @param startDate 开始日期 (yyyy-MM-dd)
     * @param endDate 结束日期 (yyyy-MM-dd)
     * @param useCache 是否使用缓存的快照数据（默认 true）
     * @return 趋势报表
     */
    suspend fun getTrendReport(
        startDate: String, 
        endDate: String, 
        useCache: Boolean = true
    ): TrendReport {
        // 优先尝试服务端聚合报表
        val remote = remoteTrendReport?.invoke(startDate, endDate, "day")
        if (remote != null) {
            return buildRemoteTrendReport(remote)
        }

        // 降级：本地 Room DB 计算
        return buildLocalTrendReport(startDate, endDate, useCache)
    }

    private fun buildRemoteTrendReport(remote: RemoteTrendReport): TrendReport {
        val dataPoints = remote.dataPoints.map { dp ->
            TrendDataPoint(
                date = dp.date,
                netRevenueMinorUnit = dp.netRevenueMinorUnit,
                orderCount = dp.orderCount,
                guestCount = dp.guestCount,
                avgCheckMinorUnit = dp.averageOrderValueMinorUnit,
                avgPerGuestMinorUnit = dp.averageSpendPerGuestMinorUnit,
            )
        }
        val summary = TrendSummary(
            totalRevenue = remote.totalNetRevenue,
            totalOrders = remote.totalOrderCount,
            totalGuests = remote.totalGuestCount,
            avgDailyRevenue = remote.avgOrderValue,
            revenueChangePercent = 0.0,
            orderChangePercent = 0.0,
        )
        return TrendReport(dataPoints = dataPoints, summary = summary)
    }

    private suspend fun buildLocalTrendReport(
        startDate: String,
        endDate: String,
        useCache: Boolean,
    ): TrendReport {
        val dataPoints = if (useCache) {
            // 尝试从缓存读取
            val cached = reportRepo.getDailySnapshots(startDate, endDate)
            
            if (cached.size >= getDaysBetween(startDate, endDate)) {
                // 缓存数据完整，直接使用
                cached.map { it.toTrendDataPoint() }
            } else {
                // 缓存不完整，实时计算
                generateTrendDataPoints(startDate, endDate)
            }
        } else {
            // 强制实时计算
            generateTrendDataPoints(startDate, endDate)
        }
        
        val summary = calculateTrendSummary(dataPoints, startDate, endDate)
        
        return TrendReport(dataPoints = dataPoints, summary = summary)
    }
    
    /**
     * 批量生成日快照（用于 ETL 定时任务）
     * 
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    suspend fun generateDailySnapshotsInRange(startDate: String, endDate: String) {
        val snapshots = mutableListOf<DailySnapshot>()
        var currentDate = startDate
        
        while (currentDate <= endDate) {
            val snapshot = generateDailySnapshot(currentDate)
            snapshots.add(snapshot)
            currentDate = addDays(currentDate, 1)
        }
        
        reportRepo.saveDailySnapshots(snapshots)
    }
    
    // ==================== 私有方法 ====================
    
    private suspend fun generateTrendDataPoints(startDate: String, endDate: String): List<TrendDataPoint> {
        val dataPoints = mutableListOf<TrendDataPoint>()
        var currentDate = startDate
        
        while (currentDate <= endDate) {
            val snapshot = generateDailySnapshot(currentDate)
            dataPoints.add(snapshot.toTrendDataPoint())
            currentDate = addDays(currentDate, 1)
        }
        
        return dataPoints
    }
    
    private fun calculateTrendSummary(
        dataPoints: List<TrendDataPoint>, 
        startDate: String, 
        endDate: String
    ): TrendSummary {
        val totalRevenue = dataPoints.sumOf { it.netRevenueMinorUnit }
        val totalOrders = dataPoints.sumOf { it.orderCount }
        val totalGuests = dataPoints.sumOf { it.guestCount }
        val avgDailyRevenue = if (dataPoints.isNotEmpty()) totalRevenue / dataPoints.size else 0L
        
        // 计算同比/环比变化（需要获取上一周期的 dataPoints）
        val daysCount = getDaysBetween(startDate, endDate)
        val prevStartDate = addDays(startDate, -daysCount)
        val prevEndDate = addDays(endDate, -daysCount)
        
        // 这里简化实现，实际应该查询上一周期的数据
        val revenueChangePercent = 0.0
        val orderChangePercent = 0.0
        
        return TrendSummary(
            totalRevenue = totalRevenue,
            totalOrders = totalOrders,
            totalGuests = totalGuests,
            avgDailyRevenue = avgDailyRevenue,
            revenueChangePercent = revenueChangePercent,
            orderChangePercent = orderChangePercent
        )
    }
    
    private fun DailySnapshot.toTrendDataPoint(): TrendDataPoint {
        return TrendDataPoint(
            date = this.date,
            netRevenueMinorUnit = this.netRevenueMinorUnit,
            orderCount = this.orderCount,
            guestCount = this.guestCount,
            avgCheckMinorUnit = this.avgCheckMinorUnit,
            avgPerGuestMinorUnit = this.avgPerGuestMinorUnit
        )
    }
    
    private fun dateToEpochRange(date: String): Pair<Long, Long> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = sdf.parse(date)?.time ?: 0L
        val end = start + 24 * 60 * 60 * 1000 - 1
        return Pair(start, end)
    }
    
    private fun addDays(date: String, days: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(date) ?: return date
        cal.add(Calendar.DAY_OF_YEAR, days)
        return sdf.format(cal.time)
    }
    
    private fun getDaysBetween(startDate: String, endDate: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = sdf.parse(startDate)?.time ?: 0L
        val end = sdf.parse(endDate)?.time ?: 0L
        return ((end - start) / (24 * 60 * 60 * 1000)).toInt() + 1
    }
}
