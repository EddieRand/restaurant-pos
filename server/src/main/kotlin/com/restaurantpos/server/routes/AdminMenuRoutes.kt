package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminMenuRoutes() {
    authenticate("jwt") {
        route("/admin/menu") {

            get {
                val items = transaction {
                    MenuItemsTable.selectAll()
                        .orderBy(MenuItemsTable.categoryId to SortOrder.ASC, MenuItemsTable.course to SortOrder.ASC)
                        .map { it.toMenuItemDto() }
                }
                call.respond(items)
            }

            post {
                val req = call.receive<CreateMenuItemRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    MenuItemsTable.insert {
                        it[id]                = req.id
                        it[names]             = req.names
                        it[priceMinorUnit]    = req.priceMinorUnit
                        it[taxRateId]         = req.taxRateId
                        it[categoryId]        = req.categoryId
                        it[course]            = req.course
                        it[isSoldOut]         = false
                        it[imageUrl]          = req.imageUrl
                        it[allergens]         = req.allergens
                        it[availableChannels] = req.availableChannels.joinToString("|")
                        it[stockCount]        = req.stockCount
                        it[updatedAt]         = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            route("/{id}") {
                patch {
                    val itemId = call.parameters["id"]!!
                    val req = call.receive<UpdateMenuItemRequest>()
                    val now = System.currentTimeMillis()
                    val updated = transaction {
                        MenuItemsTable.update({ MenuItemsTable.id eq itemId }) { stmt ->
                            req.names?.let { stmt[names] = it }
                            req.priceMinorUnit?.let { stmt[priceMinorUnit] = it }
                            req.taxRateId?.let { stmt[taxRateId] = it }
                            req.categoryId?.let { stmt[categoryId] = it }
                            req.course?.let { stmt[course] = it }
                            req.isSoldOut?.let { stmt[isSoldOut] = it }
                            req.imageUrl?.let { stmt[imageUrl] = it }
                            req.allergens?.let { stmt[allergens] = it }
                            req.availableChannels?.let { stmt[availableChannels] = it.joinToString("|") }
                            req.stockCount?.let { stmt[stockCount] = it }
                            stmt[updatedAt] = now
                        }
                    }
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu item not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val itemId = call.parameters["id"]!!
                    val deleted = transaction { MenuItemsTable.deleteWhere { id eq itemId } }
                    if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu item not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }

            post("/bulk-availability") {
                val req = call.receive<BulkAvailabilityRequest>()
                val now = System.currentTimeMillis()
                val count = transaction {
                    MenuItemsTable.update({ MenuItemsTable.id inList req.ids }) {
                        it[isSoldOut] = req.isSoldOut
                        it[updatedAt] = now
                    }
                }
                call.respond(mapOf("updatedCount" to count))
            }
        }
    }
}

internal fun ResultRow.toMenuItemDto() = MenuItemDto(
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
