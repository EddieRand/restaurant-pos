package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.ComboComponentsTable
import com.restaurantpos.server.db.tables.CombosTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminComboRoutes() {
    authenticate("jwt") {
        route("/admin/combos") {

            get {
                val combos = transaction {
                    val comboRows = CombosTable.selectAll().toList()
                    val allComponents = ComboComponentsTable.selectAll().toList()
                    comboRows.map { row ->
                        val components = allComponents
                            .filter { it[ComboComponentsTable.comboId] == row[CombosTable.id] }
                            .sortedBy { it[ComboComponentsTable.sortOrder] }
                            .map { it.toComboComponentDto() }
                        row.toComboDto(components)
                    }
                }
                call.respond(combos)
            }

            post {
                val req = call.receive<CreateComboRequest>()
                transaction {
                    CombosTable.insert {
                        it[id] = req.id
                        it[names] = req.names
                        it[comboPriceMinorUnit] = req.comboPriceMinorUnit
                        it[taxRateId] = req.taxRateId
                        it[isActive] = true
                    }
                    req.components.forEach { comp ->
                        ComboComponentsTable.insert {
                            it[id] = comp.id
                            it[comboId] = req.id
                            it[menuItemId] = comp.menuItemId
                            it[quantity] = comp.quantity
                            it[sortOrder] = comp.sortOrder
                        }
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            patch("/{id}") {
                val comboId = call.parameters["id"]!!
                val req = call.receive<UpdateComboRequest>()
                val updated = transaction {
                    val count = CombosTable.update({ CombosTable.id eq comboId }) { stmt ->
                        req.names?.let { stmt[names] = it }
                        req.comboPriceMinorUnit?.let { stmt[comboPriceMinorUnit] = it }
                        req.taxRateId?.let { stmt[taxRateId] = it }
                        req.isActive?.let { stmt[isActive] = it }
                    }
                    // Replace components when provided
                    req.components?.let { newComponents ->
                        ComboComponentsTable.deleteWhere { ComboComponentsTable.comboId eq comboId }
                        newComponents.forEach { comp ->
                            ComboComponentsTable.insert {
                                it[id] = comp.id
                                it[ComboComponentsTable.comboId] = comboId
                                it[menuItemId] = comp.menuItemId
                                it[quantity] = comp.quantity
                                it[sortOrder] = comp.sortOrder
                            }
                        }
                    }
                    count
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Combo not found"))
                else call.respond(mapOf("updated" to true))
            }
        }
    }
}

private fun ResultRow.toComboDto(components: List<ComboComponentDto>) = ComboDto(
    id = this[CombosTable.id],
    names = this[CombosTable.names],
    comboPriceMinorUnit = this[CombosTable.comboPriceMinorUnit],
    taxRateId = this[CombosTable.taxRateId],
    isActive = this[CombosTable.isActive],
    components = components,
)

private fun ResultRow.toComboComponentDto() = ComboComponentDto(
    id = this[ComboComponentsTable.id],
    comboId = this[ComboComponentsTable.comboId],
    menuItemId = this[ComboComponentsTable.menuItemId],
    quantity = this[ComboComponentsTable.quantity],
    sortOrder = this[ComboComponentsTable.sortOrder],
)
