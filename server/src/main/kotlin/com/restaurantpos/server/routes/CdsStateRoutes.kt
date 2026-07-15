package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.CdsStateTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.db.tables.TablesTable
import com.restaurantpos.server.model.CdsDisplayConfigDto
import com.restaurantpos.server.model.CdsOrderDto
import com.restaurantpos.server.model.CdsOrderItemDto
import com.restaurantpos.server.model.CdsPaymentDto
import com.restaurantpos.server.model.CdsStateResponse
import com.restaurantpos.server.model.CdsStoreDto
import com.restaurantpos.server.model.CdsTotalsDto
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val cdsJson = Json { ignoreUnknownKeys = true }

/** Currency, store identity, and display config read from the admin region config. */
private data class StoreSettings(
    val store: CdsStoreDto,
    val currencySymbol: String,
    val minorDigits: Int,
    val config: CdsDisplayConfigDto,
)

private fun readStoreSettings(): StoreSettings {
    val json = SettingsTable.selectAll().where { SettingsTable.key eq "regionConfig" }
        .firstOrNull()?.get(SettingsTable.value) ?: "{}"
    val obj = runCatching { cdsJson.parseToJsonElement(json).jsonObject }.getOrNull()
    val symbol = obj?.get("currencySymbol")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "$"
    val minor = obj?.get("currencyMinorDigits")?.jsonPrimitive?.intOrNull ?: 2
    val cds = obj?.get("cdsConfig")?.jsonObject

    fun str(key: String, default: String) =
        cds?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: default
    fun bool(key: String, default: Boolean) =
        cds?.get(key)?.jsonPrimitive?.booleanOrNull ?: default

    val defaults = CdsDisplayConfigDto()
    return StoreSettings(
        store = CdsStoreDto(
            name = str("displayName", "Store Name"),
            logoUrl = cds?.get("logoUrl")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
        ),
        currencySymbol = symbol,
        minorDigits = minor.coerceIn(0, 4),
        config = CdsDisplayConfigDto(
            welcomeTitle = str("welcomeTitle", defaults.welcomeTitle),
            welcomeSubtitle = str("welcomeSubtitle", defaults.welcomeSubtitle),
            completionTitle = str("completionTitle", defaults.completionTitle),
            completionSubtitle = str("completionSubtitle", defaults.completionSubtitle),
            showOrderItems = bool("showOrderItems", defaults.showOrderItems),
            showRunningTotal = bool("showRunningTotal", defaults.showRunningTotal),
            showModifiers = bool("showModifiers", defaults.showModifiers),
        ),
    )
}

/**
 * Public (no-auth) read endpoint that drives the customer-facing display. The cashier pushes
 * the current phase via the CDS_STATE sync entity; this returns that phase plus a snapshot of
 * the referenced order so the web CDS can render the matching screen. LAN-facing, like the
 * existing /public QR ordering routes.
 *
 * Optional `?terminal=<id>` selects a specific display; otherwise the most-recently-updated
 * CDS state is used (single-lane stores).
 */
fun Route.cdsStateRoutes() {
    get("/public/cds/state") {
        val terminal = call.request.queryParameters["terminal"]
        val response = transaction {
            val settings = readStoreSettings()
            val state = (
                if (terminal != null) CdsStateTable.selectAll().where { CdsStateTable.id eq terminal }
                else CdsStateTable.selectAll().orderBy(CdsStateTable.updatedAt to SortOrder.DESC).limit(1)
                ).firstOrNull()

            val phase = state?.get(CdsStateTable.phase) ?: "WELCOME"
            val orderId = state?.get(CdsStateTable.orderId)

            fun welcome() = CdsStateResponse(
                phase = "WELCOME", store = settings.store, config = settings.config,
                currencySymbol = settings.currencySymbol, minorDigits = settings.minorDigits,
            )

            if (phase == "WELCOME" || orderId == null) {
                welcome()
            } else {
                val orderRow = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.firstOrNull()
                if (orderRow == null) {
                    welcome()
                } else {
                    CdsStateResponse(
                        phase = phase,
                        store = settings.store,
                        config = settings.config,
                        currencySymbol = settings.currencySymbol,
                        minorDigits = settings.minorDigits,
                        order = buildOrderDto(orderId, orderRow, settings.minorDigits),
                        payment = if (phase == "SUCCESS" || phase == "RECEIPT") buildPaymentDto(orderId, orderRow, settings.minorDigits) else null,
                    )
                }
            }
        }
        call.respond(HttpStatusCode.OK, response)
    }
}

