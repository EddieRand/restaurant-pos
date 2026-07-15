package com.restaurantpos.server.sync

import com.restaurantpos.server.db.tables.CdsStateTable
import com.restaurantpos.server.db.tables.CustomersTable
import com.restaurantpos.server.db.tables.DailySnapshotsTable
import com.restaurantpos.server.db.tables.KitchenTicketsTable
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.PaymentsTable
import com.restaurantpos.server.db.tables.TablesTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * 同步数据处理器 —— 将 Android 终端推送的 raw JSON payload 反序列化到服务端业务表。
 *
 * 在 SyncRoutes.post("/push") 写入 SyncLogTable 之后调用。
 * 采用 last-write-wins 策略：仅当 incoming updatedAt >= stored updatedAt 才写入。
 */
object SyncPushProcessor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun process(entityType: String, payload: String) {
        when (entityType) {
            "ORDER"          -> processOrder(payload)
            "ORDER_ITEM"     -> processOrderItem(payload)
            "PAYMENT"        -> processPayment(payload)
            "DAILY_SNAPSHOT" -> processDailySnapshot(payload)
            "KITCHEN_TICKET" -> processKitchenTicket(payload)
            "TABLE"          -> processTable(payload)
            "CUSTOMER"       -> processCustomer(payload)
            "CDS_STATE"      -> processCdsState(payload)
            else             -> { /* no-op: MENU_ITEM, RESERVATION 由服务端 Admin API 管理 */ }
        }
    }

    // ── ORDER ──────────────────────────────────────────────────────────

    private fun processOrder(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val orderId = obj["id"]?.jsonPrimitive?.content ?: return
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val existing = OrdersTable.selectAll()
            .where { OrdersTable.id eq orderId }
            .singleOrNull()
        val existingUpdatedAt = existing?.get(OrdersTable.updatedAt) ?: -1L

        if (updatedAt < existingUpdatedAt) return

        // Clients push partial payloads (e.g. {id, fulfillmentStatus} or {id, status}) —
        // every missing field must fall back to the existing row, never a hard default,
        // or a partial update wipes the rest of the order (status, totals, tableId…).
        val type        = obj["type"]?.jsonPrimitive?.content ?: existing?.get(OrdersTable.type) ?: "DINE_IN"
        val tableId     = if (obj.containsKey("tableId")) obj["tableId"]?.jsonPrimitive?.contentOrNull
                          else existing?.get(OrdersTable.tableId)
        val guestCount  = obj["guestCount"]?.jsonPrimitive?.intOrNull ?: existing?.get(OrdersTable.guestCount) ?: 1
        val srcTerminal = obj["sourceTerminalId"]?.jsonPrimitive?.content ?: existing?.get(OrdersTable.sourceTerminalId) ?: ""
        val operatorId  = obj["operatorId"]?.jsonPrimitive?.content ?: existing?.get(OrdersTable.operatorId) ?: ""
        val subtotal    = obj["subtotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.subtotalMinorUnit) ?: 0L
        val taxTotal    = obj["taxTotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.taxTotalMinorUnit) ?: 0L
        val svcCharge   = obj["serviceChargeMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.serviceChargeMinorUnit) ?: 0L
        val tip         = obj["tipMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.tipMinorUnit) ?: 0L
        val discount    = obj["discountMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.discountMinorUnit) ?: 0L
        val status      = obj["status"]?.jsonPrimitive?.content ?: existing?.get(OrdersTable.status) ?: "OPEN"
        val notes       = obj["orderNotes"]?.jsonPrimitive?.content ?: existing?.get(OrdersTable.orderNotes) ?: ""
        val createdAt   = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrdersTable.createdAt) ?: System.currentTimeMillis()
        val pickupCode  = if (obj.containsKey("pickupCode")) obj["pickupCode"]?.jsonPrimitive?.contentOrNull
                          else existing?.get(OrdersTable.pickupCode)
        val fulfillmentStatus = obj["fulfillmentStatus"]?.jsonPrimitive?.content
            ?: existing?.get(OrdersTable.fulfillmentStatus) ?: "NOT_READY"

        if (existing == null) {
            OrdersTable.insert {
                it[OrdersTable.id] = orderId
                it[OrdersTable.type] = type
                it[OrdersTable.tableId] = tableId
                it[OrdersTable.guestCount] = guestCount
                it[OrdersTable.sourceTerminalId] = srcTerminal
                it[OrdersTable.operatorId] = operatorId
                it[OrdersTable.subtotalMinorUnit] = subtotal
                it[OrdersTable.taxTotalMinorUnit] = taxTotal
                it[OrdersTable.serviceChargeMinorUnit] = svcCharge
                it[OrdersTable.tipMinorUnit] = tip
                it[OrdersTable.discountMinorUnit] = discount
                it[OrdersTable.status] = status
                it[OrdersTable.orderNotes] = notes
                it[OrdersTable.createdAt] = createdAt
                it[OrdersTable.updatedAt] = updatedAt
                it[OrdersTable.pickupCode] = pickupCode
                it[OrdersTable.fulfillmentStatus] = fulfillmentStatus
            }
        } else {
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.type] = type
                it[OrdersTable.tableId] = tableId
                it[OrdersTable.guestCount] = guestCount
                it[OrdersTable.sourceTerminalId] = srcTerminal
                it[OrdersTable.operatorId] = operatorId
                it[OrdersTable.subtotalMinorUnit] = subtotal
                it[OrdersTable.taxTotalMinorUnit] = taxTotal
                it[OrdersTable.serviceChargeMinorUnit] = svcCharge
                it[OrdersTable.tipMinorUnit] = tip
                it[OrdersTable.discountMinorUnit] = discount
                it[OrdersTable.status] = status
                it[OrdersTable.orderNotes] = notes
                it[OrdersTable.createdAt] = createdAt
                it[OrdersTable.updatedAt] = updatedAt
                it[OrdersTable.pickupCode] = pickupCode
                it[OrdersTable.fulfillmentStatus] = fulfillmentStatus
            }
        }

        // Process nested items
        processNestedOrderItems(orderId, obj["items"]?.jsonArray)

        // Process nested payments (Android bundles payments with orders)
        processNestedPayments(orderId, obj["payments"]?.jsonArray)
    }

    private fun processNestedOrderItems(orderId: String, itemsArray: kotlinx.serialization.json.JsonArray?) {
        if (itemsArray == null) return
        OrderItemsTable.deleteWhere { OrderItemsTable.orderId eq orderId }
        itemsArray.forEach { itemEl ->
            val item = itemEl.jsonObject
            val itemId = item["id"]?.jsonPrimitive?.content ?: return@forEach
            OrderItemsTable.insert {
                it[OrderItemsTable.id] = itemId
                it[OrderItemsTable.orderId] = orderId
                it[OrderItemsTable.menuItemId] = item["menuItemId"]?.jsonPrimitive?.content ?: ""
                it[OrderItemsTable.menuItemNameSnapshot] =
                    item["menuItemNameSnapshot"]?.jsonPrimitive?.content
                        ?: item["menuItemName"]?.jsonPrimitive?.content ?: "{}"
                it[OrderItemsTable.quantity] = item["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                it[OrderItemsTable.unitPriceMinorUnit] = item["unitPriceMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
                it[OrderItemsTable.taxRateId] = item["taxRateId"]?.jsonPrimitive?.content
                it[OrderItemsTable.course] = item["course"]?.jsonPrimitive?.intOrNull ?: 1
                it[OrderItemsTable.status] = item["status"]?.jsonPrimitive?.content ?: "ACTIVE"
                it[OrderItemsTable.notes] = item["notes"]?.jsonPrimitive?.content ?: ""
                it[OrderItemsTable.categoryId] = item["categoryId"]?.jsonPrimitive?.content
                it[OrderItemsTable.allergenSnapshot] =
                    item["allergenSnapshot"]?.jsonPrimitive?.content
                        ?: item["allergens"]?.jsonPrimitive?.content ?: ""
                it[OrderItemsTable.comboId] = item["comboId"]?.jsonPrimitive?.content
            }
        }
    }

    private fun processNestedPayments(orderId: String, paymentsArray: kotlinx.serialization.json.JsonArray?) {
        if (paymentsArray == null) return
        paymentsArray.forEach { payEl ->
            val pay = payEl.jsonObject
            val paymentId = pay["id"]?.jsonPrimitive?.content ?: return@forEach

            val amount    = pay["amountMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
            val method    = pay["method"]?.jsonPrimitive?.content ?: "CASH"
            val status    = pay["status"]?.jsonPrimitive?.content ?: "PAID"
            val operator  = pay["operatorId"]?.jsonPrimitive?.content ?: ""
            val refundId  = pay["refundedPaymentId"]?.jsonPrimitive?.content
            val createdAt = pay["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

            val existing = PaymentsTable.selectAll()
                .where { PaymentsTable.id eq paymentId }
                .singleOrNull()

            if (existing == null) {
                PaymentsTable.insert {
                    it[PaymentsTable.id] = paymentId
                    it[PaymentsTable.orderId] = orderId
                    it[PaymentsTable.amountMinorUnit] = amount
                    it[PaymentsTable.method] = method
                    it[PaymentsTable.status] = status
                    it[PaymentsTable.operatorId] = operator
                    it[PaymentsTable.refundedPaymentId] = refundId
                    it[PaymentsTable.createdAt] = createdAt
                }
            } else {
                PaymentsTable.update({ PaymentsTable.id eq paymentId }) {
                    it[PaymentsTable.orderId] = orderId
                    it[PaymentsTable.amountMinorUnit] = amount
                    it[PaymentsTable.method] = method
                    it[PaymentsTable.status] = status
                    it[PaymentsTable.operatorId] = operator
                    it[PaymentsTable.refundedPaymentId] = refundId
                    it[PaymentsTable.createdAt] = createdAt
                }
            }
        }
    }

    // ── ORDER_ITEM (standalone) ────────────────────────────────────────

    private fun processOrderItem(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val itemId = obj["id"]?.jsonPrimitive?.content ?: return
        val orderId = obj["orderId"]?.jsonPrimitive?.content ?: return

        val existing = OrderItemsTable.selectAll()
            .where { OrderItemsTable.id eq itemId }
            .singleOrNull()

        // Missing fields fall back to the existing row (partial updates must not wipe data)
        val mid     = obj["menuItemId"]?.jsonPrimitive?.content ?: existing?.get(OrderItemsTable.menuItemId) ?: ""
        val nameSnap = obj["menuItemNameSnapshot"]?.jsonPrimitive?.content ?: existing?.get(OrderItemsTable.menuItemNameSnapshot) ?: "{}"
        val qty     = obj["quantity"]?.jsonPrimitive?.intOrNull ?: existing?.get(OrderItemsTable.quantity) ?: 1
        val price   = obj["unitPriceMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(OrderItemsTable.unitPriceMinorUnit) ?: 0L
        val taxId   = if (obj.containsKey("taxRateId")) obj["taxRateId"]?.jsonPrimitive?.contentOrNull
                      else existing?.get(OrderItemsTable.taxRateId)
        val course  = obj["course"]?.jsonPrimitive?.intOrNull ?: existing?.get(OrderItemsTable.course) ?: 1
        val status  = obj["status"]?.jsonPrimitive?.content ?: existing?.get(OrderItemsTable.status) ?: "ACTIVE"
        val notes   = obj["notes"]?.jsonPrimitive?.content ?: existing?.get(OrderItemsTable.notes) ?: ""
        val catId   = if (obj.containsKey("categoryId")) obj["categoryId"]?.jsonPrimitive?.contentOrNull
                      else existing?.get(OrderItemsTable.categoryId)
        val allergen = obj["allergenSnapshot"]?.jsonPrimitive?.content ?: existing?.get(OrderItemsTable.allergenSnapshot) ?: ""
        val comboId = if (obj.containsKey("comboId")) obj["comboId"]?.jsonPrimitive?.contentOrNull
                      else existing?.get(OrderItemsTable.comboId)

        if (existing == null) {
            OrderItemsTable.insert {
                it[OrderItemsTable.id] = itemId
                it[OrderItemsTable.orderId] = orderId
                it[OrderItemsTable.menuItemId] = mid
                it[OrderItemsTable.menuItemNameSnapshot] = nameSnap
                it[OrderItemsTable.quantity] = qty
                it[OrderItemsTable.unitPriceMinorUnit] = price
                it[OrderItemsTable.taxRateId] = taxId
                it[OrderItemsTable.course] = course
                it[OrderItemsTable.status] = status
                it[OrderItemsTable.notes] = notes
                it[OrderItemsTable.categoryId] = catId
                it[OrderItemsTable.allergenSnapshot] = allergen
                it[OrderItemsTable.comboId] = comboId
            }
        } else {
            OrderItemsTable.update({ OrderItemsTable.id eq itemId }) {
                it[OrderItemsTable.orderId] = orderId
                it[OrderItemsTable.menuItemId] = mid
                it[OrderItemsTable.menuItemNameSnapshot] = nameSnap
                it[OrderItemsTable.quantity] = qty
                it[OrderItemsTable.unitPriceMinorUnit] = price
                it[OrderItemsTable.taxRateId] = taxId
                it[OrderItemsTable.course] = course
                it[OrderItemsTable.status] = status
                it[OrderItemsTable.notes] = notes
                it[OrderItemsTable.categoryId] = catId
                it[OrderItemsTable.allergenSnapshot] = allergen
                it[OrderItemsTable.comboId] = comboId
            }
        }
    }

    // ── PAYMENT (standalone) ───────────────────────────────────────────

    private fun processPayment(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val paymentId = obj["id"]?.jsonPrimitive?.content ?: return

        val existing = PaymentsTable.selectAll()
            .where { PaymentsTable.id eq paymentId }
            .singleOrNull()

        val orderId  = obj["orderId"]?.jsonPrimitive?.content ?: ""
        val amount   = obj["amountMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val method   = obj["method"]?.jsonPrimitive?.content ?: "CASH"
        val status   = obj["status"]?.jsonPrimitive?.content ?: "PAID"
        val operator = obj["operatorId"]?.jsonPrimitive?.content ?: ""
        val refundId = obj["refundedPaymentId"]?.jsonPrimitive?.content
        val createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        if (existing == null) {
            PaymentsTable.insert {
                it[PaymentsTable.id] = paymentId
                it[PaymentsTable.orderId] = orderId
                it[PaymentsTable.amountMinorUnit] = amount
                it[PaymentsTable.method] = method
                it[PaymentsTable.status] = status
                it[PaymentsTable.operatorId] = operator
                it[PaymentsTable.refundedPaymentId] = refundId
                it[PaymentsTable.createdAt] = createdAt
            }
        } else {
            PaymentsTable.update({ PaymentsTable.id eq paymentId }) {
                it[PaymentsTable.orderId] = orderId
                it[PaymentsTable.amountMinorUnit] = amount
                it[PaymentsTable.method] = method
                it[PaymentsTable.status] = status
                it[PaymentsTable.operatorId] = operator
                it[PaymentsTable.refundedPaymentId] = refundId
                it[PaymentsTable.createdAt] = createdAt
            }
        }
    }

    // ── TABLE ──────────────────────────────────────────────────────────

    private fun processTable(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val tableId = obj["id"]?.jsonPrimitive?.content ?: return
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val existing = TablesTable.selectAll().where { TablesTable.id eq tableId }.singleOrNull()
        val existingUpdatedAt = existing?.get(TablesTable.updatedAt) ?: -1L
        if (updatedAt < existingUpdatedAt) return

        val name = obj["name"]?.jsonPrimitive?.content ?: existing?.get(TablesTable.name) ?: ""
        val sectionId = obj["sectionId"]?.jsonPrimitive?.content ?: existing?.get(TablesTable.sectionId) ?: ""
        val capacity = obj["capacity"]?.jsonPrimitive?.intOrNull ?: existing?.get(TablesTable.capacity) ?: 4
        val currentOrderId = if (obj.containsKey("currentOrderId")) obj["currentOrderId"]?.jsonPrimitive?.contentOrNull
                             else existing?.get(TablesTable.currentOrderId)
        val status = obj["status"]?.jsonPrimitive?.content ?: existing?.get(TablesTable.status) ?: "AVAILABLE"

        if (existing == null) {
            TablesTable.insert {
                it[TablesTable.id] = tableId
                it[TablesTable.name] = name
                it[TablesTable.sectionId] = sectionId
                it[TablesTable.capacity] = capacity
                it[TablesTable.currentOrderId] = currentOrderId
                it[TablesTable.status] = status
                it[TablesTable.updatedAt] = updatedAt
            }
        } else {
            TablesTable.update({ TablesTable.id eq tableId }) {
                it[TablesTable.name] = name
                it[TablesTable.sectionId] = sectionId
                it[TablesTable.capacity] = capacity
                it[TablesTable.currentOrderId] = currentOrderId
                it[TablesTable.status] = status
                it[TablesTable.updatedAt] = updatedAt
            }
        }
    }

    // ── CUSTOMER ───────────────────────────────────────────────────────

    private fun processCustomer(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val customerId = obj["id"]?.jsonPrimitive?.content ?: return
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val existing = CustomersTable.selectAll().where { CustomersTable.id eq customerId }.singleOrNull()
        val existingUpdatedAt = existing?.get(CustomersTable.updatedAt) ?: -1L
        if (updatedAt < existingUpdatedAt) return

        val name = obj["name"]?.jsonPrimitive?.content ?: existing?.get(CustomersTable.name) ?: ""
        val phone = obj["phone"]?.jsonPrimitive?.content ?: existing?.get(CustomersTable.phone) ?: ""
        val email = if (obj.containsKey("email")) obj["email"]?.jsonPrimitive?.contentOrNull else existing?.get(CustomersTable.email)
        val gender = if (obj.containsKey("gender")) obj["gender"]?.jsonPrimitive?.contentOrNull else existing?.get(CustomersTable.gender)
        val birthday = if (obj.containsKey("birthday")) obj["birthday"]?.jsonPrimitive?.contentOrNull else existing?.get(CustomersTable.birthday)
        val tags = obj["tags"]?.jsonPrimitive?.content ?: existing?.get(CustomersTable.tags) ?: ""
        val notes = if (obj.containsKey("notes")) obj["notes"]?.jsonPrimitive?.contentOrNull else existing?.get(CustomersTable.notes)
        val totalSpend = obj["totalSpendMinorUnit"]?.jsonPrimitive?.longOrNull ?: existing?.get(CustomersTable.totalSpendMinorUnit) ?: 0L
        val loyalty = obj["loyaltyPoints"]?.jsonPrimitive?.longOrNull ?: existing?.get(CustomersTable.loyaltyPoints) ?: 0L
        val tier = if (obj.containsKey("membershipTierId")) obj["membershipTierId"]?.jsonPrimitive?.contentOrNull else existing?.get(CustomersTable.membershipTierId)
        val visits = obj["totalVisits"]?.jsonPrimitive?.intOrNull ?: existing?.get(CustomersTable.totalVisits) ?: 0
        val lastVisit = obj["lastVisitAt"]?.jsonPrimitive?.longOrNull ?: existing?.get(CustomersTable.lastVisitAt) ?: 0L
        val registered = obj["registeredAt"]?.jsonPrimitive?.longOrNull ?: existing?.get(CustomersTable.registeredAt) ?: updatedAt

        if (existing == null) {
            CustomersTable.insert {
                it[CustomersTable.id] = customerId
                it[CustomersTable.name] = name
                it[CustomersTable.phone] = phone
                it[CustomersTable.email] = email
                it[CustomersTable.gender] = gender
                it[CustomersTable.birthday] = birthday
                it[CustomersTable.tags] = tags
                it[CustomersTable.notes] = notes
                it[CustomersTable.totalSpendMinorUnit] = totalSpend
                it[CustomersTable.loyaltyPoints] = loyalty
                it[CustomersTable.membershipTierId] = tier
                it[CustomersTable.totalVisits] = visits
                it[CustomersTable.lastVisitAt] = lastVisit
                it[CustomersTable.registeredAt] = registered
                it[CustomersTable.updatedAt] = updatedAt
            }
        } else {
            CustomersTable.update({ CustomersTable.id eq customerId }) {
                it[CustomersTable.name] = name
                it[CustomersTable.phone] = phone
                it[CustomersTable.email] = email
                it[CustomersTable.gender] = gender
                it[CustomersTable.birthday] = birthday
                it[CustomersTable.tags] = tags
                it[CustomersTable.notes] = notes
                it[CustomersTable.totalSpendMinorUnit] = totalSpend
                it[CustomersTable.loyaltyPoints] = loyalty
                it[CustomersTable.membershipTierId] = tier
                it[CustomersTable.totalVisits] = visits
                it[CustomersTable.lastVisitAt] = lastVisit
                it[CustomersTable.registeredAt] = registered
                it[CustomersTable.updatedAt] = updatedAt
            }
        }
    }

    // ── CDS_STATE ──────────────────────────────────────────────────────

    private fun processCdsState(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val id = obj["id"]?.jsonPrimitive?.content
            ?: obj["terminalId"]?.jsonPrimitive?.content ?: return
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val existing = CdsStateTable.selectAll().where { CdsStateTable.id eq id }.singleOrNull()
        if (updatedAt < (existing?.get(CdsStateTable.updatedAt) ?: -1L)) return

        val orderId = if (obj.containsKey("orderId")) obj["orderId"]?.jsonPrimitive?.contentOrNull
                      else existing?.get(CdsStateTable.orderId)
        val phase = obj["phase"]?.jsonPrimitive?.content ?: existing?.get(CdsStateTable.phase) ?: "WELCOME"

        if (existing == null) {
            CdsStateTable.insert {
                it[CdsStateTable.id] = id
                it[CdsStateTable.orderId] = orderId
                it[CdsStateTable.phase] = phase
                it[CdsStateTable.updatedAt] = updatedAt
            }
        } else {
            CdsStateTable.update({ CdsStateTable.id eq id }) {
                it[CdsStateTable.orderId] = orderId
                it[CdsStateTable.phase] = phase
                it[CdsStateTable.updatedAt] = updatedAt
            }
        }
    }

    // ── KITCHEN_TICKET ─────────────────────────────────────────────────

    private fun processKitchenTicket(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val ticketId = obj["id"]?.jsonPrimitive?.content ?: return
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val existing = KitchenTicketsTable.selectAll()
            .where { KitchenTicketsTable.id eq ticketId }
            .singleOrNull()
        val existingUpdatedAt = existing?.get(KitchenTicketsTable.updatedAt) ?: -1L

        if (updatedAt < existingUpdatedAt) return

        // Missing fields fall back to the existing row (partial bump updates must not wipe data)
        val orderId = obj["orderId"]?.jsonPrimitive?.content
            ?: existing?.get(KitchenTicketsTable.orderId) ?: return
        val orderItemIds = (obj["orderItemIds"] as? JsonArray)?.map { it.jsonPrimitive.content }
        val orderItemIdsJson = orderItemIds?.let { Json.encodeToString(it) }
            ?: existing?.get(KitchenTicketsTable.orderItemIds) ?: "[]"
        val stationId = obj["stationId"]?.jsonPrimitive?.content ?: existing?.get(KitchenTicketsTable.stationId) ?: ""
        val course = obj["course"]?.jsonPrimitive?.intOrNull ?: existing?.get(KitchenTicketsTable.course) ?: 1
        val status = obj["status"]?.jsonPrimitive?.content ?: existing?.get(KitchenTicketsTable.status) ?: "NEW"
        val createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: existing?.get(KitchenTicketsTable.createdAt) ?: updatedAt
        val bumpedAt = if (obj.containsKey("bumpedAt")) obj["bumpedAt"]?.jsonPrimitive?.longOrNull
                       else existing?.get(KitchenTicketsTable.bumpedAt)

        if (existing == null) {
            KitchenTicketsTable.insert {
                it[KitchenTicketsTable.id] = ticketId
                it[KitchenTicketsTable.orderId] = orderId
                it[KitchenTicketsTable.orderItemIds] = orderItemIdsJson
                it[KitchenTicketsTable.stationId] = stationId
                it[KitchenTicketsTable.course] = course
                it[KitchenTicketsTable.status] = status
                it[KitchenTicketsTable.createdAt] = createdAt
                it[KitchenTicketsTable.bumpedAt] = bumpedAt
                it[KitchenTicketsTable.updatedAt] = updatedAt
            }
        } else {
            KitchenTicketsTable.update({ KitchenTicketsTable.id eq ticketId }) {
                it[KitchenTicketsTable.orderId] = orderId
                it[KitchenTicketsTable.orderItemIds] = orderItemIdsJson
                it[KitchenTicketsTable.stationId] = stationId
                it[KitchenTicketsTable.course] = course
                it[KitchenTicketsTable.status] = status
                it[KitchenTicketsTable.createdAt] = createdAt
                it[KitchenTicketsTable.bumpedAt] = bumpedAt
                it[KitchenTicketsTable.updatedAt] = updatedAt
            }
        }
    }

    // ── DAILY_SNAPSHOT ─────────────────────────────────────────────────

    private fun processDailySnapshot(payload: String) {
        val obj = json.parseToJsonElement(payload).jsonObject
        val date = obj["date"]?.jsonPrimitive?.content ?: return
        val computedAt = obj["computedAt"]?.jsonPrimitive?.longOrNull
            ?: obj["createdAt"]?.jsonPrimitive?.longOrNull
            ?: obj["updatedAt"]?.jsonPrimitive?.longOrNull
            ?: System.currentTimeMillis()

        val existing = DailySnapshotsTable.selectAll()
            .where { DailySnapshotsTable.date eq date }
            .singleOrNull()

        val gross     = obj["grossRevenueMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val net       = obj["netRevenueMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val refunds   = obj["totalRefundsMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val orderCnt  = obj["orderCount"]?.jsonPrimitive?.intOrNull ?: 0
        val guestCnt  = obj["guestCount"]?.jsonPrimitive?.intOrNull ?: 0
        val disc      = obj["discountTotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val tip       = obj["tipTotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val svc       = obj["serviceChargeTotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val tax       = obj["taxTotalMinorUnit"]?.jsonPrimitive?.longOrNull ?: 0L
        val breakdown = obj["paymentBreakdownJson"]?.jsonPrimitive?.content ?: "{}"

        if (existing == null) {
            DailySnapshotsTable.insert {
                it[DailySnapshotsTable.date] = date
                it[DailySnapshotsTable.grossRevenueMinorUnit] = gross
                it[DailySnapshotsTable.netRevenueMinorUnit] = net
                it[DailySnapshotsTable.totalRefundsMinorUnit] = refunds
                it[DailySnapshotsTable.orderCount] = orderCnt
                it[DailySnapshotsTable.guestCount] = guestCnt
                it[DailySnapshotsTable.totalDiscountMinorUnit] = disc
                it[DailySnapshotsTable.totalTipMinorUnit] = tip
                it[DailySnapshotsTable.totalServiceChargeMinorUnit] = svc
                it[DailySnapshotsTable.totalTaxMinorUnit] = tax
                it[DailySnapshotsTable.paymentMethodBreakdown] = breakdown
                it[DailySnapshotsTable.computedAt] = computedAt
            }
        } else {
            val existingComputedAt = existing[DailySnapshotsTable.computedAt]
            if (computedAt >= existingComputedAt) {
                DailySnapshotsTable.update({ DailySnapshotsTable.date eq date }) {
                    it[DailySnapshotsTable.grossRevenueMinorUnit] = gross
                    it[DailySnapshotsTable.netRevenueMinorUnit] = net
                    it[DailySnapshotsTable.totalRefundsMinorUnit] = refunds
                    it[DailySnapshotsTable.orderCount] = orderCnt
                    it[DailySnapshotsTable.guestCount] = guestCnt
                    it[DailySnapshotsTable.totalDiscountMinorUnit] = disc
                    it[DailySnapshotsTable.totalTipMinorUnit] = tip
                    it[DailySnapshotsTable.totalServiceChargeMinorUnit] = svc
                    it[DailySnapshotsTable.totalTaxMinorUnit] = tax
                    it[DailySnapshotsTable.paymentMethodBreakdown] = breakdown
                    it[DailySnapshotsTable.computedAt] = computedAt
                }
            }
        }
    }
}
