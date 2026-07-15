package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.SettingsTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
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
        val previousNetRevenue = previousOrders.sumOf(::net)
        val currency = loadCurrencyConfig()
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
            previousPeriodNetRevenueMinorUnit = previousNetRevenue,
            currencyCode = currency.first,
            minorUnitDigits = currency.second,
            orderCountChangeBasisPoints = changeBasisPoints(currentOrders.size.toLong(), previousOrders.size.toLong()),
            netRevenueChangeBasisPoints = changeBasisPoints(netRevenue, previousNetRevenue),
        )
    }

    private fun loadCurrencyConfig(): Pair<String, Int> {
        val raw = SettingsTable.selectAll()
            .where { (SettingsTable.key eq "regionConfig") or (SettingsTable.key eq "region-config") }
            .firstOrNull()?.get(SettingsTable.value)
            ?: return "CNY" to 2
        return runCatching {
            val value = Json.parseToJsonElement(raw).jsonObject
            val code = value["currencyCode"]?.jsonPrimitive?.contentOrNull?.uppercase()
                ?.takeIf { it.matches(Regex("^[A-Z]{3}$")) } ?: "CNY"
            val digits = value["currencyMinorDigits"]?.jsonPrimitive?.intOrNull
                ?: value["minorDigits"]?.jsonPrimitive?.intOrNull ?: 2
            code to digits.coerceIn(0, 4)
        }.getOrDefault("CNY" to 2)
    }

    private fun changeBasisPoints(current: Long, previous: Long): Long? =
        previous.takeIf { it != 0L }?.let { ((current - previous) * 10_000L) / it }
}
