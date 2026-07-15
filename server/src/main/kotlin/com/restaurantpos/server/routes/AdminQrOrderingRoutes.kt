package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.QrCodesTable
import com.restaurantpos.server.db.tables.SettingsTable
import com.restaurantpos.server.db.tables.TablesTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val adminQrJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val qrScopes = setOf("MENU", "TABLE", "PICKUP", "DELIVERY")

fun Route.adminQrOrderingRoutes() {
    authenticate("jwt") {
        route("/admin/qr-ordering") {
            get("/config") {
                val config = transaction { readQrOrderingConfig() }
                call.respond(config)
            }

            put("/config") {
                val config = call.receive<QrOrderingConfigDto>()
                transaction {
                    val value = adminQrJson.encodeToString(config)
                    val existing = SettingsTable.selectAll()
                        .where { SettingsTable.key eq "qrOrderingConfig" }
                        .firstOrNull()
                    if (existing == null) {
                        SettingsTable.insert {
                            it[key] = "qrOrderingConfig"
                            it[SettingsTable.value] = value
                        }
                    } else {
                        SettingsTable.update({ SettingsTable.key eq "qrOrderingConfig" }) {
                            it[SettingsTable.value] = value
                        }
                    }
                }
                call.respond(config)
            }

            get("/codes") {
                val codes = transaction {
                    QrCodesTable.selectAll()
                        .orderBy(QrCodesTable.updatedAt to SortOrder.DESC)
                        .map { it.toQrCodeDto() }
                }
                call.respond(codes)
            }

            get("/table-bindings") {
                val origin = call.adminOrigin()
                val response = transaction { buildTableQrBindings(origin) }
                call.respond(response)
            }

            post("/codes") {
                val req = call.receive<CreateQrCodeRequest>()
                transaction { validateQrCode(req.code, req.scope, req.tableId) }
                val now = System.currentTimeMillis()
                val dto = transaction {
                    QrCodesTable.insert {
                        it[code] = req.code
                        it[scope] = req.scope.uppercase()
                        it[tableId] = req.tableId?.takeIf { v -> v.isNotBlank() }
                        it[enabled] = req.enabled
                        it[expiresAt] = req.expiresAt
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    QrCodeDto(req.code, req.scope.uppercase(), req.tableId, req.enabled, req.expiresAt, now, now)
                }
                call.respond(HttpStatusCode.Created, dto)
            }

            patch("/codes/{code}") {
                val code = call.parameters["code"]!!
                val req = call.receive<UpdateQrCodeRequest>()
                val updated = transaction {
                    val existing = QrCodesTable.selectAll().where { QrCodesTable.code eq code }.firstOrNull()
                        ?: return@transaction null
                    val nextScope = (req.scope ?: existing[QrCodesTable.scope]).uppercase()
                    val nextTableId = req.tableId ?: existing[QrCodesTable.tableId]
                    validateQrScope(nextScope, nextTableId)
                    QrCodesTable.update({ QrCodesTable.code eq code }) {
                        it[scope] = nextScope
                        it[tableId] = nextTableId?.takeIf { v -> v.isNotBlank() }
                        req.enabled?.let { enabled -> it[QrCodesTable.enabled] = enabled }
                        it[expiresAt] = req.expiresAt
                        it[updatedAt] = System.currentTimeMillis()
                    }
                    QrCodesTable.selectAll().where { QrCodesTable.code eq code }.first().toQrCodeDto()
                }
                if (updated == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("QR code not found"))
                else call.respond(updated)
            }

            post("/tables/{tableId}/qr") {
                val tableId = call.parameters["tableId"]!!
                val origin = call.adminOrigin()
                transaction {
                    requireTableExists(tableId)
                    findCurrentTableQr(tableId)?.toQrCodeDto()
                        ?: restoreOrCreateTableQr(tableId)
                }
                call.respond(transaction { tableBindingForTable(tableId, origin) })
            }

            post("/tables/{tableId}/qr/reset") {
                val tableId = call.parameters["tableId"]!!
                val origin = call.adminOrigin()
                transaction {
                    requireTableExists(tableId)
                    disableTableCodes(tableId)
                    createTableQr(tableId)
                }
                call.respond(transaction { tableBindingForTable(tableId, origin) })
            }

            post("/codes/{code}/rebind") {
                val code = call.parameters["code"]!!
                val req = call.receive<RebindQrCodeRequest>()
                val origin = call.adminOrigin()
                val updated = transaction {
                    requireTableExists(req.tableId)
                    val existing = QrCodesTable.selectAll().where { QrCodesTable.code eq code }.firstOrNull()
                        ?: return@transaction false
                    val now = System.currentTimeMillis()
                    disableTableCodes(req.tableId, exceptCode = code)
                    QrCodesTable.update({ QrCodesTable.code eq existing[QrCodesTable.code] }) {
                        it[scope] = "TABLE"
                        it[tableId] = req.tableId
                        it[enabled] = true
                        it[expiresAt] = null
                        it[updatedAt] = now
                    }
                    true
                }
                if (!updated) call.respond(HttpStatusCode.NotFound, ErrorResponse("QR code not found"))
                else call.respond(transaction { tableBindingForTable(req.tableId, origin) })
            }

            delete("/codes/{code}") {
                val code = call.parameters["code"]!!
                val deleted = transaction { QrCodesTable.deleteWhere { QrCodesTable.code eq code } }
                if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("QR code not found"))
                else call.respond(mapOf("deleted" to true))
            }
        }
    }
}