private fun money(minor: Long, minorDigits: Int): Double = minor / Math.pow(10.0, minorDigits.toDouble())

private fun buildOrderDto(orderId: String, order: org.jetbrains.exposed.sql.ResultRow, minorDigits: Int): CdsOrderDto {
    val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderId }.map { row ->
        val notes = row[OrderItemsTable.notes]
        CdsOrderItemDto(
            qty = row[OrderItemsTable.quantity],
            name = displayName(row[OrderItemsTable.menuItemNameSnapshot], row[OrderItemsTable.menuItemId]),
            modifiers = notes.ifBlank { null },
            amount = money(row[OrderItemsTable.unitPriceMinorUnit] * row[OrderItemsTable.quantity], minorDigits),
        )
    }
    val tableId = order[OrdersTable.tableId]
    val tableLabel = tableId?.let { id ->
        TablesTable.selectAll().where { TablesTable.id eq id }.firstOrNull()?.get(TablesTable.name)
    }
    return CdsOrderDto(
        number = orderId.takeLast(4),
        type = typeLabel(order[OrdersTable.type]),
        tableLabel = tableLabel,
        items = items,
        totals = CdsTotalsDto(
            subtotal = money(order[OrdersTable.subtotalMinorUnit], minorDigits),
            discount = money(order[OrdersTable.discountMinorUnit], minorDigits),
            tax = money(order[OrdersTable.taxTotalMinorUnit], minorDigits),
            serviceCharge = money(order[OrdersTable.serviceChargeMinorUnit], minorDigits),
            tip = money(order[OrdersTable.tipMinorUnit], minorDigits),
            total = money(orderTotal(order), minorDigits),
        ),
    )
}

private fun buildPaymentDto(orderId: String, order: org.jetbrains.exposed.sql.ResultRow, minorDigits: Int): CdsPaymentDto {
    val paid = PaymentsTable.selectAll().where { PaymentsTable.orderId eq orderId }
        .filter { it[PaymentsTable.status] == "PAID" }
        .sumOf { it[PaymentsTable.amountMinorUnit] }
    val total = orderTotal(order)
    return CdsPaymentDto(
        totalPaid = money(paid, minorDigits),
        change = money((paid - total).coerceAtLeast(0), minorDigits),
    )
}

private fun orderTotal(order: org.jetbrains.exposed.sql.ResultRow): Long =
    order[OrdersTable.subtotalMinorUnit] + order[OrdersTable.taxTotalMinorUnit] +
        order[OrdersTable.serviceChargeMinorUnit] + order[OrdersTable.tipMinorUnit] -
        order[OrdersTable.discountMinorUnit]

private fun typeLabel(type: String): String = when (type) {
    "TAKEAWAY" -> "Takeaway"
    "DELIVERY" -> "Delivery"
    else -> "Dine In"
}

/** Picks a display name from the stored multilingual JSON map, tolerating en/en-US key styles. */
private fun displayName(nameSnapshot: String, fallback: String): String {
    val map = runCatching { cdsJson.parseToJsonElement(nameSnapshot).jsonObject }.getOrNull()
        ?: return fallback
    fun get(key: String) = map[key]?.jsonPrimitive?.content
    return get("en") ?: get("en-US")
        ?: map.entries.firstOrNull { it.key.startsWith("en") }?.value?.jsonPrimitive?.content
        ?: map.values.firstOrNull()?.jsonPrimitive?.content
        ?: fallback
}
