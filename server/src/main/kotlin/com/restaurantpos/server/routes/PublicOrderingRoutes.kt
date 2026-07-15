package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.db.tables.MenuProfilesTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentIntentsTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.QrCodesTable
import com.restaurantpos.server.db.tables.QrSessionsTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private val publicJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun Route.publicOrderingRoutes() {
    route("/public") {
        get("/qr/{code}") {
            val code = call.parameters["code"]!!
            val context = transaction {
                val qr = QrCodesTable.selectAll().where { QrCodesTable.code eq code }.firstOrNull()
                val config = loadQrOrderingConfig()
                val tipCfg = loadTipConfig()
                qr?.takeIf { it[QrCodesTable.enabled] && !isExpired(it[QrCodesTable.expiresAt]) }?.let {
                    val scope = it[QrCodesTable.scope]
                    PublicQrContextDto(
                        code = code,
                        scope = scope,
                        tableId = it[QrCodesTable.tableId],
                        orderTypes = orderTypesForScope(scope, config),
                        menuOnly = config.menuOnlyMode || scope == "MENU",
                        paymentTiming = if (scope == "MENU") "MENU_ONLY" else config.paymentTiming,
                        firePolicy = config.firePolicy,
                        tipEnabled = tipCfg.enabled && tipCfg.enabledOnQr,
                        tipPresets = tipCfg.presets,
                        tipCalcBase = tipCfg.calcBase,
                    )
                }
            }
            if (context == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("QR code not found"))
            else call.respond(context)
        }

        get("/menu") {
            val channel = call.request.queryParameters["channel"]
            val timeParam = call.request.queryParameters["time"]
            val dayOfWeek = java.time.LocalDate.now().dayOfWeek.value % 7 // 0=Sun

            val (items, currency) = transaction {
                val allItems = MenuItemsTable.selectAll()
                    .where { MenuItemsTable.isSoldOut eq false }
                    .orderBy(MenuItemsTable.categoryId to SortOrder.ASC, MenuItemsTable.course to SortOrder.ASC)
                    .map { it.toPublicMenuItemDto() }

                // Channel filter: items with availableChannels=[] are available on all channels
                val channelFiltered = if (channel == null) allItems
                else allItems.filter { item ->
                    item.availableChannels.isEmpty() || item.availableChannels.contains(channel)
                }

                // Time/daypart filter via menu profiles
                val filtered = if (timeParam == null) {
                    channelFiltered
                } else {
                    val profileIdsByItem = com.restaurantpos.server.db.tables.MenuItemProfilesTable.selectAll()
                        .groupBy({ it[com.restaurantpos.server.db.tables.MenuItemProfilesTable.menuItemId] },
                                 { it[com.restaurantpos.server.db.tables.MenuItemProfilesTable.menuProfileId] })
                    val profileMap = MenuProfilesTable.selectAll()
                        .associate { row ->
                            val id = row[MenuProfilesTable.id]
                            val daysOfWeek = row[MenuProfilesTable.daysOfWeek]
                                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                            id to MenuProfileEntry(
                                enabled    = row[MenuProfilesTable.enabled],
                                startTime  = row[MenuProfilesTable.startTime],
                                endTime    = row[MenuProfilesTable.endTime],
                                daysOfWeek = daysOfWeek,
                            )
                        }
                    channelFiltered.filter { item ->
                        val itemProfileIds = profileIdsByItem[item.id] ?: emptyList()
                        if (itemProfileIds.isEmpty()) return@filter true // no profile = always visible
                        val activeProfiles = itemProfileIds.mapNotNull { profileMap[it] }.filter { it.enabled }
                        if (activeProfiles.isEmpty()) return@filter false
                        activeProfiles.any { p -> p.matchesContext(timeParam, dayOfWeek) }
                    }
                }
                filtered to loadRegionCurrency()
            }
            call.respond(
                PublicMenuResponse(
                    items = items,
                    currencyCode = currency.currencyCode,
                    currencySymbol = currency.currencySymbol,
                    currencyMinorDigits = currency.currencyMinorDigits,
                )
            )
        }

        post("/sessions") {
            val req = call.receive<CreateQrSessionRequest>()
            val session = transaction {
                val qr = QrCodesTable.selectAll().where { QrCodesTable.code eq req.code }.firstOrNull()
                    ?: return@transaction null
                if (!qr[QrCodesTable.enabled] || isExpired(qr[QrCodesTable.expiresAt])) return@transaction null

                val config = loadQrOrderingConfig()
                val scope = qr[QrCodesTable.scope]
                if (config.menuOnlyMode || scope == "MENU") {
                    throw IllegalArgumentException("This QR code is menu-only")
                }

                val orderType = (req.orderType ?: defaultOrderTypeForScope(scope)).uppercase()
                require(orderTypesForScope(scope, config).contains(orderType)) { "Unsupported order type for this QR code" }
                validateCustomerInfo(orderType, req.customerInfo)

                val existingShared = if (scope == "TABLE") {
                    QrSessionsTable.selectAll()
                        .where { (QrSessionsTable.code eq req.code) and (QrSessionsTable.status eq "ACTIVE") }
                        .firstOrNull()
                } else null

                if (existingShared != null) {
                    existingShared.toQrSessionDto(includeToken = true)
                } else {
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    val token = UUID.randomUUID().toString()
                    val customerJson = publicJson.encodeToString(req.customerInfo)
                    val cartJson = publicJson.encodeToString(QrCartDto())
                    QrSessionsTable.insert {
                        it[QrSessionsTable.id] = id
                        it[code] = req.code
                        it[QrSessionsTable.orderType] = orderType
                        it[tableId] = qr[QrCodesTable.tableId]
                        it[status] = "ACTIVE"
                        it[customerInfoJson] = customerJson
                        it[QrSessionsTable.cartJson] = cartJson
                        it[orderId] = null
                        it[sessionToken] = token
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    QrSessionDto(
                        id = id,
                        code = req.code,
                        orderType = orderType,
                        tableId = qr[QrCodesTable.tableId],
                        status = "ACTIVE",
                        customerInfo = req.customerInfo,
                        cart = QrCartDto(),
                        sessionToken = token,
                    )
                }
            }
            if (session == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("QR code not found"))
            else call.respond(HttpStatusCode.Created, session)
        }

        post("/sessions/{id}/cart") {
            val sessionId = call.parameters["id"]!!
            val req = call.receive<UpdateQrCartRequest>()
            val result = transaction {
                val session = QrSessionsTable.selectAll().where { QrSessionsTable.id eq sessionId }.firstOrNull()
                    ?: return@transaction null
                requireSessionToken(call.request.headers["X-QR-Session-Token"], session)
                require(session[QrSessionsTable.status] == "ACTIVE") { "Session is not active" }
                validateCart(req.items)
                val cart = QrCartDto(req.items.filter { it.quantity > 0 })
                QrSessionsTable.update({ QrSessionsTable.id eq sessionId }) {
                    it[cartJson] = publicJson.encodeToString(cart)
                    it[updatedAt] = System.currentTimeMillis()
                }
                QrSessionsTable.selectAll().where { QrSessionsTable.id eq sessionId }.first().toQrSessionDto(includeToken = false)
            }
            if (result == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found"))
            else call.respond(result)
        }

        post("/sessions/{id}/submit") {
            val sessionId = call.parameters["id"]!!
            val req = runCatching { call.receive<SubmitQrOrderRequest>() }.getOrDefault(SubmitQrOrderRequest())
            val response = transaction {
                val session = QrSessionsTable.selectAll().where { QrSessionsTable.id eq sessionId }.firstOrNull()
                    ?: return@transaction null
                requireSessionToken(call.request.headers["X-QR-Session-Token"], session)
                require(session[QrSessionsTable.status] == "ACTIVE") { "Session is not active" }

                val config = loadQrOrderingConfig()
                val tipConfig = loadTipConfig()
                val cart = decodeCart(session[QrSessionsTable.cartJson])
                require(cart.items.isNotEmpty()) { "Cart is empty" }
                val pricedItems = priceCart(cart)
                val subtotal = pricedItems.sumOf { it.quantity.toLong() * it.unitPriceMinorUnit }
                // Apply tip only when configured; guard against client-side injection
                val tipAmount = if (tipConfig.enabledOnQr && req.tipMinorUnit > 0L) req.tipMinorUnit else 0L
                val paymentTiming = config.paymentTiming
                if (paymentTiming == "PAY_BEFORE_SUBMIT") {
                    val paid = successfulIntentAmountForSession(sessionId)
                    require(paid >= subtotal) { "Payment required before submit" }
                }

                val now = System.currentTimeMillis()
                val orderId = UUID.randomUUID().toString()
                val firePolicy = effectiveFirePolicy(config.firePolicy, session[QrSessionsTable.orderType])
                val orderStatus = if (firePolicy == "STAFF_CONFIRM") "PENDING_CONFIRMATION" else "PLACED"
                OrdersTable.insert {
                    it[id] = orderId
                    it[type] = session[QrSessionsTable.orderType]
                    it[tableId] = session[QrSessionsTable.tableId]
                    it[guestCount] = 1
                    it[sourceTerminalId] = "qr:${session[QrSessionsTable.code]}"
                    it[operatorId] = ""
                    it[subtotalMinorUnit] = subtotal
                    it[taxTotalMinorUnit] = 0L
                    it[serviceChargeMinorUnit] = 0L
                    it[tipMinorUnit] = tipAmount
                    it[discountMinorUnit] = 0L
                    it[status] = orderStatus
                    it[orderNotes] = customerNotes(session)
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                pricedItems.forEach { item ->
                    OrderItemsTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[OrderItemsTable.orderId] = orderId
                        it[menuItemId] = item.menuItemId
                        it[menuItemNameSnapshot] = item.names
                        it[quantity] = item.quantity
                        it[unitPriceMinorUnit] = item.unitPriceMinorUnit
                        it[taxRateId] = item.taxRateId
                        it[course] = item.course
                        it[status] = if (orderStatus == "PENDING_CONFIRMATION") "SUBMITTED" else "PLACED"
                        it[notes] = item.notes
                        it[categoryId] = item.categoryId
                        it[allergenSnapshot] = item.allergens
                        it[comboId] = null
                    }
                }
                if (paymentTiming == "PAY_BEFORE_SUBMIT") {
                    PaymentsTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[PaymentsTable.orderId] = orderId
                        it[amountMinorUnit] = subtotal
                        it[method] = "QR_CODE"
                        it[status] = "SUCCEEDED"
                        it[operatorId] = ""
                        it[refundedPaymentId] = null
                        it[createdAt] = now
                    }
                }
                QrSessionsTable.update({ QrSessionsTable.id eq sessionId }) {
                    it[status] = "SUBMITTED"
                    it[QrSessionsTable.orderId] = orderId
                    it[updatedAt] = now
                }
                PaymentIntentsTable.update({ PaymentIntentsTable.sessionId eq sessionId }) {
                    it[PaymentIntentsTable.orderId] = orderId
                    it[updatedAt] = now
                }
                SubmitQrOrderResponse(
                    orderId = orderId,
                    status = orderStatus,
                    totalMinorUnit = subtotal,
                    requiresPayment = paymentTiming == "PAY_AT_END" || paymentTiming == "PAY_AFTER_SUBMIT",
                    requiresStaffConfirmation = orderStatus == "PENDING_CONFIRMATION",
                )
            }
            if (response == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Session not found"))
            else call.respond(HttpStatusCode.Created, response)
        }

        post("/payments/mock-intents") {
            val req = call.receive<CreateMockPaymentIntentRequest>()
            val intent = transaction {
                require(req.sessionId != null || req.orderId != null) { "sessionId or orderId is required" }
                val amount = req.sessionId?.let { sessionCartTotal(it) } ?: req.orderId?.let { remainingOrderTotal(it) } ?: 0L
                require(amount > 0L) { "Payment amount must be positive" }
                val now = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()
                PaymentIntentsTable.insert {
                    it[PaymentIntentsTable.id] = id
                    it[sessionId] = req.sessionId
                    it[orderId] = req.orderId
                    it[amountMinorUnit] = amount
                    it[method] = "QR_CODE"
                    it[status] = "REQUIRES_CONFIRMATION"
                    it[provider] = "MOCK"
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                MockPaymentIntentDto(id, req.sessionId, req.orderId, amount, "QR_CODE", "REQUIRES_CONFIRMATION", "MOCK")
            }
            call.respond(HttpStatusCode.Created, intent)
        }

        post("/payments/mock-intents/{id}/{action}") {
            val id = call.parameters["id"]!!
            val action = call.parameters["action"]!!
            val intent = transaction {
                val row = PaymentIntentsTable.selectAll().where { PaymentIntentsTable.id eq id }.firstOrNull()
                    ?: return@transaction null
                val nextStatus = when (action) {
                    "confirm" -> "SUCCEEDED"
                    "fail" -> "FAILED"
                    "cancel" -> "CANCELED"
                    else -> throw IllegalArgumentException("Unsupported payment action")
                }
                PaymentIntentsTable.update({ PaymentIntentsTable.id eq id }) {
                    it[status] = nextStatus
                    it[updatedAt] = System.currentTimeMillis()
                }
                if (nextStatus == "SUCCEEDED" && row[PaymentIntentsTable.orderId] != null) {
                    recordOrderPayment(row[PaymentIntentsTable.orderId]!!, row[PaymentIntentsTable.amountMinorUnit])
                }
                PaymentIntentsTable.selectAll().where { PaymentIntentsTable.id eq id }.first().toMockPaymentIntentDto()
            }
            if (intent == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Payment intent not found"))
            else call.respond(intent)
        }

        get("/orders/{id}") {
            val orderId = call.parameters["id"]!!
            val detail = transaction {
                val order = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.firstOrNull()
                    ?: return@transaction null
                val items = OrderItemsTable.selectAll().where { OrderItemsTable.orderId eq orderId }.map { it.toOrderItemDtoPublic() }
                val payments = PaymentsTable.selectAll().where { PaymentsTable.orderId eq orderId }.map { it.toPaymentDtoPublic() }
                OrderDetailDto(order.toOrderSummaryDtoPublic(), items, payments)
            }
            if (detail == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found"))
            else call.respond(detail)
        }
    }
}

private data class PricedQrCartItem(
    val menuItemId: String,
    val quantity: Int,
    val notes: String,
    val names: String,
    val unitPriceMinorUnit: Long,
    val taxRateId: String?,
    val course: Int,
    val categoryId: String,
    val allergens: String,
)

private fun loadQrOrderingConfig(): QrOrderingConfigDto {
    val raw = SettingsTable.selectAll().where { SettingsTable.key eq "qrOrderingConfig" }.firstOrNull()
        ?.get(SettingsTable.value)
    return raw?.let { runCatching { publicJson.decodeFromString<QrOrderingConfigDto>(it) }.getOrNull() }
        ?: QrOrderingConfigDto()
}

@Serializable
private data class RegionCurrencyDto(
    val currencyCode: String = "CNY",
    val currencySymbol: String = "¥",
    val currencyMinorDigits: Int = 2,
)

@Serializable
private data class TipConfigDto(
    val enabled: Boolean = true,
    val presets: List<Int> = listOf(15, 18, 20, 22),
    val calcBase: String = "SUBTOTAL",
    val enabledOnQr: Boolean = false,
    val enabledOnKiosk: Boolean = false,
    val enabledOnPad: Boolean = false,
)

@Serializable
private data class RegionConfigWithTipDto(
    val tipConfig: TipConfigDto = TipConfigDto(),
)

private fun loadRegionCurrency(): RegionCurrencyDto {
    val raw = SettingsTable.selectAll().where { SettingsTable.key eq "regionConfig" }.firstOrNull()
        ?.get(SettingsTable.value)
        ?: SettingsTable.selectAll().where { SettingsTable.key eq "region-config" }.firstOrNull()
            ?.get(SettingsTable.value)
    return raw?.let { runCatching { publicJson.decodeFromString<RegionCurrencyDto>(it) }.getOrNull() }
        ?: RegionCurrencyDto()
}

private fun loadTipConfig(): TipConfigDto {
    val raw = SettingsTable.selectAll().where { SettingsTable.key eq "regionConfig" }.firstOrNull()
        ?.get(SettingsTable.value)
        ?: SettingsTable.selectAll().where { SettingsTable.key eq "region-config" }.firstOrNull()
            ?.get(SettingsTable.value)
    return raw?.let { runCatching { publicJson.decodeFromString<RegionConfigWithTipDto>(it) }.getOrNull()?.tipConfig }
        ?: TipConfigDto()
}

private fun isExpired(expiresAt: Long?): Boolean =
    expiresAt != null && expiresAt < System.currentTimeMillis()

private fun orderTypesForScope(scope: String, config: QrOrderingConfigDto): List<String> {
    val allowed = when (scope) {
        "TABLE" -> listOf("DINE_IN")
        "PICKUP" -> listOf("TAKEAWAY")
        "DELIVERY" -> listOf("DELIVERY")
        else -> emptyList()
    }
    return allowed.filter { config.supportedOrderTypes.contains(it) }
}

private fun defaultOrderTypeForScope(scope: String): String =
    when (scope) {
        "TABLE" -> "DINE_IN"
        "DELIVERY" -> "DELIVERY"
        else -> "TAKEAWAY"
    }

private fun validateCustomerInfo(orderType: String, customerInfo: CustomerInfoDto) {
    if (orderType == "DELIVERY") {
        require(!customerInfo.phone.isNullOrBlank()) { "Delivery phone is required" }
        require(!customerInfo.address.isNullOrBlank()) { "Delivery address is required" }
    }
}

private fun validateCart(items: List<QrCartItemDto>) {
    require(items.isNotEmpty()) { "Cart is empty" }
    items.forEach {
        require(it.menuItemId.isNotBlank()) { "menuItemId is required" }
        require(it.quantity in 0..99) { "Invalid quantity" }
    }
}

private fun requireSessionToken(token: String?, session: ResultRow) {
    if (token != session[QrSessionsTable.sessionToken]) {
        throw IllegalArgumentException("Invalid QR session token")
    }
}

private fun decodeCart(raw: String): QrCartDto =
    runCatching { publicJson.decodeFromString<QrCartDto>(raw) }.getOrDefault(QrCartDto())

private fun decodeCustomerInfo(raw: String): CustomerInfoDto =
    runCatching { publicJson.decodeFromString<CustomerInfoDto>(raw) }.getOrDefault(CustomerInfoDto())

private fun priceCart(cart: QrCartDto): List<PricedQrCartItem> {
    validateCart(cart.items)
    return cart.items.filter { it.quantity > 0 }.map { cartItem ->
        val menu = MenuItemsTable.selectAll().where { MenuItemsTable.id eq cartItem.menuItemId }.firstOrNull()
            ?: throw IllegalArgumentException("Menu item not found: ${cartItem.menuItemId}")
        require(!menu[MenuItemsTable.isSoldOut]) { "Menu item is sold out: ${cartItem.menuItemId}" }
        PricedQrCartItem(
            menuItemId = cartItem.menuItemId,
            quantity = cartItem.quantity,
            notes = cartItem.notes,
            names = menu[MenuItemsTable.names],
            unitPriceMinorUnit = menu[MenuItemsTable.priceMinorUnit],
            taxRateId = menu[MenuItemsTable.taxRateId],
            course = menu[MenuItemsTable.course],
            categoryId = menu[MenuItemsTable.categoryId],
            allergens = menu[MenuItemsTable.allergens],
        )
    }
}

private fun sessionCartTotal(sessionId: String): Long {
    val session = QrSessionsTable.selectAll().where { QrSessionsTable.id eq sessionId }.firstOrNull()
        ?: throw IllegalArgumentException("Session not found")
    return priceCart(decodeCart(session[QrSessionsTable.cartJson])).sumOf { it.quantity.toLong() * it.unitPriceMinorUnit }
}

private fun remainingOrderTotal(orderId: String): Long {
    val order = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.firstOrNull()
        ?: throw IllegalArgumentException("Order not found")
    val total = order[OrdersTable.subtotalMinorUnit] + order[OrdersTable.taxTotalMinorUnit] +
        order[OrdersTable.serviceChargeMinorUnit] + order[OrdersTable.tipMinorUnit] - order[OrdersTable.discountMinorUnit]
    val paid = PaymentsTable.selectAll().where { PaymentsTable.orderId eq orderId }
        .sumOf { if (it[PaymentsTable.status] == "SUCCEEDED") it[PaymentsTable.amountMinorUnit] else 0L }
    return total - paid
}

private fun successfulIntentAmountForSession(sessionId: String): Long =
    PaymentIntentsTable.selectAll()
        .where { (PaymentIntentsTable.sessionId eq sessionId) and (PaymentIntentsTable.status eq "SUCCEEDED") }
        .sumOf { it[PaymentIntentsTable.amountMinorUnit] }

private fun effectiveFirePolicy(policy: String, orderType: String): String =
    when (policy) {
        "AUTO_FIRE", "STAFF_CONFIRM" -> policy
        else -> if (orderType == "DINE_IN") "STAFF_CONFIRM" else "AUTO_FIRE"
    }

private fun customerNotes(session: ResultRow): String {
    val info = decodeCustomerInfo(session[QrSessionsTable.customerInfoJson])
    return listOfNotNull(
        info.name?.takeIf { it.isNotBlank() }?.let { "name=$it" },
        info.phone?.takeIf { it.isNotBlank() }?.let { "phone=$it" },
        info.address?.takeIf { it.isNotBlank() }?.let { "address=$it" },
        info.notes?.takeIf { it.isNotBlank() },
    ).joinToString(" | ").take(512)
}

private fun recordOrderPayment(orderId: String, amount: Long) {
    val now = System.currentTimeMillis()
    PaymentsTable.insert {
        it[id] = UUID.randomUUID().toString()
        it[PaymentsTable.orderId] = orderId
        it[amountMinorUnit] = amount
        it[method] = "QR_CODE"
        it[status] = "SUCCEEDED"
        it[operatorId] = ""
        it[refundedPaymentId] = null
        it[createdAt] = now
    }
    if (remainingOrderTotal(orderId) <= 0L) {
        OrdersTable.update({ OrdersTable.id eq orderId }) {
            it[status] = "CLOSED"
            it[updatedAt] = now
        }
    }
}

private fun ResultRow.toQrSessionDto(includeToken: Boolean): QrSessionDto =
    QrSessionDto(
        id = this[QrSessionsTable.id],
        code = this[QrSessionsTable.code],
        orderType = this[QrSessionsTable.orderType],
        tableId = this[QrSessionsTable.tableId],
        status = this[QrSessionsTable.status],
        customerInfo = decodeCustomerInfo(this[QrSessionsTable.customerInfoJson]),
        cart = decodeCart(this[QrSessionsTable.cartJson]),
        orderId = this[QrSessionsTable.orderId],
        sessionToken = if (includeToken) this[QrSessionsTable.sessionToken] else null,
    )

private data class MenuProfileEntry(
    val enabled: Boolean,
    val startTime: String?,
    val endTime: String?,
    val daysOfWeek: List<Int>?,
) {
    fun matchesContext(timeHhmm: String?, dayOfWeek: Int): Boolean {
        if (startTime != null && endTime != null && timeHhmm != null) {
            if (timeHhmm < startTime || timeHhmm > endTime) return false
        }
        if (!daysOfWeek.isNullOrEmpty() && !daysOfWeek.contains(dayOfWeek)) return false
        return true
    }
}

private fun ResultRow.toPublicMenuItemDto() = MenuItemDto(
    id                = this[MenuItemsTable.id],
    names             = this[MenuItemsTable.names],
    priceMinorUnit    = this[MenuItemsTable.priceMinorUnit],
    taxRateId         = this[MenuItemsTable.taxRateId],
    categoryId        = this[MenuItemsTable.categoryId],
    course            = this[MenuItemsTable.course],
    isSoldOut         = this[MenuItemsTable.isSoldOut],
    imageUrl          = this[MenuItemsTable.imageUrl],
    allergens         = this[MenuItemsTable.allergens],
    availableChannels = this[MenuItemsTable.availableChannels].split("|").filter { it.isNotBlank() },
    stockCount        = this[MenuItemsTable.stockCount],
    updatedAt         = this[MenuItemsTable.updatedAt],
)

private fun ResultRow.toMockPaymentIntentDto() = MockPaymentIntentDto(
    id = this[PaymentIntentsTable.id],
    sessionId = this[PaymentIntentsTable.sessionId],
    orderId = this[PaymentIntentsTable.orderId],
    amountMinorUnit = this[PaymentIntentsTable.amountMinorUnit],
    method = this[PaymentIntentsTable.method],
    status = this[PaymentIntentsTable.status],
    provider = this[PaymentIntentsTable.provider],
)

private fun ResultRow.toOrderSummaryDtoPublic() = OrderSummaryDto(
    id = this[OrdersTable.id],
    type = this[OrdersTable.type],
    tableId = this[OrdersTable.tableId],
    sourceTerminalId = this[OrdersTable.sourceTerminalId],
    guestCount = this[OrdersTable.guestCount],
    status = this[OrdersTable.status],
    subtotalMinorUnit = this[OrdersTable.subtotalMinorUnit],
    taxTotalMinorUnit = this[OrdersTable.taxTotalMinorUnit],
    serviceChargeMinorUnit = this[OrdersTable.serviceChargeMinorUnit],
    tipMinorUnit = this[OrdersTable.tipMinorUnit],
    discountMinorUnit = this[OrdersTable.discountMinorUnit],
    orderNotes = this[OrdersTable.orderNotes],
    createdAt = this[OrdersTable.createdAt],
    updatedAt = this[OrdersTable.updatedAt],
)

private fun ResultRow.toOrderItemDtoPublic() = OrderItemDto(
    id = this[OrderItemsTable.id],
    orderId = this[OrderItemsTable.orderId],
    menuItemId = this[OrderItemsTable.menuItemId],
    menuItemNameSnapshot = this[OrderItemsTable.menuItemNameSnapshot],
    quantity = this[OrderItemsTable.quantity],
    unitPriceMinorUnit = this[OrderItemsTable.unitPriceMinorUnit],
    taxRateId = this[OrderItemsTable.taxRateId],
    course = this[OrderItemsTable.course],
    status = this[OrderItemsTable.status],
    notes = this[OrderItemsTable.notes],
    categoryId = this[OrderItemsTable.categoryId],
    allergenSnapshot = this[OrderItemsTable.allergenSnapshot],
    comboId = this[OrderItemsTable.comboId],
)

private fun ResultRow.toPaymentDtoPublic() = PaymentDto(
    id = this[PaymentsTable.id],
    orderId = this[PaymentsTable.orderId],
    amountMinorUnit = this[PaymentsTable.amountMinorUnit],
    method = this[PaymentsTable.method],
    status = this[PaymentsTable.status],
    operatorId = this[PaymentsTable.operatorId],
    refundedPaymentId = this[PaymentsTable.refundedPaymentId],
    createdAt = this[PaymentsTable.createdAt],
)