private fun readQrOrderingConfig(): QrOrderingConfigDto {
    val raw = SettingsTable.selectAll().where { SettingsTable.key eq "qrOrderingConfig" }.firstOrNull()
        ?.get(SettingsTable.value)
    return raw?.let { runCatching { adminQrJson.decodeFromString<QrOrderingConfigDto>(it) }.getOrNull() }
        ?: QrOrderingConfigDto()
}

private fun validateQrCode(code: String, scope: String, tableId: String?) {
    require(code.matches(Regex("[A-Za-z0-9._:-]{3,96}"))) { "Invalid QR code" }
    validateQrScope(scope, tableId)
}

private fun validateQrScope(scope: String, tableId: String?) {
    val normalized = scope.uppercase()
    require(qrScopes.contains(normalized)) { "Unsupported QR scope" }
    if (normalized == "TABLE") {
        require(!tableId.isNullOrBlank()) { "tableId is required for table QR" }
        requireTableExists(tableId)
    }
}

private fun requireTableExists(tableId: String) {
    val exists = TablesTable.selectAll().where { TablesTable.id eq tableId }.count() > 0
    require(exists) { "Table not found" }
}

private fun buildTableQrBindings(origin: String): TableQrBindingResponse {
    val tableRows = TablesTable.selectAll()
        .orderBy(TablesTable.sectionId to SortOrder.ASC, TablesTable.name to SortOrder.ASC)
        .toList()
    val qrRows = QrCodesTable.selectAll()
        .where { QrCodesTable.scope eq "TABLE" }
        .orderBy(QrCodesTable.updatedAt to SortOrder.DESC)
        .toList()
    val knownTableIds = tableRows.map { it[TablesTable.id] }.toSet()
    val sections = tableRows
        .groupBy { it[TablesTable.sectionId] }
        .map { (sectionId, rows) ->
            val sectionName = readableSectionName(sectionId)
            TableQrSectionDto(
                id = sectionId,
                name = sectionName,
                tables = rows.map { row ->
                    val tableId = row[TablesTable.id]
                    val tableCodes = qrRows.filter { it[QrCodesTable.tableId] == tableId }
                    val current = tableCodes.firstOrNull { it[QrCodesTable.enabled] }
                    TableQrBindingDto(
                        tableId = tableId,
                        tableName = row[TablesTable.name],
                        sectionId = sectionId,
                        sectionName = sectionName,
                        capacity = row[TablesTable.capacity],
                        status = row[TablesTable.status],
                        currentQr = current?.toQrCodeDto(),
                        activeCodeCount = tableCodes.count { it[QrCodesTable.enabled] },
                        disabledCodeCount = tableCodes.count { !it[QrCodesTable.enabled] },
                        customerUrl = current?.let { customerUrl(origin, it[QrCodesTable.code]) },
                    )
                },
            )
        }
    val unassigned = qrRows
        .filter { it[QrCodesTable.tableId].isNullOrBlank() || !knownTableIds.contains(it[QrCodesTable.tableId]) }
        .map { it.toQrCodeDto() }
    return TableQrBindingResponse(sections = sections, unassignedCodes = unassigned)
}

