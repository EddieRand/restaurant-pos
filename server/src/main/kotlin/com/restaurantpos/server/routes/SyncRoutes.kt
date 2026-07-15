package com.restaurantpos.server.routes

import com.restaurantpos.server.db.tables.KitchenTicketsTable
import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.model.KitchenTicketDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.restaurantpos.server.db.tables.OrderItemsTable
import com.restaurantpos.server.db.tables.OrdersTable
import com.restaurantpos.server.db.tables.RolePermissionsTable
import com.restaurantpos.server.db.tables.RolesTable
import com.restaurantpos.server.db.tables.CustomersTable
import com.restaurantpos.server.db.tables.SyncLogTable
import com.restaurantpos.server.db.tables.TablesTable
import com.restaurantpos.server.db.tables.UsersTable
import com.restaurantpos.server.model.CustomerSyncDto
import com.restaurantpos.server.model.CustomerSyncPullResponse
import com.restaurantpos.server.model.TableSyncDto
import com.restaurantpos.server.model.TableSyncPullResponse
import com.restaurantpos.server.model.OrderItemPullDto
import com.restaurantpos.server.model.OrderPullDto
import com.restaurantpos.server.model.PermissionSyncPullResponse
import com.restaurantpos.server.sync.SyncPushProcessor
import com.restaurantpos.server.model.PermissionSyncMappingDto
import com.restaurantpos.server.model.PermissionSyncRoleDto
import com.restaurantpos.server.model.SyncPullResponse
import com.restaurantpos.server.model.UserSyncDto
import com.restaurantpos.server.model.UserSyncPullResponse
import com.restaurantpos.server.model.SyncPushRequest
import com.restaurantpos.server.model.SyncPushResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Route.syncRoutes() {
    authenticate("jwt") {
        route("/sync") {
            get("/pull") {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                val menuItems = transaction {
                    MenuItemsTable.selectAll()
                        .where { MenuItemsTable.updatedAt greater since }
                        .map { it.toMenuItemDto() }
                }

                val kitchenTickets = transaction {
                    KitchenTicketsTable.selectAll()
                        .where { KitchenTicketsTable.updatedAt greater since }
                        .map {
                            KitchenTicketDto(
                                id = it[KitchenTicketsTable.id],
                                orderId = it[KitchenTicketsTable.orderId],
                                orderItemIds = runCatching {
                                    Json.decodeFromString<List<String>>(it[KitchenTicketsTable.orderItemIds])
                                }.getOrDefault(emptyList()),
                                stationId = it[KitchenTicketsTable.stationId],
                                course = it[KitchenTicketsTable.course],
                                status = it[KitchenTicketsTable.status],
                                createdAt = it[KitchenTicketsTable.createdAt],
                                bumpedAt = it[KitchenTicketsTable.bumpedAt],
                                updatedAt = it[KitchenTicketsTable.updatedAt],
                            )
                        }
                }

                // Orders changed since the watermark, plus their items (items carry no own
                // updatedAt — they ride along whenever their order changes).
                val orders = transaction {
                    OrdersTable.selectAll()
                        .where { OrdersTable.updatedAt greater since }
                        .map {
                            OrderPullDto(
                                id = it[OrdersTable.id],
                                type = it[OrdersTable.type],
                                tableId = it[OrdersTable.tableId],
                                guestCount = it[OrdersTable.guestCount],
                                sourceTerminalId = it[OrdersTable.sourceTerminalId],
                                operatorId = it[OrdersTable.operatorId],
                                subtotalMinorUnit = it[OrdersTable.subtotalMinorUnit],
                                taxTotalMinorUnit = it[OrdersTable.taxTotalMinorUnit],
                                serviceChargeMinorUnit = it[OrdersTable.serviceChargeMinorUnit],
                                tipMinorUnit = it[OrdersTable.tipMinorUnit],
                                discountMinorUnit = it[OrdersTable.discountMinorUnit],
                                status = it[OrdersTable.status],
                                orderNotes = it[OrdersTable.orderNotes],
                                createdAt = it[OrdersTable.createdAt],
                                updatedAt = it[OrdersTable.updatedAt],
                                pickupCode = it[OrdersTable.pickupCode],
                                fulfillmentStatus = it[OrdersTable.fulfillmentStatus],
                            )
                        }
                }
                val orderItems = if (orders.isEmpty()) emptyList() else transaction {
                    val orderIds = orders.map { it.id }
                    OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId inList orderIds }
                        .map {
                            OrderItemPullDto(
                                id = it[OrderItemsTable.id],
                                orderId = it[OrderItemsTable.orderId],
                                menuItemId = it[OrderItemsTable.menuItemId],
                                menuItemNameSnapshot = it[OrderItemsTable.menuItemNameSnapshot],
                                quantity = it[OrderItemsTable.quantity],
                                unitPriceMinorUnit = it[OrderItemsTable.unitPriceMinorUnit],
                                taxRateId = it[OrderItemsTable.taxRateId],
                                course = it[OrderItemsTable.course],
                                status = it[OrderItemsTable.status],
                                notes = it[OrderItemsTable.notes],
                                categoryId = it[OrderItemsTable.categoryId],
                                allergenSnapshot = it[OrderItemsTable.allergenSnapshot],
                                comboId = it[OrderItemsTable.comboId],
                            )
                        }
                }

                call.respond(
                    HttpStatusCode.OK,
                    SyncPullResponse(
                        serverTime = now,
                        menuItems = menuItems,
                        kitchenTickets = kitchenTickets,
                        orders = orders,
                        orderItems = orderItems,
                    ),
                )
            }

            get("/permissions") {
                val now = System.currentTimeMillis()

                val roles = transaction {
                    RolesTable.selectAll().map {
                        PermissionSyncRoleDto(
                            id = it[RolesTable.id],
                            displayName = it[RolesTable.displayName],
                            isBuiltin = it[RolesTable.isBuiltin],
                            sortOrder = it[RolesTable.sortOrder],
                        )
                    }
                }

                val rolePermissions = transaction {
                    RolePermissionsTable.selectAll().map {
                        PermissionSyncMappingDto(
                            roleId = it[RolePermissionsTable.roleId],
                            permissionKey = it[RolePermissionsTable.permissionKey],
                        )
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    PermissionSyncPullResponse(
                        serverTime = now,
                        roles = roles,
                        rolePermissions = rolePermissions,
                    ),
                )
            }

            // Full staff-user set for PIN-login terminals that don't run the on-device seeder
            // (handheld, pad). JWT-authed; small dataset → full pull, no watermark (see F-023).
            get("/users") {
                val now = System.currentTimeMillis()
                val users = transaction {
                    UsersTable.selectAll().map {
                        UserSyncDto(
                            id = it[UsersTable.id],
                            displayName = it[UsersTable.displayName],
                            roleId = it[UsersTable.role],
                            pinHash = it[UsersTable.pinHash],
                            isActive = it[UsersTable.isActive],
                            createdAt = it[UsersTable.createdAt],
                        )
                    }
                }
                call.respond(HttpStatusCode.OK, UserSyncPullResponse(serverTime = now, users = users))
            }

            // Cross-device table state (status/current order/layout), watermark-based (F-024).
            get("/tables") {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()
                val tables = transaction {
                    TablesTable.selectAll()
                        .where { TablesTable.updatedAt greater since }
                        .map {
                            TableSyncDto(
                                id = it[TablesTable.id],
                                name = it[TablesTable.name],
                                sectionId = it[TablesTable.sectionId],
                                capacity = it[TablesTable.capacity],
                                currentOrderId = it[TablesTable.currentOrderId],
                                status = it[TablesTable.status],
                                updatedAt = it[TablesTable.updatedAt],
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, TableSyncPullResponse(serverTime = now, tables = tables))
            }

            // Customer directory for lookup on non-seeding terminals, watermark-based (F-024).
            get("/customers") {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()
                val customers = transaction {
                    CustomersTable.selectAll()
                        .where { CustomersTable.updatedAt greater since }
                        .map {
                            CustomerSyncDto(
                                id = it[CustomersTable.id],
                                name = it[CustomersTable.name],
                                phone = it[CustomersTable.phone],
                                email = it[CustomersTable.email],
                                gender = it[CustomersTable.gender],
                                birthday = it[CustomersTable.birthday],
                                tags = it[CustomersTable.tags],
                                notes = it[CustomersTable.notes],
                                totalSpendMinorUnit = it[CustomersTable.totalSpendMinorUnit],
                                loyaltyPoints = it[CustomersTable.loyaltyPoints],
                                membershipTierId = it[CustomersTable.membershipTierId],
                                totalVisits = it[CustomersTable.totalVisits],
                                lastVisitAt = it[CustomersTable.lastVisitAt],
                                registeredAt = it[CustomersTable.registeredAt],
                                updatedAt = it[CustomersTable.updatedAt],
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, CustomerSyncPullResponse(serverTime = now, customers = customers))
            }

            post("/push") {
                val req = call.receive<SyncPushRequest>()
                val now = System.currentTimeMillis()

                data class PushResult(val accepted: Boolean, val serverPayload: String?)

                val result = transaction {
                    // Last-write-wins: only upsert if incoming updatedAt >= stored
                    val existing = SyncLogTable
                        .selectAll()
                        .where { SyncLogTable.entityId eq req.entityId }
                        .filter { it[SyncLogTable.entityType] == req.entityType }
                        .maxByOrNull { it[SyncLogTable.updatedAt] }

                    val existingUpdatedAt = existing?.get(SyncLogTable.updatedAt) ?: -1L

                    if (req.updatedAt >= existingUpdatedAt) {
                        // Use insertIgnore for idempotency — if same sync id already exists, skip
                        val insertResult = SyncLogTable.insertIgnore {
                            it[id] = req.id
                            it[entityType] = req.entityType
                            it[entityId] = req.entityId
                            it[operation] = req.operation
                            it[payload] = req.payload
                            it[updatedAt] = req.updatedAt
                            it[retryCount] = req.retryCount
                            it[status] = "ACCEPTED"
                            it[receivedAt] = now
                        }
                        val inserted = insertResult.insertedCount
                        if (inserted > 0) {
                            SyncPushProcessor.process(req.entityType, req.payload)
                            PushResult(accepted = true, serverPayload = null)
                        } else {
                            // Duplicate id — update existing record if newer
                            SyncLogTable.update({ SyncLogTable.id eq req.id }) {
                                it[entityType] = req.entityType
                                it[entityId] = req.entityId
                                it[operation] = req.operation
                                it[payload] = req.payload
                                it[updatedAt] = req.updatedAt
                                it[retryCount] = req.retryCount
                                it[status] = "ACCEPTED"
                                it[receivedAt] = now
                            }
                            SyncPushProcessor.process(req.entityType, req.payload)
                            PushResult(accepted = true, serverPayload = null)
                        }
                    } else {
                        // Server has a newer version — return its payload so client can reconcile
                        PushResult(accepted = false, serverPayload = existing?.get(SyncLogTable.payload))
                    }
                }

                if (result.accepted) {
                    call.respond(HttpStatusCode.OK, SyncPushResponse(status = "accepted"))
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        SyncPushResponse(status = "conflict", serverPayload = result.serverPayload),
                    )
                }
            }
        }
    }
}
