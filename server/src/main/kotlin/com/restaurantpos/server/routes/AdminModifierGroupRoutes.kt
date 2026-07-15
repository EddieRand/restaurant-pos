package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.ModifierGroupsTable
import com.restaurantpos.server.db.tables.ModifierOptionsTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminModifierGroupRoutes() {
    authenticate("jwt") {
        route("/admin/modifier-groups") {

            get {
                val groups = transaction { fetchAllGroups() }
                call.respond(groups)
            }

            post {
                val req = call.receive<CreateModifierGroupRequest>()
                transaction {
                    ModifierGroupsTable.insert {
                        it[id]            = req.id
                        it[name]          = req.name
                        it[selectionType] = req.selectionType
                        it[required]      = req.required
                        it[minSelect]     = req.minSelect
                        it[maxSelect]     = req.maxSelect
                    }
                    req.options.forEach { opt ->
                        ModifierOptionsTable.insert {
                            it[id]                   = opt.id
                            it[groupId]              = req.id
                            it[name]                 = opt.name
                            it[priceAdjustMinorUnit] = opt.priceAdjustMinorUnit
                            it[isDefault]            = opt.isDefault
                            it[sortOrder]            = opt.sortOrder
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            route("/{id}") {
                patch {
                    val groupId = call.parameters["id"]!!
                    val req = call.receive<UpdateModifierGroupRequest>()
                    val updated = transaction {
                        val rows = ModifierGroupsTable.update({ ModifierGroupsTable.id eq groupId }) { stmt ->
                            req.name?.let { stmt[name] = it }
                            req.selectionType?.let { stmt[selectionType] = it }
                            req.required?.let { stmt[required] = it }
                            req.minSelect?.let { stmt[minSelect] = it }
                            req.maxSelect?.let { stmt[maxSelect] = it }
                        }
                        req.options?.let { opts ->
                            ModifierOptionsTable.deleteWhere { ModifierOptionsTable.groupId eq groupId }
                            opts.forEach { opt ->
                                ModifierOptionsTable.insert {
                                    it[id]                   = opt.id
                                    it[ModifierOptionsTable.groupId] = groupId
                                    it[name]                 = opt.name
                                    it[priceAdjustMinorUnit] = opt.priceAdjustMinorUnit
                                    it[isDefault]            = opt.isDefault
                                    it[sortOrder]            = opt.sortOrder
                                }
                            }
                        }
                        rows
                    }
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Modifier group not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val groupId = call.parameters["id"]!!
                    val deleted = transaction {
                        ModifierOptionsTable.deleteWhere { ModifierOptionsTable.groupId eq groupId }
                        ModifierGroupsTable.deleteWhere { id eq groupId }
                    }
                    if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Modifier group not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }
        }
    }
}

private fun fetchAllGroups(): List<ModifierGroupDto> {
    val optionsByGroup = ModifierOptionsTable.selectAll()
        .orderBy(ModifierOptionsTable.sortOrder to SortOrder.ASC)
        .groupBy { it[ModifierOptionsTable.groupId] }
        .mapValues { (_, rows) ->
            rows.map { row ->
                ModifierOptionDto(
                    id                   = row[ModifierOptionsTable.id],
                    name                 = row[ModifierOptionsTable.name],
                    priceAdjustMinorUnit = row[ModifierOptionsTable.priceAdjustMinorUnit],
                    isDefault            = row[ModifierOptionsTable.isDefault],
                    sortOrder            = row[ModifierOptionsTable.sortOrder],
                )
            }
        }

    return ModifierGroupsTable.selectAll()
        .orderBy(ModifierGroupsTable.id to SortOrder.ASC)
        .map { row ->
            ModifierGroupDto(
                id            = row[ModifierGroupsTable.id],
                name          = row[ModifierGroupsTable.name],
                selectionType = row[ModifierGroupsTable.selectionType],
                required      = row[ModifierGroupsTable.required],
                minSelect     = row[ModifierGroupsTable.minSelect],
                maxSelect     = row[ModifierGroupsTable.maxSelect],
                options       = optionsByGroup[row[ModifierGroupsTable.id]] ?: emptyList(),
            )
        }
}