private fun tableBindingForTable(tableId: String, origin: String): TableQrBindingDto {
    return buildTableQrBindings(origin).sections
        .flatMap { it.tables }
        .first { it.tableId == tableId }
}

private fun findCurrentTableQr(tableId: String): ResultRow? =
    QrCodesTable.selectAll()
        .where { (QrCodesTable.scope eq "TABLE") and (QrCodesTable.tableId eq tableId) and (QrCodesTable.enabled eq true) }
        .orderBy(QrCodesTable.updatedAt to SortOrder.DESC)
        .firstOrNull()

private fun restoreOrCreateTableQr(tableId: String): QrCodeDto {
    val disabled = QrCodesTable.selectAll()
        .where { (QrCodesTable.scope eq "TABLE") and (QrCodesTable.tableId eq tableId) and (QrCodesTable.enabled eq false) }
        .orderBy(QrCodesTable.updatedAt to SortOrder.DESC)
        .firstOrNull()
    if (disabled != null) {
        val now = System.currentTimeMillis()
        QrCodesTable.update({ QrCodesTable.code eq disabled[QrCodesTable.code] }) {
            it[enabled] = true
            it[expiresAt] = null
            it[updatedAt] = now
        }
        return QrCodesTable.selectAll().where { QrCodesTable.code eq disabled[QrCodesTable.code] }.first().toQrCodeDto()
    }
    return createTableQr(tableId)
}

private fun createTableQr(tableId: String): QrCodeDto {
    val now = System.currentTimeMillis()
    val code = nextTableCode(tableId)
    QrCodesTable.insert {
        it[QrCodesTable.code] = code
        it[scope] = "TABLE"
        it[QrCodesTable.tableId] = tableId
        it[enabled] = true
        it[expiresAt] = null
        it[createdAt] = now
        it[updatedAt] = now
    }
    return QrCodeDto(code, "TABLE", tableId, true, null, now, now)
}

private fun disableTableCodes(tableId: String, exceptCode: String? = null) {
    val now = System.currentTimeMillis()
    QrCodesTable.selectAll()
        .where { (QrCodesTable.scope eq "TABLE") and (QrCodesTable.tableId eq tableId) }
        .filter { exceptCode == null || it[QrCodesTable.code] != exceptCode }
        .forEach { row ->
            QrCodesTable.update({ QrCodesTable.code eq row[QrCodesTable.code] }) {
                it[enabled] = false
                it[updatedAt] = now
            }
        }
}

private fun nextTableCode(tableId: String): String {
    val slug = tableId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "table" }
    repeat(100) {
        val suffix = java.util.UUID.randomUUID().toString().take(8)
        val candidate = "table-$slug-$suffix"
        val exists = QrCodesTable.selectAll().where { QrCodesTable.code eq candidate }.count() > 0
        if (!exists) return candidate
    }
    error("Unable to generate QR code")
}

private fun customerUrl(origin: String, code: String) =
    "$origin/qr/?code=${java.net.URLEncoder.encode(code, Charsets.UTF_8)}"

private fun readableSectionName(sectionId: String): String =
    sectionId.replace('-', ' ').replace('_', ' ').trim().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }

private fun ApplicationCall.adminOrigin(): String {
    val scheme = request.headers["X-Forwarded-Proto"] ?: "http"
    val host = request.host()
    val port = request.headers["X-Forwarded-Port"]?.toIntOrNull() ?: request.port()
    val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
    return if (isDefaultPort) "$scheme://$host" else "$scheme://$host:$port"
}

private fun ResultRow.toQrCodeDto() = QrCodeDto(
    code = this[QrCodesTable.code],
    scope = this[QrCodesTable.scope],
    tableId = this[QrCodesTable.tableId],
    enabled = this[QrCodesTable.enabled],
    expiresAt = this[QrCodesTable.expiresAt],
    createdAt = this[QrCodesTable.createdAt],
    updatedAt = this[QrCodesTable.updatedAt],
)
