package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.MenuCategoriesTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Route.adminMenuCategoryRoutes() {
    authenticate("jwt") {
        route("/admin/menu-categories") {

            get {
                val categories = transaction {
                    MenuCategoriesTable.selectAll()
                        .orderBy(MenuCategoriesTable.sortOrder to SortOrder.ASC)
                        .map { it.toCategoryDto() }
                }
                call.respond(categories)
            }

            post {
                val req = call.receive<CreateMenuCategoryRequest>()
                val id = UUID.randomUUID().toString()
                transaction {
                    MenuCategoriesTable.insert {
                        it[MenuCategoriesTable.id]         = id
                        it[name]       = req.name
                        it[sortOrder]  = req.sortOrder
                        it[activeFrom] = req.activeFrom
                        it[activeTo]   = req.activeTo
                        it[daysOfWeek] = req.daysOfWeek?.joinToString(",")
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            route("/{id}") {
                patch {
                    val catId = call.parameters["id"]!!
                    val req = call.receive<UpdateMenuCategoryRequest>()
                    val updated = transaction {
                        MenuCategoriesTable.update({ MenuCategoriesTable.id eq catId }) { stmt ->
                            req.name?.let { stmt[name] = it }
                            req.sortOrder?.let { stmt[sortOrder] = it }
                            if (req.activeFrom != null) stmt[activeFrom] = req.activeFrom
                            if (req.activeTo != null)   stmt[activeTo]   = req.activeTo
                            req.daysOfWeek?.let { stmt[daysOfWeek] = it.joinToString(",").ifEmpty { null } }
                        }
                    }
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Category not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val catId = call.parameters["id"]!!
                    val deleted = transaction { MenuCategoriesTable.deleteWhere { id eq catId } }
                    if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Category not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }
        }
    }
}

private fun ResultRow.toCategoryDto() = MenuCategoryDto(
    id         = this[MenuCategoriesTable.id],
    name       = this[MenuCategoriesTable.name],
    sortOrder  = this[MenuCategoriesTable.sortOrder],
    activeFrom = this[MenuCategoriesTable.activeFrom],
    activeTo   = this[MenuCategoriesTable.activeTo],
    daysOfWeek = this[MenuCategoriesTable.daysOfWeek]
        ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() },
)
