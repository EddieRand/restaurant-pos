package com.restaurantpos.server.ai

import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.model.AiWorkspaceEvidenceDto
import com.restaurantpos.server.model.AiWorkspaceEvidenceUnits
import com.restaurantpos.server.model.AiWorkspacePeriodDto
import com.restaurantpos.server.model.AiWorkspaceQueryResultDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset

class AiWorkspaceReportQueryService(
    private val modelClient: AiWorkspaceModelClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun query(
        question: String,
        queryType: String?,
        fromMs: Long,
        toMs: Long,
    ): AiWorkspaceQueryResultDto {
        require(toMs > fromMs) { "toMs must be greater than fromMs" }
        require(toMs - fromMs <= MAX_RANGE_MS) { "Date range must not exceed 90 days" }
        val type = runCatching { QueryType.valueOf(queryType ?: "SUMMARY") }
            .getOrElse { throw AiWorkspaceException("AI_INVALID_REQUEST", "Unsupported report query type") }
        val evidence = when (type) {
            QueryType.SUMMARY -> summary(fromMs, toMs)
            QueryType.TOP_ITEMS -> topItems(fromMs, toMs)
            QueryType.PEAK_HOURS -> peakHours(fromMs, toMs)
            QueryType.PAYMENT_METHODS -> paymentMethods(fromMs, toMs)
            QueryType.TREND -> trend(fromMs, toMs)
        }
        val period = AiWorkspacePeriodDto(fromMs, toMs)
        val answer = modelClient.explainQuery(question, period, evidence)
        return AiWorkspaceQueryResultDto(answer, period, evidence, "report.query")
    }

    private fun closedOrders(fromMs: Long, toMs: Long) = OrdersTable.selectAll().where {
        (OrdersTable.status eq "CLOSED") and
            (OrdersTable.createdAt greaterEq fromMs) and
            (OrdersTable.createdAt lessEq toMs)
    }.toList()

    private fun summary(fromMs: Long, toMs: Long): List<AiWorkspaceEvidenceDto> = transaction {
        val orders = closedOrders(fromMs, toMs)
        val count = orders.size
        val guests = orders.sumOf { it[OrdersTable.guestCount] }
        val gross = orders.sumOf {
            it[OrdersTable.subtotalMinorUnit] + it[OrdersTable.taxTotalMinorUnit] +
                it[OrdersTable.serviceChargeMinorUnit] + it[OrdersTable.tipMinorUnit]
        }
        val discount = orders.sumOf { it[OrdersTable.discountMinorUnit] }
        val net = gross - discount
        val ids = orders.map { it[OrdersTable.id] }
        val refunds = if (ids.isEmpty()) 0L else PaymentsTable.selectAll()
            .where { (PaymentsTable.orderId inList ids) and (PaymentsTable.status eq "REFUNDED") }
            .sumOf { it[PaymentsTable.amountMinorUnit] }
        listOf(
            evidence("netRevenue", "净营收", net, AiWorkspaceEvidenceUnits.MINOR_UNIT),
            evidence("grossRevenue", "总营收", gross, AiWorkspaceEvidenceUnits.MINOR_UNIT),
            evidence("orderCount", "订单量", count.toLong(), AiWorkspaceEvidenceUnits.COUNT),
            evidence("guestCount", "客流", guests.toLong(), AiWorkspaceEvidenceUnits.COUNT),
            evidence("averageOrderValue", "客单价", if (count == 0) 0 else net / count, AiWorkspaceEvidenceUnits.MINOR_UNIT),
            evidence("discount", "折扣", discount, AiWorkspaceEvidenceUnits.MINOR_UNIT),
            evidence("refund", "退款", refunds, AiWorkspaceEvidenceUnits.MINOR_UNIT),
        )
    }

    private fun topItems(fromMs: Long, toMs: Long): List<AiWorkspaceEvidenceDto> = transaction {
        val ids = closedOrders(fromMs, toMs).map { it[OrdersTable.id] }
        if (ids.isEmpty()) return@transaction emptyList()
        data class ItemTotal(val name: String, var quantity: Long)
        val totals = linkedMapOf<String, ItemTotal>()
        OrderItemsTable.selectAll().where { OrderItemsTable.orderId inList ids }.forEach { row ->
            val id = row[OrderItemsTable.menuItemId]
            val name = localizedName(row[OrderItemsTable.menuItemNameSnapshot], id)
            totals.getOrPut(id) { ItemTotal(name, 0) }.quantity += row[OrderItemsTable.quantity]
        }
        totals.values.sortedByDescending { it.quantity }.take(MAX_TOP_RESULTS).map {
            evidence("topItems", "销量", it.quantity, AiWorkspaceEvidenceUnits.COUNT, it.name)
        }
    }

    private fun peakHours(fromMs: Long, toMs: Long): List<AiWorkspaceEvidenceDto> = transaction {
        closedOrders(fromMs, toMs)
            .groupingBy { Instant.ofEpochMilli(it[OrdersTable.createdAt]).atZone(ZoneOffset.UTC).hour }
            .eachCount()
            .entries.sortedByDescending { it.value }.take(MAX_TOP_RESULTS).map {
                evidence("peakHours", "订单量", it.value.toLong(), AiWorkspaceEvidenceUnits.COUNT, "%02d:00".format(it.key))
            }
    }

    private fun paymentMethods(fromMs: Long, toMs: Long): List<AiWorkspaceEvidenceDto> = transaction {
        val ids = closedOrders(fromMs, toMs).map { it[OrdersTable.id] }
        if (ids.isEmpty()) return@transaction emptyList()
        val totals = linkedMapOf<String, Long>()
        PaymentsTable.selectAll().where {
            (PaymentsTable.orderId inList ids) and
                ((PaymentsTable.status eq "PAID") or (PaymentsTable.status eq "COMPLETED"))
        }.forEach { row ->
            val method = row[PaymentsTable.method]
            totals[method] = (totals[method] ?: 0L) + row[PaymentsTable.amountMinorUnit]
        }
        totals.entries.sortedByDescending { it.value }.take(MAX_TOP_RESULTS).map {
            evidence("paymentMethods", "实收", it.value, AiWorkspaceEvidenceUnits.MINOR_UNIT, it.key)
        }
    }

    private fun trend(fromMs: Long, toMs: Long): List<AiWorkspaceEvidenceDto> = transaction {
        closedOrders(fromMs, toMs).groupBy {
            Instant.ofEpochMilli(it[OrdersTable.createdAt]).atZone(ZoneOffset.UTC).toLocalDate().toString()
        }.entries.sortedBy { it.key }.takeLast(MAX_TOP_RESULTS).map { (date, orders) ->
            val net = orders.sumOf {
                it[OrdersTable.subtotalMinorUnit] + it[OrdersTable.taxTotalMinorUnit] +
                    it[OrdersTable.serviceChargeMinorUnit] + it[OrdersTable.tipMinorUnit] - it[OrdersTable.discountMinorUnit]
            }
            evidence("netRevenue", "净营收", net, AiWorkspaceEvidenceUnits.MINOR_UNIT, date)
        }
    }

    private fun evidence(key: String, label: String, value: Long, unit: String, dimension: String? = null) =
        AiWorkspaceEvidenceDto(key, label, value, unit, dimension)

    private fun localizedName(raw: String, fallback: String): String = runCatching {
        val names = json.parseToJsonElement(raw).jsonObject
        names["zh-CN"]?.jsonPrimitive?.content
            ?: names["zh"]?.jsonPrimitive?.content
            ?: names["en"]?.jsonPrimitive?.content
            ?: names.values.first().jsonPrimitive.content
    }.getOrDefault(fallback)

    private enum class QueryType { SUMMARY, TOP_ITEMS, PEAK_HOURS, PAYMENT_METHODS, TREND }

    companion object {
        private const val MAX_TOP_RESULTS = 10
        private const val MAX_RANGE_MS = 90L * 24 * 60 * 60 * 1000
    }
}
