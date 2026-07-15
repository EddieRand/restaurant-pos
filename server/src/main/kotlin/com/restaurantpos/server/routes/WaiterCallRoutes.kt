package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.WaiterCallsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class WaiterCallDto(
    val id: String,
    val tableId: String,
    val tableDisplayName: String,
    val reason: String,
    val createdAt: Long,
    val acknowledgedAt: Long? = null,
)

@Serializable
data class CreateWaiterCallRequest(
    val id: String,
    val tableId: String,
    val tableDisplayName: String,
    val reason: String = "GENERAL",
    val createdAt: Long,
)

fun Route.waiterCallRoutes() {
    authenticate("jwt") {
        route("/waiter-calls") {

            // List pending (unacknowledged) calls — cashier polling / SSE fallback
            get {
                val calls = transaction {
                    WaiterCallsTable
                        .selectAll()
                        .where { WaiterCallsTable.acknowledgedAt.isNull() }
                        .orderBy(WaiterCallsTable.createdAt, SortOrder.DESC)
                        .map { it.toDto() }
                }
                call.respond(calls)
            }

            // PAD posts a call
            post {
                val req = call.receive<CreateWaiterCallRequest>()
                transaction {
                    WaiterCallsTable.insertIgnore {
                        it[id] = req.id
                        it[tableId] = req.tableId
                        it[tableDisplayName] = req.tableDisplayName
                        it[reason] = req.reason
                        it[createdAt] = req.createdAt
                        it[acknowledgedAt] = null
                    }
                }
                call.respond(HttpStatusCode.Created)
            }

            // Cashier acknowledges a single call
            post("/{callId}/acknowledge") {
                val callId = call.parameters["callId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val now = System.currentTimeMillis()
                val updated = transaction {
                    WaiterCallsTable.update({ WaiterCallsTable.id eq callId }) {
                        it[acknowledgedAt] = now
                    }
                }
                if (updated == 0) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.NoContent)
            }

            // Acknowledge all pending calls for a table
            post("/acknowledge-table/{tableId}") {
                val tid = call.parameters["tableId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val now = System.currentTimeMillis()
                transaction {
                    WaiterCallsTable.update({
                        (WaiterCallsTable.tableId eq tid) and WaiterCallsTable.acknowledgedAt.isNull()
                    }) {
                        it[acknowledgedAt] = now
                    }
                }
                call.respond(HttpStatusCode.NoContent)
            }

            // Purge old acknowledged calls (housekeeping, called by background job or on demand)
            delete("/purge") {
                val olderThanMs = call.request.queryParameters["olderThanMs"]?.toLongOrNull()
                    ?: (System.currentTimeMillis() - 7 * 24 * 3600_000L) // default: 7 days
                val deleted = transaction {
                    WaiterCallsTable.deleteWhere {
                        (acknowledgedAt.isNotNull()) and (acknowledgedAt less olderThanMs)
                    }
                }
                call.respond(mapOf("deleted" to deleted))
            }
        }
    }
}

private fun ResultRow.toDto() = WaiterCallDto(
    id = this[WaiterCallsTable.id],
    tableId = this[WaiterCallsTable.tableId],
    tableDisplayName = this[WaiterCallsTable.tableDisplayName],
    reason = this[WaiterCallsTable.reason],
    createdAt = this[WaiterCallsTable.createdAt],
    acknowledgedAt = this[WaiterCallsTable.acknowledgedAt],
)
