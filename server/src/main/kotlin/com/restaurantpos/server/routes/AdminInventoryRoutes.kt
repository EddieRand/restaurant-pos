package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable data class IngredientDto(
    val id: String, val name: String, val category: String, val unit: String,
    val purchaseUnit: String? = null, val purchaseUnitFactor: Double? = null,
    val safetyStock: Double, val currentStock: Double, val createdAt: Long,
)

@Serializable data class CreateIngredientRequest(
    val id: String = UUID.randomUUID().toString(),
    val name: String, val category: String = "", val unit: String,
    val purchaseUnit: String? = null, val purchaseUnitFactor: Double? = null,
    val safetyStock: Double = 0.0,
)

@Serializable data class UpdateIngredientRequest(
    val name: String? = null, val category: String? = null,
    val safetyStock: Double? = null, val currentStock: Double? = null,
)

@Serializable data class StockMovementDto(
    val id: String, val ingredientId: String, val type: String,
    val qty: Double, val note: String, val createdAt: Long,
)

@Serializable data class CreateStockMovementRequest(
    val id: String = UUID.randomUUID().toString(),
    val ingredientId: String, val type: String, val qty: Double, val note: String = "",
)

@Serializable data class SupplierDto(
    val id: String, val name: String, val contact: String, val phone: String, val note: String, val createdAt: Long,
)

@Serializable data class CreateSupplierRequest(
    val id: String = UUID.randomUUID().toString(),
    val name: String, val contact: String = "", val phone: String = "", val note: String = "",
)

@Serializable data class UpdateSupplierRequest(
    val name: String? = null, val contact: String? = null, val phone: String? = null, val note: String? = null,
)

@Serializable data class PurchaseOrderItemDto(val id: String, val orderId: String, val ingredientId: String, val qty: Double, val unitCost: Long)
@Serializable data class PurchaseOrderDto(
    val id: String, val status: String, val supplier: String, val supplierId: String? = null,
    val note: String, val createdAt: Long, val confirmedAt: Long? = null, val items: List<PurchaseOrderItemDto>,
)

@Serializable data class CreatePurchaseOrderItemInput(val ingredientId: String, val qty: Double, val unitCost: Long = 0L)
@Serializable data class CreatePurchaseOrderRequest(
    val id: String = UUID.randomUUID().toString(),
    val supplier: String, val supplierId: String? = null, val note: String = "",
    val items: List<CreatePurchaseOrderItemInput>,
)

@Serializable data class OutboundItemDto(val id: String, val orderId: String, val ingredientId: String, val qty: Double)
@Serializable data class OutboundOrderDto(
    val id: String, val status: String, val type: String, val note: String,
    val createdAt: Long, val confirmedAt: Long? = null, val items: List<OutboundItemDto>,
)

@Serializable data class CreateOutboundItemInput(val ingredientId: String, val qty: Double)
@Serializable data class CreateOutboundRequest(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "ISSUE", val note: String = "", val items: List<CreateOutboundItemInput>,
)

@Serializable data class BomLineDto(val id: String, val menuItemId: String, val ingredientId: String, val qty: Double)
@Serializable data class BomRecipeDto(val menuItemId: String, val lines: List<BomLineDto>)
@Serializable data class BomLineInput(val ingredientId: String, val qty: Double)
@Serializable data class SaveBomRequest(val lines: List<BomLineInput>)

@Serializable data class StocktakeItemDto(val id: String, val orderId: String, val ingredientId: String, val systemQty: Double, val actualQty: Double)
@Serializable data class StocktakeOrderDto(val id: String, val status: String, val note: String, val createdAt: Long, val submittedAt: Long? = null, val items: List<StocktakeItemDto>)
@Serializable data class CreateStocktakeRequest(val id: String = UUID.randomUUID().toString(), val note: String = "")
@Serializable data class UpdateStocktakeItemRequest(val actualQty: Double)

// ── Routes ────────────────────────────────────────────────────────────────────

