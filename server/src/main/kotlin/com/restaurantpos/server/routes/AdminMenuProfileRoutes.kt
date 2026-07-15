package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.MenuItemProfilesTable
import com.restaurantpos.server.db.tables.MenuProfilesTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminMenuProfileRoutes() {
    authenticate("jwt") {
        route("/admin/menu-profiles") {

            get {
                val profiles = transaction {
                    MenuProfilesTable.selectAll()
                        .orderBy(MenuProfilesTable.updatedAt to SortOrder.DESC)
                        .map { it.toMenuProfileDto() }
                }
                call.respond(profiles)
            }

            post {
                val req = call.receive<CreateMenuProfileRequest>()
                val now = System.currentTimeMillis()
                transaction {
                    MenuProfilesTable.insert {
                        it[id] = req.id
                        it[name] = req.name
                        it[enabled] = req.enabled
                        it[startTime] = req.startTime
                        it[endTime] = req.endTime
                        it[daysOfWeek] = req.daysOfWeek?.joinToString(",")
                        it[updatedAt] = now
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            route("/{id}") {
                patch {
                    val profileId = call.parameters["id"]!!
                    val req = call.receive<UpdateMenuProfileRequest>()
                    val now = System.currentTimeMillis()
                    val updated = transaction {
                        MenuProfilesTable.update({ MenuProfilesTable.id eq profileId }) { stmt ->
                            req.name?.let { stmt[name] = it }
                            req.enabled?.let { stmt[enabled] = it }
                            if (req.startTime != null) stmt[startTime] = req.startTime
                            if (req.endTime != null)   stmt[endTime]   = req.endTime
                            req.daysOfWeek?.let { stmt[daysOfWeek] = it.joinToString(",").ifEmpty { null } }
                            stmt[updatedAt] = now
                        }
                    }
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu profile not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val profileId = call.parameters["id"]!!
                    // Join rows are removed by CASCADE on MenuItemProfilesTable
                    val deleted = transaction { MenuProfilesTable.deleteWhere { id eq profileId } }
                    if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Menu profile not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }

                // GET /admin/menu-profiles/{id}/items → list of menuItemIds
                get("/items") {
                    val profileId = call.parameters["id"]!!
                    val itemIds = transaction {
                        MenuItemProfilesTable.selectAll()
                            .where { MenuItemProfilesTable.menuProfileId eq profileId }
                            .map { it[MenuItemProfilesTable.menuItemId] }
                    }
                    call.respond(itemIds)
                }

                // PUT /admin/menu-profiles/{id}/items → full replacement of item assignment
                put("/items") {
                    val profileId = call.parameters["id"]!!
                    val req = call.receive<SetProfileItemsRequest>()
                    transaction {
                        MenuItemProfilesTable.deleteWhere { menuProfileId eq profileId }
                        if (req.menuItemIds.isNotEmpty()) {
                            MenuItemProfilesTable.batchInsert(req.menuItemIds, ignore = true) { itemId ->
                                this[MenuItemProfilesTable.menuItemId] = itemId
                                this[MenuItemProfilesTable.menuProfileId] = profileId
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("assigned" to req.menuItemIds.size))
                }
            }
        }
    }
}

private fun ResultRow.toMenuProfileDto() = MenuProfileDto(
    id         = this[MenuProfilesTable.id],
    name       = this[MenuProfilesTable.name],
    enabled    = this[MenuProfilesTable.enabled],
    startTime  = this[MenuProfilesTable.startTime],
    endTime    = this[MenuProfilesTable.endTime],
    daysOfWeek = this[MenuProfilesTable.daysOfWeek]
        ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() },
    updatedAt  = this[MenuProfilesTable.updatedAt],
)
