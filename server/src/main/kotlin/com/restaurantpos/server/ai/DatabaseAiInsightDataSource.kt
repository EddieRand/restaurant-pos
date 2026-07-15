package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.ZoneId

class DatabaseAiInsightDataSource : AiInsightDataSource {
    override fun load(fromMs: Long, toMs: Long, locale: String): AiInsightMetrics = transaction {
        val periodLength = toMs - fromMs
        val currentOrders = OrdersTable.selectAll().where {
            (OrdersTable.status eq "CLOSED") and
                (OrdersTable.createdAt greaterEq fromMs) and
                (OrdersTable.createdAt less toMs)
        }.toList()
        val previousOrders = OrdersTable.selectAll().where {
            (OrdersTable.status eq "CLOSED") and
                (OrdersTable.createdAt greaterEq (fromMs - periodLength)) and
                (OrdersTable.createdAt less fromMs)
        }.toList()

        fun gross(row: org.jetbrains.exposed.sql.ResultRow): Long =
            row[OrdersTable.subtotalMinorUnit] + row[OrdersTable.taxTotalMinorUnit] +
                row[OrdersTable.serviceChargeMinorUnit] + row[OrdersTable.tipMinorUnit]

        fun net(row: org.jetbrains.exposed.sql.ResultRow): Long = gross(row) - row[OrdersTable.discountMinorUnit]

        val orderIds = currentOrders.map { it[OrdersTable.id] }
        val payments = if (orderIds.isEmpty()) emptyList() else PaymentsTable.selectAll()
            .where { PaymentsTable.orderId inList orderIds }
            .toList()
        val items = if (orderIds.isEmpty()) emptyList() else OrderItemsTable.selectAll()
            .where { OrderItemsTable.orderId inList orderIds }
            .toList()

        val paymentBreakdown = payments.asSequence()
            .filter { it[PaymentsTable.status] in setOf("PAID", "COMPLETED") }
            .groupBy { it[PaymentsTable.method] }
            .mapValues { (_, rows) -> rows.sumOf { it[PaymentsTable.amountMinorUnit] } }
            .toSortedMap()
        val refunds = payments.asSequence()
            .filter { it[PaymentsTable.status] == "REFUNDED" }
            .sumOf { it[PaymentsTable.amountMinorUnit] }

        val localeLanguage = locale.substringBefore('-')
        val topItems = items.groupBy { it[OrderItemsTable.menuItemId] }
            .map { (menuItemId, rows) ->
                val names = runCatching {
                    Json.parseToJsonElement(rows.first()[OrderItemsTable.menuItemNameSnapshot]).jsonObject
                }.getOrDefault(emptyMap())
                val name = names[locale]?.jsonPrimitive?.content
                    ?: names[localeLanguage]?.jsonPrimitive?.content
                    ?: names["en"]?.jsonPrimitive?.content
                    ?: names.values.firstOrNull()?.jsonPrimitive?.content
                    ?: menuItemId
                AiTopItemMetric(
                    menuItemId = menuItemId,
                    name = name,
                    quantity = rows.sumOf { it[OrderItemsTable.quantity] },
                    revenueMinorUnit = rows.sumOf {
                        it[OrderItemsTable.quantity].toLong() * it[OrderItemsTable.unitPriceMinorUnit]
                    },
                )
            }
            .sortedWith(compareByDescending<AiTopItemMetric> { it.quantity }.thenByDescending { it.revenueMinorUnit })
            .take(10)

        val peakHours = currentOrders.groupingBy {
            Instant.ofEpochMilli(it[OrdersTable.createdAt]).atZone(ZoneId.systemDefault()).hour
        }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { AiPeakHourMetric(hour = it.key, orderCount = it.value) }

        val netRevenue = currentOrders.sumOf(::net)
        AiInsightMetrics(
            fromMs = fromMs,
            toMs = toMs,
            orderCount = currentOrders.size,
            grossRevenueMinorUnit = currentOrders.sumOf(::gross),
            netRevenueMinorUnit = netRevenue,
            averageOrderValueMinorUnit = if (currentOrders.isEmpty()) 0 else netRevenue / currentOrders.size,
            guestCount = currentOrders.sumOf { it[OrdersTable.guestCount] },
            totalDiscountMinorUnit = currentOrders.sumOf { it[OrdersTable.discountMinorUnit] },
            totalRefundMinorUnit = refunds,
            paymentMethodBreakdown = paymentBreakdown,
            topItems = topItems,
            peakHours = peakHours,
            previousPeriodOrderCount = previousOrders.size,
            previousPeriodNetRevenueMinorUnit = previousOrders.sumOf(::net),
        )
    }
}