fun Route.adminInventoryRoutes() {
    authenticate("jwt") {

        // ── Suppliers ─────────────────────────────────────────────────────────

        route("/admin/suppliers") {
            get {
                val list = transaction { SuppliersTable.selectAll().orderBy(SuppliersTable.createdAt, SortOrder.DESC).map { it.toSupplierDto() } }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateSupplierRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    SuppliersTable.insert {
                        it[id] = req.id; it[name] = req.name; it[contact] = req.contact
                        it[phone] = req.phone; it[note] = req.note; it[createdAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val sid = call.parameters["id"]!!
                val req = call.receive<UpdateSupplierRequest>()
                transaction { SuppliersTable.update({ SuppliersTable.id eq sid }) { stmt ->
                    req.name?.let { stmt[name] = it }; req.contact?.let { stmt[contact] = it }
                    req.phone?.let { stmt[phone] = it }; req.note?.let { stmt[note] = it }
                } }
                call.respond(mapOf("updated" to true))
            }
            delete("/{id}") {
                val sid = call.parameters["id"]!!
                transaction { SuppliersTable.deleteWhere { id eq sid } }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── Ingredients ───────────────────────────────────────────────────────

        route("/admin/ingredients") {
            get {
                val list = transaction { IngredientsTable.selectAll().orderBy(IngredientsTable.name).map { it.toIngredientDto() } }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateIngredientRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    IngredientsTable.insert {
                        it[id] = req.id; it[name] = req.name; it[category] = req.category
                        it[unit] = req.unit; it[purchaseUnit] = req.purchaseUnit
                        it[purchaseUnitFactor] = req.purchaseUnitFactor
                        it[safetyStock] = req.safetyStock; it[createdAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{id}") {
                val iid = call.parameters["id"]!!
                val req = call.receive<UpdateIngredientRequest>()
                transaction { IngredientsTable.update({ IngredientsTable.id eq iid }) { stmt ->
                    req.name?.let { stmt[name] = it }; req.category?.let { stmt[category] = it }
                    req.safetyStock?.let { stmt[safetyStock] = it }; req.currentStock?.let { stmt[currentStock] = it }
                } }
                call.respond(mapOf("updated" to true))
            }
            delete("/{id}") {
                val iid = call.parameters["id"]!!
                transaction { IngredientsTable.deleteWhere { id eq iid } }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── Stock Movements ───────────────────────────────────────────────────

        route("/admin/stock-movements") {
            get {
                val ingredientId = call.request.queryParameters["ingredientId"]
                val list = transaction {
                    var q = StockMovementsTable.selectAll()
                    if (!ingredientId.isNullOrBlank()) q = q.where { StockMovementsTable.ingredientId eq ingredientId }
                    q.orderBy(StockMovementsTable.createdAt, SortOrder.DESC).map { it.toMovementDto() }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateStockMovementRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    StockMovementsTable.insert {
                        it[id] = req.id; it[ingredientId] = req.ingredientId
                        it[type] = req.type; it[qty] = req.qty; it[note] = req.note; it[createdAt] = now
                    }
                    val delta = if (req.type == "OUT") -req.qty else req.qty
                    IngredientsTable.update({ IngredientsTable.id eq req.ingredientId }) {
                        with(SqlExpressionBuilder) { it.update(currentStock, currentStock + delta) }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
        }

        // ── Purchase Orders ───────────────────────────────────────────────────

        route("/admin/purchase-orders") {
            get {
                val list = transaction {
                    val orders = PurchaseOrdersTable.selectAll().orderBy(PurchaseOrdersTable.createdAt, SortOrder.DESC).toList()
                    val itemsByOrder = PurchaseOrderItemsTable.selectAll().groupBy { it[PurchaseOrderItemsTable.orderId] }
                    orders.map { row ->
                        val items = itemsByOrder[row[PurchaseOrdersTable.id]] ?: emptyList()
                        row.toPurchaseOrderDto(items.map { it.toPurchaseOrderItemDto() })
                    }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreatePurchaseOrderRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    PurchaseOrdersTable.insert {
                        it[id] = req.id; it[supplier] = req.supplier; it[supplierId] = req.supplierId
                        it[note] = req.note; it[createdAt] = now
                    }
                    req.items.forEach { item ->
                        PurchaseOrderItemsTable.insert {
                            it[id] = UUID.randomUUID().toString(); it[orderId] = req.id
                            it[ingredientId] = item.ingredientId; it[qty] = item.qty; it[unitCost] = item.unitCost
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            post("/{id}/confirm") {
                val oid = call.parameters["id"]!!
                val now = System.currentTimeMillis()
                transaction {
                    PurchaseOrdersTable.update({ PurchaseOrdersTable.id eq oid }) {
                        it[status] = "CONFIRMED"; it[confirmedAt] = now
                    }
                    // Apply stock IN movements
                    PurchaseOrderItemsTable.selectAll().where { PurchaseOrderItemsTable.orderId eq oid }
                        .forEach { row ->
                            val ingId = row[PurchaseOrderItemsTable.ingredientId]
                            val qty = row[PurchaseOrderItemsTable.qty]
                            StockMovementsTable.insert {
                                it[id] = UUID.randomUUID().toString(); it[ingredientId] = ingId
                                it[type] = "IN"; it[this.qty] = qty; it[note] = "PO $oid"; it[createdAt] = now
                            }
                            IngredientsTable.update({ IngredientsTable.id eq ingId }) {
                                with(SqlExpressionBuilder) { it.update(currentStock, currentStock + qty) }
                            }
                        }
                }
                call.respond(mapOf("confirmed" to true))
            }
            delete("/{id}") {
                val oid = call.parameters["id"]!!
                transaction {
                    PurchaseOrderItemsTable.deleteWhere { orderId eq oid }
                    PurchaseOrdersTable.deleteWhere { id eq oid }
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── Outbound Orders ───────────────────────────────────────────────────

        route("/admin/outbound-orders") {
            get {
                val list = transaction {
                    val orders = OutboundOrdersTable.selectAll().orderBy(OutboundOrdersTable.createdAt, SortOrder.DESC).toList()
                    val itemsByOrder = OutboundOrderItemsTable.selectAll().groupBy { it[OutboundOrderItemsTable.orderId] }
                    orders.map { row ->
                        val items = itemsByOrder[row[OutboundOrdersTable.id]] ?: emptyList()
                        row.toOutboundDto(items.map { it.toOutboundItemDto() })
                    }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateOutboundRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    OutboundOrdersTable.insert {
                        it[id] = req.id; it[type] = req.type; it[note] = req.note; it[createdAt] = now
                    }
                    req.items.forEach { item ->
                        OutboundOrderItemsTable.insert {
                            it[id] = UUID.randomUUID().toString(); it[orderId] = req.id
                            it[ingredientId] = item.ingredientId; it[qty] = item.qty
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            post("/{id}/confirm") {
                val oid = call.parameters["id"]!!
                val now = System.currentTimeMillis()
                transaction {
                    OutboundOrdersTable.update({ OutboundOrdersTable.id eq oid }) {
                        it[status] = "CONFIRMED"; it[confirmedAt] = now
                    }
                    OutboundOrderItemsTable.selectAll().where { OutboundOrderItemsTable.orderId eq oid }
                        .forEach { row ->
                            val ingId = row[OutboundOrderItemsTable.ingredientId]
                            val qty = row[OutboundOrderItemsTable.qty]
                            StockMovementsTable.insert {
                                it[id] = UUID.randomUUID().toString(); it[ingredientId] = ingId
                                it[type] = "OUT"; it[this.qty] = qty; it[note] = "Outbound $oid"; it[createdAt] = now
                            }
                            IngredientsTable.update({ IngredientsTable.id eq ingId }) {
                                with(SqlExpressionBuilder) { it.update(currentStock, currentStock - qty) }
                            }
                        }
                }
                call.respond(mapOf("confirmed" to true))
            }
            delete("/{id}") {
                val oid = call.parameters["id"]!!
                transaction {
                    OutboundOrderItemsTable.deleteWhere { orderId eq oid }
                    OutboundOrdersTable.deleteWhere { id eq oid }
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── BOM ───────────────────────────────────────────────────────────────

        route("/admin/bom") {
            get("/{menuItemId}") {
                val mid = call.parameters["menuItemId"]!!
                val lines = transaction {
                    BomLinesTable.selectAll().where { BomLinesTable.menuItemId eq mid }
                        .map { BomLineDto(it[BomLinesTable.id], it[BomLinesTable.menuItemId], it[BomLinesTable.ingredientId], it[BomLinesTable.qty]) }
                }
                call.respond(BomRecipeDto(mid, lines))
            }
            put("/{menuItemId}") {
                val mid = call.parameters["menuItemId"]!!
                val req = call.receive<SaveBomRequest>()
                transaction {
                    BomLinesTable.deleteWhere { menuItemId eq mid }
                    req.lines.forEach { line ->
                        BomLinesTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[menuItemId] = mid; it[ingredientId] = line.ingredientId; it[qty] = line.qty
                        }
                    }
                }
                val saved = transaction {
                    BomLinesTable.selectAll().where { BomLinesTable.menuItemId eq mid }
                        .map { BomLineDto(it[BomLinesTable.id], it[BomLinesTable.menuItemId], it[BomLinesTable.ingredientId], it[BomLinesTable.qty]) }
                }
                call.respond(BomRecipeDto(mid, saved))
            }
        }

        // ── Stocktakes ────────────────────────────────────────────────────────

        route("/admin/stocktakes") {
            get {
                val list = transaction {
                    val orders = StocktakeOrdersTable.selectAll().orderBy(StocktakeOrdersTable.createdAt, SortOrder.DESC).toList()
                    val itemsByOrder = StocktakeItemsTable.selectAll().groupBy { it[StocktakeItemsTable.orderId] }
                    orders.map { row ->
                        val items = itemsByOrder[row[StocktakeOrdersTable.id]] ?: emptyList()
                        row.toStocktakeDto(items.map { it.toStocktakeItemDto() })
                    }
                }
                call.respond(list)
            }
            post {
                val req = call.receive<CreateStocktakeRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    StocktakeOrdersTable.insert { it[id] = req.id; it[note] = req.note; it[createdAt] = now }
                    // Snapshot current stock for all ingredients
                    IngredientsTable.selectAll().forEach { ing ->
                        StocktakeItemsTable.insert {
                            it[id] = UUID.randomUUID().toString(); it[orderId] = req.id
                            it[ingredientId] = ing[IngredientsTable.id]
                            it[systemQty] = ing[IngredientsTable.currentStock]; it[actualQty] = ing[IngredientsTable.currentStock]
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }
            patch("/{orderId}/items/{itemId}") {
                val oid = call.parameters["orderId"]!!
                val iid = call.parameters["itemId"]!!
                val req = call.receive<UpdateStocktakeItemRequest>()
                transaction {
                    StocktakeItemsTable.update({ (StocktakeItemsTable.orderId eq oid) and (StocktakeItemsTable.id eq iid) }) {
                        it[actualQty] = req.actualQty
                    }
                }
                call.respond(mapOf("updated" to true))
            }
            post("/{id}/submit") {
                val oid = call.parameters["id"]!!
                val now = System.currentTimeMillis()
                transaction {
                    // Write ADJUST movements for all items with discrepancy
                    StocktakeItemsTable.selectAll().where { StocktakeItemsTable.orderId eq oid }.forEach { row ->
                        val diff = row[StocktakeItemsTable.actualQty] - row[StocktakeItemsTable.systemQty]
                        if (diff != 0.0) {
                            val ingId = row[StocktakeItemsTable.ingredientId]
                            StockMovementsTable.insert {
                                it[id] = UUID.randomUUID().toString(); it[ingredientId] = ingId
                                it[type] = "ADJUST"; it[qty] = diff; it[note] = "Stocktake $oid"; it[createdAt] = now
                            }
                            IngredientsTable.update({ IngredientsTable.id eq ingId }) {
                                with(SqlExpressionBuilder) { it.update(currentStock, currentStock + diff) }
                            }
                        }
                    }
                    StocktakeOrdersTable.update({ StocktakeOrdersTable.id eq oid }) {
                        it[status] = "SUBMITTED"; it[submittedAt] = now
                    }
                }
                call.respond(mapOf("submitted" to true))
            }
        }

        // ── Inventory Report ──────────────────────────────────────────────────

        get("/admin/inventory/report") {
            val from = call.request.queryParameters["from"]?.toLongOrNull() ?: 0L
            val to = call.request.queryParameters["to"]?.toLongOrNull() ?: System.currentTimeMillis()
            val report = transaction {
                val ingredients = IngredientsTable.selectAll().toList()
                val movements = StockMovementsTable.selectAll()
                    .where { (StockMovementsTable.createdAt greaterEq from) and (StockMovementsTable.createdAt lessEq to) }
                    .toList()
                val byIngredient = movements.groupBy { it[StockMovementsTable.ingredientId] }
                val summaries = ingredients.map { ing ->
                    val ingId = ing[IngredientsTable.id]
                    val mvts = byIngredient[ingId] ?: emptyList()
                    val totalIn = mvts.filter { it[StockMovementsTable.type] == "IN" }.sumOf { it[StockMovementsTable.qty] }
                    val totalOut = mvts.filter { it[StockMovementsTable.type] == "OUT" }.sumOf { it[StockMovementsTable.qty] }
                    val totalAdj = mvts.filter { it[StockMovementsTable.type] == "ADJUST" }.sumOf { it[StockMovementsTable.qty] }
                    mapOf(
                        "ingredientId" to ingId, "name" to ing[IngredientsTable.name],
                        "unit" to ing[IngredientsTable.unit], "totalIn" to totalIn,
                        "totalOut" to totalOut, "totalAdjust" to totalAdj,
                        "closingStock" to ing[IngredientsTable.currentStock],
                        "safetyStock" to ing[IngredientsTable.safetyStock],
                    )
                }
                val lowStockCount = ingredients.count { it[IngredientsTable.currentStock] < it[IngredientsTable.safetyStock] }
                mapOf("from" to from, "to" to to, "movements" to summaries, "lowStockCount" to lowStockCount)
            }
            call.respond(report)
        }
    }
}

// ── Row mappers ───────────────────────────────────────────────────────────────

private fun ResultRow.toSupplierDto() = SupplierDto(this[SuppliersTable.id], this[SuppliersTable.name], this[SuppliersTable.contact], this[SuppliersTable.phone], this[SuppliersTable.note], this[SuppliersTable.createdAt])
private fun ResultRow.toIngredientDto() = IngredientDto(this[IngredientsTable.id], this[IngredientsTable.name], this[IngredientsTable.category], this[IngredientsTable.unit], this[IngredientsTable.purchaseUnit], this[IngredientsTable.purchaseUnitFactor], this[IngredientsTable.safetyStock], this[IngredientsTable.currentStock], this[IngredientsTable.createdAt])
private fun ResultRow.toMovementDto() = StockMovementDto(this[StockMovementsTable.id], this[StockMovementsTable.ingredientId], this[StockMovementsTable.type], this[StockMovementsTable.qty], this[StockMovementsTable.note], this[StockMovementsTable.createdAt])
private fun ResultRow.toPurchaseOrderItemDto() = PurchaseOrderItemDto(this[PurchaseOrderItemsTable.id], this[PurchaseOrderItemsTable.orderId], this[PurchaseOrderItemsTable.ingredientId], this[PurchaseOrderItemsTable.qty], this[PurchaseOrderItemsTable.unitCost])
private fun ResultRow.toPurchaseOrderDto(items: List<PurchaseOrderItemDto>) = PurchaseOrderDto(this[PurchaseOrdersTable.id], this[PurchaseOrdersTable.status], this[PurchaseOrdersTable.supplier], this[PurchaseOrdersTable.supplierId], this[PurchaseOrdersTable.note], this[PurchaseOrdersTable.createdAt], this[PurchaseOrdersTable.confirmedAt], items)
private fun ResultRow.toOutboundItemDto() = OutboundItemDto(this[OutboundOrderItemsTable.id], this[OutboundOrderItemsTable.orderId], this[OutboundOrderItemsTable.ingredientId], this[OutboundOrderItemsTable.qty])
private fun ResultRow.toOutboundDto(items: List<OutboundItemDto>) = OutboundOrderDto(this[OutboundOrdersTable.id], this[OutboundOrdersTable.status], this[OutboundOrdersTable.type], this[OutboundOrdersTable.note], this[OutboundOrdersTable.createdAt], this[OutboundOrdersTable.confirmedAt], items)
private fun ResultRow.toStocktakeItemDto() = StocktakeItemDto(this[StocktakeItemsTable.id], this[StocktakeItemsTable.orderId], this[StocktakeItemsTable.ingredientId], this[StocktakeItemsTable.systemQty], this[StocktakeItemsTable.actualQty])
private fun ResultRow.toStocktakeDto(items: List<StocktakeItemDto>) = StocktakeOrderDto(this[StocktakeOrdersTable.id], this[StocktakeOrdersTable.status], this[StocktakeOrdersTable.note], this[StocktakeOrdersTable.createdAt], this[StocktakeOrdersTable.submittedAt], items)
