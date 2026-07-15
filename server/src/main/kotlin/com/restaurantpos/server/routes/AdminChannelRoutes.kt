package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.ChannelsTable
import com.restaurantpos.server.model.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.adminChannelRoutes() {
    authenticate("jwt") {
        route("/admin/channels") {

            get {
                val channels = transaction {
                    ChannelsTable.selectAll()
                        .orderBy(ChannelsTable.sortOrder to SortOrder.ASC)
                        .map { it.toChannelDto() }
                }
                call.respond(channels)
            }

            post {
                val req = call.receive<CreateChannelRequest>()
                val exists = transaction {
                    ChannelsTable.selectAll().where { ChannelsTable.id eq req.id }.count() > 0
                }
                if (exists) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Channel id '${req.id}' already exists"))
                    return@post
                }
                transaction {
                    ChannelsTable.insert {
                        it[id]        = req.id
                        it[name]      = req.name
                        it[sortOrder] = req.sortOrder
                        it[enabled]   = true
                        it[color]     = req.color
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to req.id))
            }

            route("/{id}") {
                patch {
                    val channelId = call.parameters["id"]!!
                    val req = call.receive<UpdateChannelRequest>()
                    val updated = transaction {
                        ChannelsTable.update({ ChannelsTable.id eq channelId }) { stmt ->
                            req.name?.let      { stmt[name]      = it }
                            req.sortOrder?.let { stmt[sortOrder] = it }
                            req.enabled?.let   { stmt[enabled]   = it }
                            req.color?.let     { stmt[color]     = it }
                        }
                    }
                    if (updated == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Channel not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("updated" to true))
                }

                delete {
                    val channelId = call.parameters["id"]!!
                    val deleted = transaction { ChannelsTable.deleteWhere { id eq channelId } }
                    if (deleted == 0) call.respond(HttpStatusCode.NotFound, ErrorResponse("Channel not found"))
                    else call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }
        }
    }
}

private fun ResultRow.toChannelDto() = ChannelDto(
    id        = this[ChannelsTable.id],
    name      = this[ChannelsTable.name],
    sortOrder = this[ChannelsTable.sortOrder],
    enabled   = this[ChannelsTable.enabled],
    color     = this[ChannelsTable.color],
)
