package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.menu.MenuCommandService
import com.restaurantpos.server.menu.isSoldOutOnlyMutation
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminMenuRoutes(commandService: MenuCommandService = MenuCommandService()) {
    authenticate("jwt") {
        route("/admin/menu") {

            get {
                if (!call.requirePermission("menu.view")) return@get
                val items = transaction {
                    MenuItemsTable.selectAll()
                        .orderBy(MenuItemsTable.categoryId to SortOrder.ASC, MenuItemsTable.course to SortOrder.ASC)
                        .map { it.toMenuItemDto() }
                }
                call.respond(items)
            }

            post {
                if (!call.requirePermission("menu.edit")) return@post
                val req = call.receive<CreateMenuItemRequest>()
                commandService.create(req)
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            route("/{id}") {
                patch {
                    val itemId = call.parameters["id"]!!
                    val req = call.receive<UpdateMenuItemRequest>()
                    val permission = if (req.isSoldOutOnlyMutation()) "menu.sold_out" else "menu.edit"
                    if (!call.requirePermission(permission)) return@patch
                    val updated = commandService.update(itemId, req)
                    if (!updated) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu item not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    if (!call.requirePermission("menu.edit")) return@delete
                    val itemId = call.parameters["id"]!!
                    val deleted = commandService.delete(itemId)
                    if (!deleted) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu item not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }

            post("/bulk-availability") {
                if (!call.requirePermission("menu.sold_out")) return@post
                val req = call.receive<BulkAvailabilityRequest>()
                val count = commandService.setAvailability(req.ids, req.isSoldOut)
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
