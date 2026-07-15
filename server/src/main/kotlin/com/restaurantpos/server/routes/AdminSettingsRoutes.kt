package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.model.ErrorResponse
import com.restaurantpos.server.model.SettingDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Route.adminSettingsRoutes() {
    authenticate("jwt") {
        route("/admin/settings") {

            get {
                val settings = transaction {
                    SettingsTable.selectAll().map { SettingDto(it[SettingsTable.key], it[SettingsTable.value]) }
                }
                call.respond(settings)
            }

            get("/{key}") {
                val key = canonicalSettingKey(call.parameters["key"]!!)
                val setting = transaction {
                    val row = SettingsTable.selectAll()
                        .where { SettingsTable.key eq key }
                        .firstOrNull()
                        ?: legacySettingKey(key)?.let { legacyKey ->
                            SettingsTable.selectAll()
                                .where { SettingsTable.key eq legacyKey }
                                .firstOrNull()
                        }
                    row?.let { SettingDto(it[SettingsTable.key], it[SettingsTable.value]) }
                }
                if (setting == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Setting not found"))
                else call.respond(setting.copy(key = key))
            }

            put("/{key}") {
                val key = canonicalSettingKey(call.parameters["key"]!!)
                val body = call.receive<SettingDto>()
                transaction {
                    val existing = SettingsTable.selectAll().where { SettingsTable.key eq key }.firstOrNull()
                    if (existing != null) {
                        SettingsTable.update({ SettingsTable.key eq key }) { it[value] = body.value }
                    } else {
                        SettingsTable.insert { it[SettingsTable.key] = key; it[value] = body.value }
                    }
                }
                call.respond(mapOf("key" to key))
            }

            delete("/{key}") {
                val key = canonicalSettingKey(call.parameters["key"]!!)
                val deleted = transaction { SettingsTable.deleteWhere { SettingsTable.key eq key } }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Setting not found"))
                else call.respond(mapOf("deleted" to true))
            }
        }
    }
}

private fun canonicalSettingKey(key: String): String =
    if (key == "region-config") "regionConfig" else key

private fun legacySettingKey(key: String): String? =
    if (key == "regionConfig") "region-config" else null
