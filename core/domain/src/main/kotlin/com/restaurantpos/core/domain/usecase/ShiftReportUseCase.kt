package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.OrderRepository
import com.restaurantpos.core.domain.repository.PaymentRepository
import com.restaurantpos.core.domain.repository.ReportRepository
import com.restaurantpos.core.model.OrderItemStatus
import com.restaurantpos.core.model.OrderStatus
import com.restaurantpos.core.model.PaymentMethod
import com.restaurantpos.core.model.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ShiftReportUseCase(
    private val orderRepo: OrderRepository,
    private val paymentRepo: PaymentRepository,
    private val reportRepo: ReportRepository,
    private val remoteShiftReport: (suspend (Long, Long) -> RemoteShiftReport?)? = null,
) {
    data class MethodBreakdown(
        val method: PaymentMethod,
        val grossMinorUnit: Long,
        val refundsMinorUnit: Long,
    ) {
        val netMinorUnit: Long get() = grossMinorUnit - refundsMinorUnit
    }

    data class TopItem(
        val menuItemId: String,
        val name: String,
        val quantitySold: Int,
        val revenueMinorUnit: Long,
    )

    /**
     * 昨日对比数据（用于底部对比条）
     */
    data class YesterdayComparison(
        val yesterdayNetRevenueMinorUnit: Long,
        val yesterdayOrderCount: Int,
        val yesterdayGuestCount: Int,
        val netRevenueChangePercent: Double,   // 正=涨，负=跌
        val orderCountChangePercent: Double,
        val guestCountChangePercent: Double,
    )

    /**
     * 远程服务端返回的报表数据（跨所有终端聚合）。
     * 由 [remoteShiftReport] lambda 注入，通常来自 [HttpReportApi]。
     */
    data class RemoteShiftReport(
        val orderCount: Int,
        val grossRevenueMinorUnit: Long,
        val totalDiscountMinorUnit: Long,
        val totalTipMinorUnit: Long,
        val totalServiceChargeMinorUnit: Long,
        val totalTaxMinorUnit: Long,
        val totalGuestCount: Int,
        val paymentMethodBreakdown: Map<String, Long>,
    )

    data class ShiftReport(
        val fromEpoch: Long,
        val toEpoch: Long,
        val grossRevenueMinorUnit: Long,
        val totalRefundsMinorUnit: Long,
        val orderCount: Int,
        val byMethod: List<MethodBreakdown>,
        val topItems: List<TopItem> = emptyList(),
        val totalDiscountMinorUnit: Long = 0L,
        val totalTipMinorUnit: Long = 0L,
        val totalServiceChargeMinorUnit: Long = 0L,
        val totalTaxMinorUnit: Long = 0L,
        val totalGuestCount: Int = 0,
        // 昨日对比（可为 null，首次使用无昨日数据时）
        val yesterdayComparison: YesterdayComparison? = null,
    ) {
        val netRevenueMinorUnit: Long get() = grossRevenueMinorUnit - totalRefundsMinorUnit
        val averageOrderValueMinorUnit: Long
            get() = if (orderCount > 0) netRevenueMinorUnit / orderCount else 0L
        val averageSpendPerGuestMinorUnit: Long
            get() = if (totalGuestCount > 0) netRevenueMinorUnit / totalGuestCount else 0L
    }

    suspend operator fun invoke(fromEpoch: Long, toEpoch: Long, topItemsLimit: Int = 5): ShiftReport {
        require(toEpoch > fromEpoch) { "toEpoch must be after fromEpoch" }

        // 优先尝试服务端聚合报表（涵盖所有终端数据）
        val remote = remoteShiftReport?.invoke(fromEpoch, toEpoch)
        if (remote != null) {
            return buildRemoteShiftReport(fromEpoch, toEpoch, remote)
        }

        // 降级：本地 Room DB 单终端计算
        return buildLocalShiftReport(fromEpoch, toEpoch, topItemsLimit)
    }

    private suspend fun buildRemoteShiftReport(fromEpoch: Long, toEpoch: Long, remote: RemoteShiftReport): ShiftReport {
        val byMethod = PaymentMethod.entries.mapNotNull { method ->
            val amount = remote.paymentMethodBreakdown[method.name] ?: return@mapNotNull null
            MethodBreakdown(method, amount, 0L)
        }

        // 昨日对比仍然从本地计算（跨终端对比数据）
        val yesterdayComparison = calculateYesterdayComparison(fromEpoch, toEpoch, emptyList())

        return ShiftReport(
            fromEpoch = fromEpoch,
            toEpoch = toEpoch,
            grossRevenueMinorUnit = remote.grossRevenueMinorUnit,
            totalRefundsMinorUnit = 0L,
            orderCount = remote.orderCount,
            byMethod = byMethod,
            topItems = emptyList(),  // 远程暂不返回 TopItems，后续可扩展
            totalDiscountMinorUnit = remote.totalDiscountMinorUnit,
            totalTipMinorUnit = remote.totalTipMinorUnit,
            totalServiceChargeMinorUnit = remote.totalServiceChargeMinorUnit,
            totalTaxMinorUnit = remote.totalTaxMinorUnit,
            totalGuestCount = remote.totalGuestCount,
            yesterdayComparison = yesterdayComparison,
        )
    }

    private suspend fun buildLocalShiftReport(fromEpoch: Long, toEpoch: Long, topItemsLimit: Int): ShiftReport {

        val orders = orderRepo.getClosedInRange(fromEpoch, toEpoch)
        val allPayments = orders.flatMap { paymentRepo.getByOrder(it.id) }
            .filter { it.createdAt in fromEpoch..toEpoch }

        val paid = allPayments.filter { it.status == PaymentStatus.PAID }
        val refunded = allPayments.filter { it.status == PaymentStatus.REFUNDED }

        val gross = paid.sumOf { it.amountMinorUnit }
        val refunds = refunded.sumOf { it.amountMinorUnit }

        val byMethod = PaymentMethod.entries.mapNotNull { method ->
            val g = paid.filter { it.method == method }.sumOf { it.amountMinorUnit }
            val r = refunded.filter { it.method == method }.sumOf { it.amountMinorUnit }
            if (g == 0L && r == 0L) null
            else MethodBreakdown(method, g, r)
        }

        val closedOrders = orders.filter { it.status == OrderStatus.CLOSED }

        // Build top-selling items from order items (exclude voided items)
        val topItems = closedOrders
            .flatMap { orderRepo.getItemsByOrder(it.id) }
            .filter { it.status != OrderItemStatus.REFUNDED && it.status != OrderItemStatus.COMPED }
            .groupBy { it.menuItemId }
            .map { (menuItemId, items) ->
                val firstName = items.firstOrNull()?.menuItemNameSnapshot?.values?.firstOrNull() ?: menuItemId
                TopItem(
                    menuItemId = menuItemId,
                    name = firstName,
                    quantitySold = items.sumOf { it.quantity },
                    revenueMinorUnit = items.sumOf { it.unitPriceMinorUnit * it.quantity },
                )
            }
            .sortedByDescending { it.revenueMinorUnit }
            .take(topItemsLimit)

        // 昨日同时段对比
        val yesterdayComparison = calculateYesterdayComparison(fromEpoch, toEpoch, closedOrders)

        return ShiftReport(
            fromEpoch = fromEpoch,
            toEpoch = toEpoch,
            grossRevenueMinorUnit = gross,
            totalRefundsMinorUnit = refunds,
            orderCount = closedOrders.size,
            byMethod = byMethod,
            topItems = topItems,
            totalDiscountMinorUnit = closedOrders.sumOf { it.discountMinorUnit },
            totalTipMinorUnit = closedOrders.sumOf { it.tipMinorUnit },
            totalServiceChargeMinorUnit = closedOrders.sumOf { it.serviceChargeMinorUnit },
            totalTaxMinorUnit = closedOrders.sumOf { it.taxTotalMinorUnit },
            totalGuestCount = closedOrders.sumOf { it.guestCount },
            yesterdayComparison = yesterdayComparison,
        )
    }

    /**
     * 计算昨日同时段对比
     * 逻辑：用 24h 前的同日同时段数据，优先读 DailySnapshot，没有则实时算
     */
    private suspend fun calculateYesterdayComparison(
        fromEpoch: Long,
        toEpoch: Long,
        todayClosedOrders: List<com.restaurantpos.core.model.Order>
    ): YesterdayComparison? {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayDate = sdf.format(java.util.Date(fromEpoch))
        val cal = java.util.Calendar.getInstance().apply {
            time = sdf.parse(todayDate) ?: return null
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDate = sdf.format(cal.time)

        // 优先从 DailySnapshot 读取（由 ETL 或交班时预计算）
        val snapshot = reportRepo.getDailySnapshot(yesterdayDate)
        if (snapshot != null) {
            val netRevenue = todayClosedOrders.sumOf { it.totalMinorUnit }
            val orderCount = todayClosedOrders.size
            val guestCount = todayClosedOrders.sumOf { it.guestCount }
            return YesterdayComparison(
                yesterdayNetRevenueMinorUnit = snapshot.netRevenueMinorUnit,
                yesterdayOrderCount = snapshot.orderCount,
                yesterdayGuestCount = snapshot.guestCount,
                netRevenueChangePercent = calcPercentChange(netRevenue, snapshot.netRevenueMinorUnit),
                orderCountChangePercent = calcPercentChange(orderCount.toLong(), snapshot.orderCount.toLong()),
                guestCountChangePercent = calcPercentChange(guestCount.toLong(), snapshot.guestCount.toLong()),
            )
        }

        // 无快照时实时计算昨日同时段（降级方案）
        val yesterdayFrom = fromEpoch - 24 * 60 * 60 * 1000L
        val yesterdayTo = toEpoch - 24 * 60 * 60 * 1000L
        val yesterdayOrders = orderRepo.getClosedInRange(yesterdayFrom, yesterdayTo)
            .filter { it.status == com.restaurantpos.core.model.OrderStatus.CLOSED }
        val yesterdayNet = yesterdayOrders.sumOf { it.totalMinorUnit }
        val yesterdayCount = yesterdayOrders.size
        val yesterdayGuests = yesterdayOrders.sumOf { it.guestCount }

        val todayNet = todayClosedOrders.sumOf { it.totalMinorUnit }
        val todayCount = todayClosedOrders.size
        val todayGuests = todayClosedOrders.sumOf { it.guestCount }

        return YesterdayComparison(
            yesterdayNetRevenueMinorUnit = yesterdayNet,
            yesterdayOrderCount = yesterdayCount,
            yesterdayGuestCount = yesterdayGuests,
            netRevenueChangePercent = calcPercentChange(todayNet, yesterdayNet),
            orderCountChangePercent = calcPercentChange(todayCount.toLong(), yesterdayCount.toLong()),
            guestCountChangePercent = calcPercentChange(todayGuests.toLong(), yesterdayGuests.toLong()),
        )
    }

    private fun calcPercentChange(current: Long, previous: Long): Double {
        if (previous == 0L) return if (current == 0L) 0.0 else 100.0
        return ((current - previous).toDouble() / previous.toDouble()) * 100.0
    }
}
