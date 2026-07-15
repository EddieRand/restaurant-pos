package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.AdminUsersTable
import com.restaurantpos.server.db.tables.DailySnapshotsTable
import com.restaurantpos.server.db.tables.MenuCategoriesTable
import com.restaurantpos.server.db.tables.OrderItemModifiersTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.db.tables.UsersTable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE!!

fun Route.adminReportRoutes() {
    authenticate("jwt") {
        route("/admin/reports") {

            // ── Existing: single-range shift report ──────────────────

            get("/shift") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val report = transaction {
                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                    (OrdersTable.createdAt greaterEq from) and
                                    (OrdersTable.createdAt lessEq to)
                        }
                        .toList()

                    val orderCount     = closedOrders.size
                    val totalDiscount  = closedOrders.sumOf { it[OrdersTable.discountMinorUnit] }
                    val totalTip       = closedOrders.sumOf { it[OrdersTable.tipMinorUnit] }
                    val totalSvc       = closedOrders.sumOf { it[OrdersTable.serviceChargeMinorUnit] }
                    val totalTax       = closedOrders.sumOf { it[OrdersTable.taxTotalMinorUnit] }
                    val totalGuests    = closedOrders.sumOf { it[OrdersTable.guestCount] }

                    val grossRevenue = closedOrders.sumOf {
                        it[OrdersTable.subtotalMinorUnit] +
                                it[OrdersTable.taxTotalMinorUnit] +
                                it[OrdersTable.serviceChargeMinorUnit] +
                                it[OrdersTable.tipMinorUnit]
                    }
                    val netRevenue = grossRevenue - totalDiscount

                    val orderIds = closedOrders.map { it[OrdersTable.id] }
                    val breakdown = mutableMapOf<String, Long>()
                    var totalRefunds = 0L
                    if (orderIds.isNotEmpty()) {
                        PaymentsTable.selectAll()
                            .where { PaymentsTable.orderId inList orderIds }
                            .forEach { row ->
                                when (row[PaymentsTable.status]) {
                                    "PAID", "COMPLETED" -> {
                                        val m = row[PaymentsTable.method]
                                        breakdown[m] = (breakdown[m] ?: 0L) + row[PaymentsTable.amountMinorUnit]
                                    }
                                    "REFUNDED" -> totalRefunds += row[PaymentsTable.amountMinorUnit]
                                }
                            }
                    }

                    ShiftReportDto(
                        fromMs = from,
                        toMs   = to,
                        orderCount = orderCount,
                        grossRevenueMinorUnit = grossRevenue,
                        netRevenueMinorUnit   = netRevenue,
                        totalDiscountMinorUnit      = totalDiscount,
                        totalTipMinorUnit          = totalTip,
                        totalServiceChargeMinorUnit = totalSvc,
                        totalTaxMinorUnit         = totalTax,
                        totalGuestCount = totalGuests,
                        totalRefundsMinorUnit = totalRefunds,
                        averageOrderValueMinorUnit =
                            if (orderCount > 0) netRevenue / orderCount else 0L,
                        averageSpendPerGuestMinorUnit =
                            if (totalGuests > 0) netRevenue / totalGuests else 0L,
                        paymentMethodBreakdown = breakdown,
                    )
                }
                call.respond(report)
            }

            // ── Trend report (reads pre-computed snapshots) ─────────

            get("/trend") {
                val params     = call.request.queryParameters
                val fromDateStr = params["fromDate"].takeUnless { it.isNullOrBlank() }
                    ?: return@get call.respondText(
                        "Missing required parameter: fromDate (YYYY-MM-DD)",
                        status = HttpStatusCode.BadRequest,
                    )
                val toDateStr = params["toDate"].takeUnless { it.isNullOrBlank() }
                    ?: return@get call.respondText(
                        "Missing required parameter: toDate (YYYY-MM-DD)",
                        status = HttpStatusCode.BadRequest,
                    )
                val granularity = params["granularity"]?.lowercase() ?: "day"

                val points = transaction {
                    // Try fast path: read from daily_snapshots
                    val snapshots = DailySnapshotsTable.selectAll()
                        .where {
                            (DailySnapshotsTable.date greaterEq fromDateStr) and
                                    (DailySnapshotsTable.date lessEq toDateStr)
                        }
                        .orderBy(DailySnapshotsTable.date to SortOrder.ASC)
                        .toList()

                    if (snapshots.size >= dateDaysBetween(fromDateStr, toDateStr) * 0.8) {
                        // Enough snapshots → fast path
                        snapshots.map { row ->
                            TrendDataPointDto(
                                date  = row[DailySnapshotsTable.date],
                                grossRevenueMinorUnit = row[DailySnapshotsTable.grossRevenueMinorUnit],
                                netRevenueMinorUnit   = row[DailySnapshotsTable.netRevenueMinorUnit],
                                orderCount = row[DailySnapshotsTable.orderCount],
                                guestCount = row[DailySnapshotsTable.guestCount],
                                averageOrderValueMinorUnit =
                                    if (row[DailySnapshotsTable.orderCount] > 0)
                                        row[DailySnapshotsTable.netRevenueMinorUnit] / row[DailySnapshotsTable.orderCount]
                                    else 0L,
                                averageSpendPerGuestMinorUnit =
                                    if (row[DailySnapshotsTable.guestCount] > 0)
                                        row[DailySnapshotsTable.netRevenueMinorUnit] / row[DailySnapshotsTable.guestCount]
                                    else 0L,
                                refundMinorUnit   = row[DailySnapshotsTable.totalRefundsMinorUnit],
                                discountMinorUnit = row[DailySnapshotsTable.totalDiscountMinorUnit],
                                taxMinorUnit      = row[DailySnapshotsTable.totalTaxMinorUnit],
                            )
                        }
                    } else {
                        // Slow path: compute on the fly (same logic as snapshot generation)
                        buildTrendFromLiveData(fromDateStr, toDateStr)
                    }
                }

                val (grouped, summary) = aggregateTrend(points, granularity)

                call.respond(TrendReportDto(dataPoints = grouped, summary = summary))
            }

            // ── Get single snapshot ──────────────────────────────────

            get("/snapshot/{date}") {
                val dateStr = call.parameters["date"]!!
                val snapshot = transaction {
                    DailySnapshotsTable.selectAll()
                        .where { DailySnapshotsTable.date eq dateStr }
                        .singleOrNull()
                        ?.let { row ->
                            DailySnapshotDto(
                                date  = row[DailySnapshotsTable.date],
                                grossRevenueMinorUnit = row[DailySnapshotsTable.grossRevenueMinorUnit],
                                netRevenueMinorUnit   = row[DailySnapshotsTable.netRevenueMinorUnit],
                                orderCount = row[DailySnapshotsTable.orderCount],
                                guestCount = row[DailySnapshotsTable.guestCount],
                                totalDiscountMinorUnit       = row[DailySnapshotsTable.totalDiscountMinorUnit],
                                totalTipMinorUnit          = row[DailySnapshotsTable.totalTipMinorUnit],
                                totalServiceChargeMinorUnit = row[DailySnapshotsTable.totalServiceChargeMinorUnit],
                                totalTaxMinorUnit         = row[DailySnapshotsTable.totalTaxMinorUnit],
                                paymentMethodBreakdown =
                                    Json.decodeFromString<Map<String, Long>>(row[DailySnapshotsTable.paymentMethodBreakdown]),
                                computedAt = row[DailySnapshotsTable.computedAt],
                            )
                        }
                }
                if (snapshot == null) {
                    call.respondText("Snapshot not found for $dateStr", status = HttpStatusCode.NotFound)
                } else {
                    call.respond(snapshot)
                }
            }

            // ── Trigger ETL: regenerate snapshots for date range ─────

            post("/snapshot/regenerate") {
                val req = call.receive<SnapshotRegenerateRequest>()
                val result = transaction {
                    regenerateSnapshots(req.fromDate, req.toDate)
                }
                call.respond(mapOf("regenerated" to result, "from" to req.fromDate, "to" to req.toDate))
            }

            // ── Peak hours heatmap (7×24 grid) ─────────────────────

            get("/peak") {
                val params = call.request.queryParameters
                val fromDateStr = params["fromDate"].takeUnless { it.isNullOrBlank() }
                    ?: return@get call.respondText(
                        "Missing required parameter: fromDate (YYYY-MM-DD)",
                        status = HttpStatusCode.BadRequest,
                    )
                val toDateStr = params["toDate"].takeUnless { it.isNullOrBlank() }
                    ?: return@get call.respondText(
                        "Missing required parameter: toDate (YYYY-MM-DD)",
                        status = HttpStatusCode.BadRequest,
                    )

                val report = transaction {
                    val fromEpoch = LocalDate.parse(fromDateStr)!!.atStartOfDay()
                        .toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                    val toEpoch = LocalDate.parse(toDateStr)!!.plusDays(1).atStartOfDay()
                        .toEpochSecond(java.time.ZoneOffset.UTC) * 1000 - 1

                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq fromEpoch) and
                                (OrdersTable.createdAt lessEq toEpoch)
                        }
                        .toList()

                    // Build 7×24 grid: group by ISO dayOfWeek (1=Mon..7=Sun) + hour
                    val grid = mutableMapOf<Pair<Int, Int>, Int>()
                    for (day in 1..7) {
                        for (hour in 0..23) {
                            grid[day to hour] = 0
                        }
                    }

                    closedOrders.forEach { row ->
                        val instant = java.time.Instant.ofEpochMilli(row[OrdersTable.createdAt])
                        val zdt = instant.atZone(java.time.ZoneId.of("UTC"))
                        val dow = zdt.dayOfWeek.value          // 1=Mon … 7=Sun
                        val hour = zdt.hour                    // 0-23
                        val key = dow to hour
                        grid[key] = (grid[key] ?: 0) + 1
                    }

                    val cells = grid.map { (key, count) ->
                        PeakHourCellDto(dayOfWeek = key.first, hour = key.second, orderCount = count)
                    }.sortedWith(compareBy({ it.dayOfWeek }, { it.hour }))

                    val maxOrders = cells.maxOfOrNull { it.orderCount } ?: 0
                    val totalOrders = closedOrders.size

                    PeakReportDto(cells = cells, maxOrderCount = maxOrders, totalOrders = totalOrders)
                }
                call.respond(report)
            }

            // ── Top-selling items within a time range ────────────────

            get("/top-items") {
                val params = call.request.queryParameters
                val from  = params["from"]?.toLongOrNull() ?: 0L
                val to    = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()
                val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 5

                val topItems = transaction {
                    val orderIds = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                    (OrdersTable.createdAt greaterEq from) and
                                    (OrdersTable.createdAt lessEq to)
                        }
                        .map { it[OrdersTable.id] }

                    if (orderIds.isEmpty()) {
                        emptyList()
                    } else {
                        data class Agg(var names: String, var quantity: Int, var revenue: Long)
                        val byItem = LinkedHashMap<String, Agg>()
                        OrderItemsTable.selectAll()
                            .where { OrderItemsTable.orderId inList orderIds }
                            .forEach { row ->
                                val id = row[OrderItemsTable.menuItemId]
                                val qty = row[OrderItemsTable.quantity]
                                val revenue = row[OrderItemsTable.unitPriceMinorUnit] * qty
                                val agg = byItem.getOrPut(id) {
                                    Agg(row[OrderItemsTable.menuItemNameSnapshot], 0, 0L)
                                }
                                agg.quantity += qty
                                agg.revenue += revenue
                            }
                        byItem.entries
                            .sortedByDescending { it.value.quantity }
                            .take(limit)
                            .map { (id, agg) ->
                                com.restaurantpos.server.model.TopItemDto(
                                    menuItemId = id,
                                    names = agg.names,
                                    quantity = agg.quantity,
                                    revenueMinorUnit = agg.revenue,
                                )
                            }
                    }
                }
                call.respond(topItems)
            }

            // ── Category sales breakdown ──────────────────────────────

            get("/category") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    val orderIds = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .map { it[OrdersTable.id] }

                    if (orderIds.isEmpty()) return@transaction emptyList<CategorySalesDto>()

                    data class CatAgg(var name: String, var qty: Long, var revenue: Long, val orders: MutableSet<String>)
                    val byCategory = mutableMapOf<String, CatAgg>()

                    val catNames = MenuCategoriesTable.selectAll()
                        .associate { it[MenuCategoriesTable.id] to it[MenuCategoriesTable.name] }

                    OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId inList orderIds }
                        .forEach { row ->
                            val catId = row[OrderItemsTable.categoryId] ?: "unknown"
                            val agg = byCategory.getOrPut(catId) {
                                CatAgg(catNames[catId] ?: catId, 0L, 0L, mutableSetOf())
                            }
                            agg.qty += row[OrderItemsTable.quantity]
                            agg.revenue += row[OrderItemsTable.unitPriceMinorUnit] * row[OrderItemsTable.quantity]
                            agg.orders.add(row[OrderItemsTable.orderId])
                        }

                    val totalRevenue = byCategory.values.sumOf { it.revenue }
                    byCategory.entries
                        .sortedByDescending { it.value.revenue }
                        .map { (catId, agg) ->
                            CategorySalesDto(
                                categoryId = catId,
                                categoryName = agg.name,
                                quantity = agg.qty,
                                revenueMinorUnit = agg.revenue,
                                orderCount = agg.orders.size,
                                sharePermille = if (totalRevenue > 0) ((agg.revenue * 1000) / totalRevenue).toInt() else 0,
                            )
                        }
                }
                call.respond(result)
            }

            // ── Full item sales breakdown ─────────────────────────────

            get("/items") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()
                val filterCategoryId = params["categoryId"]

                val result = transaction {
                    val orderIds = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .map { it[OrdersTable.id] }

                    if (orderIds.isEmpty()) return@transaction emptyList<ItemSalesDto>()

                    val catNames = MenuCategoriesTable.selectAll()
                        .associate { it[MenuCategoriesTable.id] to it[MenuCategoriesTable.name] }

                    data class ItemAgg(
                        var names: String, var catId: String,
                        var qty: Long, var revenue: Long, val orders: MutableSet<String>
                    )
                    val byItem = mutableMapOf<String, ItemAgg>()

                    var query = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId inList orderIds }
                    if (filterCategoryId != null) {
                        query = OrderItemsTable.selectAll()
                            .where {
                                (OrderItemsTable.orderId inList orderIds) and
                                    (OrderItemsTable.categoryId eq filterCategoryId)
                            }
                    }

                    query.forEach { row ->
                        val itemId = row[OrderItemsTable.menuItemId]
                        val catId = row[OrderItemsTable.categoryId] ?: "unknown"
                        val agg = byItem.getOrPut(itemId) {
                            ItemAgg(row[OrderItemsTable.menuItemNameSnapshot], catId, 0L, 0L, mutableSetOf())
                        }
                        agg.qty += row[OrderItemsTable.quantity]
                        agg.revenue += row[OrderItemsTable.unitPriceMinorUnit] * row[OrderItemsTable.quantity]
                        agg.orders.add(row[OrderItemsTable.orderId])
                    }

                    byItem.entries
                        .sortedByDescending { it.value.revenue }
                        .map { (itemId, agg) ->
                            ItemSalesDto(
                                menuItemId = itemId,
                                names = agg.names,
                                categoryId = agg.catId,
                                categoryName = catNames[agg.catId] ?: agg.catId,
                                quantity = agg.qty,
                                orderCount = agg.orders.size,
                                revenueMinorUnit = agg.revenue,
                            )
                        }
                }
                call.respond(result)
            }

            // ── Order type (channel) distribution ────────────────────

            get("/order-type") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .toList()

                    data class TypeAgg(var orderCount: Int, var revenue: Long, var guests: Int)
                    val byType = mutableMapOf<String, TypeAgg>()
                    closedOrders.forEach { row ->
                        val type = row[OrdersTable.type]
                        val agg = byType.getOrPut(type) { TypeAgg(0, 0L, 0) }
                        agg.orderCount++
                        agg.revenue += row[OrdersTable.subtotalMinorUnit] + row[OrdersTable.taxTotalMinorUnit] +
                            row[OrdersTable.serviceChargeMinorUnit] + row[OrdersTable.tipMinorUnit] -
                            row[OrdersTable.discountMinorUnit]
                        agg.guests += row[OrdersTable.guestCount]
                    }

                    val totalRevenue = byType.values.sumOf { it.revenue }
                    byType.entries
                        .sortedByDescending { it.value.revenue }
                        .map { (type, agg) ->
                            OrderTypeSalesDto(
                                type = type,
                                orderCount = agg.orderCount,
                                revenueMinorUnit = agg.revenue,
                                guestCount = agg.guests,
                                sharePermille = if (totalRevenue > 0) ((agg.revenue * 1000) / totalRevenue).toInt() else 0,
                            )
                        }
                }
                call.respond(result)
            }

            // ── Staff / operator performance ──────────────────────────

            get("/staff") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .toList()

                    // Build name lookup: posUser id → displayName, adminUser id → email prefix
                    val posNames = UsersTable.selectAll()
                        .associate { it[UsersTable.id] to it[UsersTable.displayName] }
                    val adminNames = AdminUsersTable.selectAll()
                        .associate { it[AdminUsersTable.id] to it[AdminUsersTable.email].substringBefore('@') }

                    fun resolveName(id: String) = posNames[id] ?: adminNames[id] ?: id

                    val allOrderIds = closedOrders.map { it[OrdersTable.id] }
                    val refundsByOperator = if (allOrderIds.isNotEmpty()) {
                        PaymentsTable.selectAll()
                            .where { PaymentsTable.orderId inList allOrderIds and (PaymentsTable.status eq "REFUNDED") }
                            .groupBy { it[PaymentsTable.operatorId] }
                            .mapValues { (_, rows) -> rows.sumOf { it[PaymentsTable.amountMinorUnit] } }
                    } else emptyMap()

                    data class StaffAgg(
                        var orderCount: Int, var revenue: Long,
                        var tip: Long, var discount: Long
                    )
                    val byOperator = mutableMapOf<String, StaffAgg>()
                    closedOrders.forEach { row ->
                        val opId = row[OrdersTable.operatorId].ifBlank { "unknown" }
                        val agg = byOperator.getOrPut(opId) { StaffAgg(0, 0L, 0L, 0L) }
                        agg.orderCount++
                        agg.revenue += row[OrdersTable.subtotalMinorUnit] + row[OrdersTable.taxTotalMinorUnit] +
                            row[OrdersTable.serviceChargeMinorUnit] + row[OrdersTable.tipMinorUnit] -
                            row[OrdersTable.discountMinorUnit]
                        agg.tip += row[OrdersTable.tipMinorUnit]
                        agg.discount += row[OrdersTable.discountMinorUnit]
                    }

                    byOperator.entries
                        .sortedByDescending { it.value.revenue }
                        .map { (opId, agg) ->
                            StaffReportDto(
                                operatorId = opId,
                                operatorName = resolveName(opId),
                                orderCount = agg.orderCount,
                                revenueMinorUnit = agg.revenue,
                                avgOrderValueMinorUnit = if (agg.orderCount > 0) agg.revenue / agg.orderCount else 0L,
                                tipMinorUnit = agg.tip,
                                discountMinorUnit = agg.discount,
                                refundMinorUnit = refundsByOperator[opId] ?: 0L,
                            )
                        }
                }
                call.respond(result)
            }

            // ── Hourly revenue distribution ───────────────────────────

            get("/hourly") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .toList()

                    data class HourAgg(var orderCount: Int, var revenue: Long)
                    val byHour = Array(24) { HourAgg(0, 0L) }

                    closedOrders.forEach { row ->
                        val hour = java.time.Instant.ofEpochMilli(row[OrdersTable.createdAt])
                            .atZone(java.time.ZoneId.of("UTC")).hour
                        byHour[hour].orderCount++
                        byHour[hour].revenue += row[OrdersTable.subtotalMinorUnit] + row[OrdersTable.taxTotalMinorUnit] +
                            row[OrdersTable.serviceChargeMinorUnit] + row[OrdersTable.tipMinorUnit] -
                            row[OrdersTable.discountMinorUnit]
                    }

                    byHour.mapIndexed { hour, agg ->
                        HourlySalesDto(
                            hour = hour,
                            orderCount = agg.orderCount,
                            revenueMinorUnit = agg.revenue,
                            avgOrderValueMinorUnit = if (agg.orderCount > 0) agg.revenue / agg.orderCount else 0L,
                        )
                    }
                }
                call.respond(result)
            }
            // ── Tax breakdown by rate ─────────────────────────────────

            get("/tax") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    // Load tax rates from regionConfig
                    val regionConfigJson = SettingsTable.selectAll()
                        .where { SettingsTable.key eq "regionConfig" }
                        .firstOrNull()?.get(SettingsTable.value) ?: "{}"
                    val taxRates = try {
                        val obj = Json.parseToJsonElement(regionConfigJson).jsonObject
                        val arr = obj["availableTaxRates"]?.jsonArray ?: obj["taxRates"]?.jsonArray
                        arr?.mapNotNull { el ->
                            val o = el.jsonObject
                            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val name = o["name"]?.jsonPrimitive?.content ?: id
                            val ratePermille = o["ratePermille"]?.jsonPrimitive?.intOrNull ?: 0
                            Triple(id, name, ratePermille)
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    val rateById = taxRates.associate { (id, name, rate) -> id to Pair(name, rate) }

                    val closedOrders = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .toList()

                    val orderIds = closedOrders.map { it[OrdersTable.id] }

                    data class TaxAgg(var taxableSales: Long, var taxAmount: Long)
                    val byRate = mutableMapOf<String, TaxAgg>()
                    var nonTaxable = 0L
                    var totalSales = 0L

                    if (orderIds.isNotEmpty()) {
                        OrderItemsTable.selectAll()
                            .where { OrderItemsTable.orderId inList orderIds }
                            .forEach { row ->
                                val qty = row[OrderItemsTable.quantity]
                                val price = row[OrderItemsTable.unitPriceMinorUnit] * qty
                                totalSales += price
                                val rateId = row[OrderItemsTable.taxRateId]
                                if (rateId != null && rateById.containsKey(rateId)) {
                                    val (_, ratePermille) = rateById[rateId]!!
                                    val taxAmount = (price * ratePermille) / 1000
                                    val agg = byRate.getOrPut(rateId) { TaxAgg(0L, 0L) }
                                    agg.taxableSales += price
                                    agg.taxAmount += taxAmount
                                } else {
                                    nonTaxable += price
                                }
                            }
                    }

                    val lines = byRate.entries
                        .sortedByDescending { it.value.taxableSales }
                        .map { (rateId, agg) ->
                            val (name, rate) = rateById[rateId] ?: Pair(rateId, 0)
                            TaxReportLineDto(
                                taxRateId = rateId,
                                taxRateName = name,
                                ratePermille = rate,
                                taxableSalesMinorUnit = agg.taxableSales,
                                taxAmountMinorUnit = agg.taxAmount,
                            )
                        }

                    TaxReportSummaryDto(
                        taxableSalesMinorUnit = totalSales - nonTaxable,
                        nonTaxableSalesMinorUnit = nonTaxable,
                        totalNetSalesMinorUnit = totalSales,
                        lines = lines,
                    )
                }
                call.respond(result)
            }

            // ── Modifier option sales ─────────────────────────────────

            get("/modifiers") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    val orderIds = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.status eq "CLOSED") and
                                (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .map { it[OrdersTable.id] }

                    if (orderIds.isEmpty()) return@transaction emptyList<ModifierSalesDto>()

                    val itemIds = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId inList orderIds }
                        .map { it[OrderItemsTable.id] }

                    if (itemIds.isEmpty()) return@transaction emptyList<ModifierSalesDto>()

                    data class ModAgg(
                        var groupId: String, var groupName: String,
                        var optionName: String, var qty: Long, var revenue: Long
                    )
                    val byOption = mutableMapOf<String, ModAgg>()

                    OrderItemModifiersTable.selectAll()
                        .where { OrderItemModifiersTable.orderItemId inList itemIds }
                        .forEach { row ->
                            val optId = row[OrderItemModifiersTable.optionId]
                            val agg = byOption.getOrPut(optId) {
                                ModAgg(
                                    groupId = row[OrderItemModifiersTable.modifierGroupId],
                                    groupName = row[OrderItemModifiersTable.modifierGroupNameSnapshot],
                                    optionName = row[OrderItemModifiersTable.optionNameSnapshot],
                                    qty = 0L, revenue = 0L,
                                )
                            }
                            agg.qty += 1
                            agg.revenue += row[OrderItemModifiersTable.priceAdjustMinorUnit]
                                .coerceAtLeast(0L)
                        }

                    byOption.entries
                        .sortedByDescending { it.value.qty }
                        .map { (optId, agg) ->
                            ModifierSalesDto(
                                optionId = optId,
                                optionName = agg.optionName,
                                groupId = agg.groupId,
                                groupName = agg.groupName,
                                quantitySold = agg.qty,
                                revenueMinorUnit = agg.revenue,
                            )
                        }
                }
                call.respond(result)
            }

            // ── Payment method breakdown ──────────────────────────────

            get("/payment-methods") {
                val params = call.request.queryParameters
                val from = params["from"]?.toLongOrNull() ?: 0L
                val to   = params["to"]?.toLongOrNull()   ?: System.currentTimeMillis()

                val result = transaction {
                    data class MethodAgg(
                        var payCount: Int = 0, var payAmount: Long = 0L,
                        var refCount: Int = 0, var refAmount: Long = 0L
                    )
                    val byMethod = mutableMapOf<String, MethodAgg>()

                    // Get all payments linked to orders in the time window
                    val orderIds = OrdersTable.selectAll()
                        .where {
                            (OrdersTable.createdAt greaterEq from) and
                                (OrdersTable.createdAt lessEq to)
                        }
                        .map { it[OrdersTable.id] }

                    if (orderIds.isNotEmpty()) {
                        PaymentsTable.selectAll()
                            .where { PaymentsTable.orderId inList orderIds }
                            .forEach { row ->
                                val method = row[PaymentsTable.method]
                                val status = row[PaymentsTable.status]
                                val amount = row[PaymentsTable.amountMinorUnit]
                                val agg = byMethod.getOrPut(method) { MethodAgg() }
                                when (status) {
                                    "PAID", "COMPLETED" -> { agg.payCount++; agg.payAmount += amount }
                                    "REFUNDED"  -> { agg.refCount++; agg.refAmount += amount }
                                }
                            }
                    }

                    byMethod.entries
                        .sortedByDescending { it.value.payAmount }
                        .map { (method, agg) ->
                            PaymentMethodReportDto(
                                method = method,
                                paymentCount = agg.payCount,
                                paymentAmountMinorUnit = agg.payAmount,
                                refundCount = agg.refCount,
                                refundAmountMinorUnit = agg.refAmount,
                                netAmountMinorUnit = agg.payAmount - agg.refAmount,
                            )
                        }
                }
                call.respond(result)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun dateDaysBetween(from: String, to: String): Int {
    val d1 = LocalDate.parse(from)!!
    val d2 = LocalDate.parse(to)!!
    return (d2.toEpochDay() - d1.toEpochDay()).toInt() + 1
}

private fun buildTrendFromLiveData(fromDate: String, toDate: String): List<TrendDataPointDto> {
    val fromEpoch = LocalDate.parse(fromDate)!!.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
    val toEpoch   = LocalDate.parse(toDate)!!.plusDays(1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 - 1

    val closedOrders = OrdersTable.selectAll()
        .where {
            (OrdersTable.status eq "CLOSED") and
                    (OrdersTable.createdAt greaterEq fromEpoch) and
                    (OrdersTable.createdAt lessEq toEpoch)
        }
        .toList()

    val orderIds = closedOrders.map { it[OrdersTable.id] }
    val payments = if (orderIds.isNotEmpty()) {
        PaymentsTable.selectAll()
            .where {
                (PaymentsTable.orderId inList orderIds) and
                    (PaymentsTable.status inList listOf("PAID", "COMPLETED"))
            }
            .toList()
    } else emptyList()

    // Group by date
    val byDate = closedOrders.groupBy {
        java.time.Instant.ofEpochMilli(it[OrdersTable.createdAt])
            .atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString()
    }

    return byDate.map { (dateStr, orders) ->
        val gross = orders.sumOf {
            it[OrdersTable.subtotalMinorUnit] +
                    it[OrdersTable.taxTotalMinorUnit] +
                    it[OrdersTable.serviceChargeMinorUnit] +
                    it[OrdersTable.tipMinorUnit]
        }
        val discount = orders.sumOf { it[OrdersTable.discountMinorUnit] }
        val tax      = orders.sumOf { it[OrdersTable.taxTotalMinorUnit] }
        val net   = gross - discount
        val guests = orders.sumOf { it[OrdersTable.guestCount] }
        val dayOrderIds = orders.map { it[OrdersTable.id] }
        val refund = if (dayOrderIds.isNotEmpty())
            PaymentsTable.selectAll()
                .where { PaymentsTable.orderId inList dayOrderIds and (PaymentsTable.status eq "REFUNDED") }
                .sumOf { it[PaymentsTable.amountMinorUnit] }
        else 0L

        TrendDataPointDto(
            date  = dateStr,
            grossRevenueMinorUnit = gross,
            netRevenueMinorUnit   = net,
            orderCount = orders.size,
            guestCount = guests,
            averageOrderValueMinorUnit = if (orders.isNotEmpty()) net / orders.size else 0L,
            averageSpendPerGuestMinorUnit = if (guests > 0) net / guests else 0L,
            refundMinorUnit   = refund,
            discountMinorUnit = discount,
            taxMinorUnit      = tax,
        )
    }.sortedBy { it.date }
}

private fun regenerateSnapshots(fromDate: String, toDate: String): Int {
    val from = LocalDate.parse(fromDate)!!
    val to   = LocalDate.parse(toDate)!!
    var cursor = from
    var count = 0

    while (!cursor.isAfter(to)) {
        val dateStr  = cursor.toString()
        val startMs = cursor.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        val endMs   = cursor.plusDays(1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 - 1

        val closedOrders = OrdersTable.selectAll()
            .where {
                (OrdersTable.status eq "CLOSED") and
                        (OrdersTable.createdAt greaterEq startMs) and
                        (OrdersTable.createdAt lessEq endMs)
            }
            .toList()

        val orderIds = closedOrders.map { it[OrdersTable.id] }
        val allPayments = if (orderIds.isNotEmpty()) {
            PaymentsTable.selectAll()
                .where { PaymentsTable.orderId inList orderIds }
                .toList()
        } else emptyList()
        val payments = allPayments.filter { it[PaymentsTable.status] in setOf("PAID", "COMPLETED") }
        val refundsTotal = allPayments.filter { it[PaymentsTable.status] == "REFUNDED" }
            .sumOf { it[PaymentsTable.amountMinorUnit] }

        val gross = closedOrders.sumOf {
            it[OrdersTable.subtotalMinorUnit] +
                    it[OrdersTable.taxTotalMinorUnit] +
                    it[OrdersTable.serviceChargeMinorUnit] +
                    it[OrdersTable.tipMinorUnit]
        }
        val net = gross - closedOrders.sumOf { it[OrdersTable.discountMinorUnit] }
        val guests = closedOrders.sumOf { it[OrdersTable.guestCount] }
        val tip    = closedOrders.sumOf { it[OrdersTable.tipMinorUnit] }
        val svc    = closedOrders.sumOf { it[OrdersTable.serviceChargeMinorUnit] }
        val tax    = closedOrders.sumOf { it[OrdersTable.taxTotalMinorUnit] }
        val disc   = closedOrders.sumOf { it[OrdersTable.discountMinorUnit] }

        val breakdown = mutableMapOf<String, Long>()
        payments.forEach { row ->
            val m = row[PaymentsTable.method]
            breakdown[m] = (breakdown[m] ?: 0L) + row[PaymentsTable.amountMinorUnit]
        }

        val existing = DailySnapshotsTable.selectAll()
            .where { DailySnapshotsTable.date eq dateStr }
            .singleOrNull()

        val now = System.currentTimeMillis()
        if (existing == null) {
            DailySnapshotsTable.insert {
                it[DailySnapshotsTable.date]                  = dateStr
                it[DailySnapshotsTable.grossRevenueMinorUnit] = gross
                it[DailySnapshotsTable.netRevenueMinorUnit]    = net
                it[DailySnapshotsTable.totalRefundsMinorUnit]  = refundsTotal
                it[DailySnapshotsTable.orderCount]             = closedOrders.size
                it[DailySnapshotsTable.guestCount]             = guests
                it[DailySnapshotsTable.totalDiscountMinorUnit]       = disc
                it[DailySnapshotsTable.totalTipMinorUnit]          = tip
                it[DailySnapshotsTable.totalServiceChargeMinorUnit] = svc
                it[DailySnapshotsTable.totalTaxMinorUnit]         = tax
                it[DailySnapshotsTable.paymentMethodBreakdown]     = Json.encodeToString(breakdown)
                it[DailySnapshotsTable.computedAt]               = now
            }
        } else {
            DailySnapshotsTable.update({ DailySnapshotsTable.date eq dateStr }) {
                it[DailySnapshotsTable.grossRevenueMinorUnit] = gross
                it[DailySnapshotsTable.netRevenueMinorUnit]    = net
                it[DailySnapshotsTable.totalRefundsMinorUnit]  = refundsTotal
                it[DailySnapshotsTable.orderCount]             = closedOrders.size
                it[DailySnapshotsTable.guestCount]             = guests
                it[DailySnapshotsTable.totalDiscountMinorUnit]       = disc
                it[DailySnapshotsTable.totalTipMinorUnit]          = tip
                it[DailySnapshotsTable.totalServiceChargeMinorUnit] = svc
                it[DailySnapshotsTable.totalTaxMinorUnit]         = tax
                it[DailySnapshotsTable.paymentMethodBreakdown]     = Json.encodeToString(breakdown)
                it[DailySnapshotsTable.computedAt]               = now
            }
        }

        count++
        cursor = cursor.plusDays(1)
    }
    return count
}

private fun aggregateTrend(
    points: List<TrendDataPointDto>,
    granularity: String,
): Pair<List<TrendDataPointDto>, TrendSummaryDto> {
    val grouped = when (granularity) {
        "week" -> {
            val weekField = WeekFields.ISO.weekOfYear()
            points.groupBy {
                val d = LocalDate.parse(it.date)!!
                "${d.year}-W${d.get(weekField).toString().padStart(2, '0')}"
            }.map { (label, group) ->
                TrendDataPointDto(
                    date = label,
                    grossRevenueMinorUnit = group.sumOf { it.grossRevenueMinorUnit },
                    netRevenueMinorUnit   = group.sumOf { it.netRevenueMinorUnit },
                    orderCount = group.sumOf { it.orderCount },
                    guestCount = group.sumOf { it.guestCount },
                    averageOrderValueMinorUnit =
                        if (group.sumOf { it.orderCount } > 0)
                            group.sumOf { it.netRevenueMinorUnit } / group.sumOf { it.orderCount }
                        else 0L,
                    averageSpendPerGuestMinorUnit =
                        if (group.sumOf { it.guestCount } > 0)
                            group.sumOf { it.netRevenueMinorUnit } / group.sumOf { it.guestCount }
                        else 0L,
                    refundMinorUnit   = group.sumOf { it.refundMinorUnit },
                    discountMinorUnit = group.sumOf { it.discountMinorUnit },
                    taxMinorUnit      = group.sumOf { it.taxMinorUnit },
                )
            }
        }
        "month" -> {
            points.groupBy { it.date.substring(0, 7) }.map { (label, group) ->
                TrendDataPointDto(
                    date = label,
                    grossRevenueMinorUnit = group.sumOf { it.grossRevenueMinorUnit },
                    netRevenueMinorUnit   = group.sumOf { it.netRevenueMinorUnit },
                    orderCount = group.sumOf { it.orderCount },
                    guestCount = group.sumOf { it.guestCount },
                    averageOrderValueMinorUnit =
                        if (group.sumOf { it.orderCount } > 0)
                            group.sumOf { it.netRevenueMinorUnit } / group.sumOf { it.orderCount }
                        else 0L,
                    averageSpendPerGuestMinorUnit =
                        if (group.sumOf { it.guestCount } > 0)
                            group.sumOf { it.netRevenueMinorUnit } / group.sumOf { it.guestCount }
                        else 0L,
                    refundMinorUnit   = group.sumOf { it.refundMinorUnit },
                    discountMinorUnit = group.sumOf { it.discountMinorUnit },
                    taxMinorUnit      = group.sumOf { it.taxMinorUnit },
                )
            }
        }
        else -> points   // day
    }

    val totalGross = grouped.sumOf { it.grossRevenueMinorUnit }
    val totalNet   = grouped.sumOf { it.netRevenueMinorUnit }
    val totalOrders = grouped.sumOf { it.orderCount }
    val totalGuests = grouped.sumOf { it.guestCount }

    // Compare with previous period (same length)
    val growth: Double? = if (points.size >= 2) {
        val firstHalf = points.take(points.size / 2)
        val secondHalf = points.drop(points.size / 2)
        val prevNet = firstHalf.sumOf { it.netRevenueMinorUnit }
        val currNet = secondHalf.sumOf { it.netRevenueMinorUnit }
        if (prevNet > 0) (currNet - prevNet).toDouble() / prevNet else null
    } else null

    val summary = TrendSummaryDto(
        totalGrossRevenue = totalGross,
        totalNetRevenue   = totalNet,
        totalOrderCount   = totalOrders,
        totalGuestCount   = totalGuests,
        avgOrderValue     = if (totalOrders > 0) totalNet / totalOrders else 0L,
        avgSpendPerGuest = if (totalGuests > 0) totalNet / totalGuests else 0L,
        growthFromPrevious = growth,
    )

    return grouped to summary
}
