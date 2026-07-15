package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.FloorSectionsTable
import com.restaurantpos.server.db.tables.TablesTable
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

fun Route.adminTableRoutes() {
    authenticate("jwt") {
        route("/admin/sections") {

            get {
                val sections = transaction {
                    FloorSectionsTable.selectAll()
                        .orderBy(FloorSectionsTable.sortOrder to SortOrder.ASC)
                        .map { it.toSectionDto() }
                }
                call.respond(sections)
            }

            post {
                val req = call.receive<CreateSectionRequest>()
                val id = UUID.randomUUID().toString()
                transaction {
                    FloorSectionsTable.insert {
                        it[FloorSectionsTable.id] = id
                        it[name] = req.name
                        it[sortOrder] = req.sortOrder
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            patch("/{id}") {
                val sectionId = call.parameters["id"]!!
                val req = call.receive<UpdateSectionRequest>()
                val updated = transaction {
                    FloorSectionsTable.update({ FloorSectionsTable.id eq sectionId }) { stmt ->
                        req.name?.let { stmt[name] = it }
                        req.sortOrder?.let { stmt[sortOrder] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Section not found"))
                else call.respond(mapOf("updated" to true))
            }

            delete("/{id}") {
                val sectionId = call.parameters["id"]!!
                val deleted = transaction { FloorSectionsTable.deleteWhere { id eq sectionId } }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Section not found"))
                else call.respond(mapOf("deleted" to true))
            }
        }

        route("/admin/tables") {

            get {
                val tables = transaction {
                    TablesTable.selectAll()
                        .orderBy(TablesTable.sectionId to SortOrder.ASC, TablesTable.name to SortOrder.ASC)
                        .map { it.toTableConfigDto() }
                }
                call.respond(tables)
            }

            post {
                val req = call.receive<CreateTableConfigRequest>()
                val id = UUID.randomUUID().toString()
                transaction {
                    TablesTable.insert {
                        it[TablesTable.id] = id
                        it[name] = req.name
                        it[sectionId] = req.sectionId
                        it[capacity] = req.capacity
                        it[shape] = req.shape
                        it[x] = req.x
                        it[y] = req.y
                        it[w] = req.w
                        it[h] = req.h
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }

            patch("/{id}") {
                val tableId = call.parameters["id"]!!
                val req = call.receive<UpdateTableConfigRequest>()
                val updated = transaction {
                    TablesTable.update({ TablesTable.id eq tableId }) { stmt ->
                        req.name?.let { stmt[name] = it }
                        req.sectionId?.let { stmt[sectionId] = it }
                        req.capacity?.let { stmt[capacity] = it }
                        req.shape?.let { stmt[shape] = it }
                        req.x?.let { stmt[x] = it }
                        req.y?.let { stmt[y] = it }
                        req.w?.let { stmt[w] = it }
                        req.h?.let { stmt[h] = it }
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Table not found"))
                else call.respond(mapOf("updated" to true))
            }

            delete("/{id}") {
                val tableId = call.parameters["id"]!!
                val deleted = transaction { TablesTable.deleteWhere { id eq tableId } }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Table not found"))
                else call.respond(mapOf("deleted" to true))
            }
        }
    }
}

private fun ResultRow.toSectionDto() = SectionDto(
    id = this[FloorSectionsTable.id],
    name = this[FloorSectionsTable.name],
    sortOrder = this[FloorSectionsTable.sortOrder],
)

private fun ResultRow.toTableConfigDto() = TableConfigDto(
    id = this[TablesTable.id],
    name = this[TablesTable.name],
    sectionId = this[TablesTable.sectionId],
    capacity = this[TablesTable.capacity],
    shape = this[TablesTable.shape],
    x = this[TablesTable.x],
    y = this[TablesTable.y],
    w = this[TablesTable.w],
    h = this[TablesTable.h],
)
