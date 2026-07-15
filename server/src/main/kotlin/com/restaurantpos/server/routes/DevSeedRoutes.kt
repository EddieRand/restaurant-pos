package com.restaurantpos.server.routes

import com.restaurantpos.server.auth.requirePermission
import com.restaurantpos.server.db.tables.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.random.Random

/* ── TEMPORARY: one-off mock-data seeding for local dev preview ──────────── */

fun Route.devSeedRoutes(enabled: Boolean = System.getenv("DEMO_MODE")?.equals("true", ignoreCase = true) == true) {
    if (!enabled) return
    authenticate("jwt") {
        post("/admin/dev/seed-mock-data") {
            if (!call.requirePermission("report.daily")) return@post
            val summary = transaction {
                val now = System.currentTimeMillis()
                val day = 24 * 60 * 60 * 1000L

                // ── Menu items (categories: appetizer / main / drink / dessert) ──
                val menuItems = listOf(
                    Triple("item-edamame", """{"zh-CN":"毛豆","en-US":"Edamame"}""", 1800L) to "appetizer",
                    Triple("item-gyoza", """{"zh-CN":"煎饺","en-US":"Pan-fried Dumplings"}""", 2800L) to "appetizer",
                    Triple("item-salad", """{"zh-CN":"沙拉","en-US":"House Salad"}""", 2400L) to "appetizer",
                    Triple("item-ramen", """{"zh-CN":"豚骨拉面","en-US":"Tonkotsu Ramen"}""", 4800L) to "main",
                    Triple("item-curry", """{"zh-CN":"咖喱饭","en-US":"Curry Rice"}""", 4200L) to "main",
                    Triple("item-teriyaki", """{"zh-CN":"照烧鸡排饭","en-US":"Teriyaki Chicken"}""", 4600L) to "main",
                    Triple("item-sushi", """{"zh-CN":"什锦寿司","en-US":"Sushi Platter"}""", 6800L) to "main",
                    Triple("item-cola", """{"zh-CN":"可乐","en-US":"Cola"}""", 1200L) to "drink",
                    Triple("item-tea", """{"zh-CN":"乌龙茶","en-US":"Oolong Tea"}""", 1000L) to "drink",
                    Triple("item-beer", """{"zh-CN":"生啤","en-US":"Draft Beer"}""", 2200L) to "drink",
                    Triple("item-icecream", """{"zh-CN":"抹茶冰淇淋","en-US":"Matcha Ice Cream"}""", 1800L) to "dessert",
                    Triple("item-cheesecake", """{"zh-CN":"芝士蛋糕","en-US":"Cheesecake"}""", 2600L) to "dessert",
                )
                var menuCreated = 0
                menuItems.forEach { (triple, categoryId) ->
                    val (id, names, price) = triple
                    val exists = MenuItemsTable.selectAll().where { MenuItemsTable.id eq id }.count() > 0
                    if (!exists) {
                        MenuItemsTable.insert {
                            it[MenuItemsTable.id] = id
                            it[MenuItemsTable.names] = names
                            it[priceMinorUnit] = price
                            it[MenuItemsTable.categoryId] = categoryId
                            it[course] = if (categoryId == "drink") 0 else if (categoryId == "dessert") 3 else 1
                            it[updatedAt] = now
                        }
                        menuCreated++
                    }
                }

                // ── Floor sections & tables ──
                val sections = listOf("main-hall" to "大厅", "private-room" to "包间", "patio" to "露台")
                sections.forEach { (id, name) ->
                    val exists = FloorSectionsTable.selectAll().where { FloorSectionsTable.id eq id }.count() > 0
                    if (!exists) {
                        FloorSectionsTable.insert {
                            it[FloorSectionsTable.id] = id
                            it[FloorSectionsTable.name] = name
                            it[sortOrder] = sections.indexOfFirst { s -> s.first == id }
                        }
                    }
                }
                val tables = listOf(
                    "table-2" to ("2 号桌" to "main-hall"), "table-3" to ("3 号桌" to "main-hall"),
                    "table-4" to ("4 号桌" to "main-hall"), "table-5" to ("5 号桌" to "main-hall"),
                    "table-6" to ("6 号包间" to "private-room"), "table-7" to ("7 号包间" to "private-room"),
                    "table-8" to ("8 号桌" to "patio"), "table-9" to ("9 号桌" to "patio"),
                )
                var tablesCreated = 0
                tables.forEachIndexed { idx, (id, info) ->
                    val (name, sectionId) = info
                    val exists = TablesTable.selectAll().where { TablesTable.id eq id }.count() > 0
                    if (!exists) {
                        val col = idx % 4
                        val row = idx / 4
                        TablesTable.insert {
                            it[TablesTable.id] = id
                            it[TablesTable.name] = name
                            it[TablesTable.sectionId] = sectionId
                            it[capacity] = listOf(2, 4, 4, 6, 8).random()
                            it[status] = "AVAILABLE"
                            it[shape] = "square"
                            it[x] = 40 + col * 160
                            it[y] = 40 + row * 160
                            it[w] = 120
                            it[h] = 120
                            it[updatedAt] = now   // > 0 so the watermark pull (since=0) returns it
                        }
                        tablesCreated++
                    }
                }
                val allTableIds = listOf("table-1") + tables.map { it.first }

                // ── Membership tiers ──
                val tiers = listOf(
                    Triple("tier-bronze", "青铜会员", 0L) to Pair("amber", 0L),
                    Triple("tier-silver", "白银会员", 1000L) to Pair("gray", 50L),
                    Triple("tier-gold", "黄金会员", 5000L) to Pair("yellow", 100L),
                )
                tiers.forEachIndexed { idx, (triple, extra) ->
                    val (id, name, minPoints) = triple
                    val (color, discount) = extra
                    val exists = MembershipTiersTable.selectAll().where { MembershipTiersTable.id eq id }.count() > 0
                    if (!exists) {
                        MembershipTiersTable.insert {
                            it[MembershipTiersTable.id] = id
                            it[MembershipTiersTable.name] = name
                            it[MembershipTiersTable.minPoints] = minPoints
                            it[MembershipTiersTable.color] = color
                            it[discountPermille] = discount
                            it[MembershipTiersTable.sortOrder] = idx
                        }
                    }
                }

                // ── Customers ──
                val customers = listOf(
                    Triple("cust-1", "王芳", "13800001111") to Triple(2400L, "tier-gold", 12),
                    Triple("cust-2", "李明", "13800002222") to Triple(800L, "tier-silver", 6),
                    Triple("cust-3", "张伟", "13800003333") to Triple(150L, "tier-bronze", 2),
                    Triple("cust-4", "陈静", "13800004444") to Triple(3200L, "tier-gold", 18),
                    Triple("cust-5", "刘洋", "13800005555") to Triple(60L, "tier-bronze", 1),
                    Triple("cust-6", "赵敏", "13800006666") to Triple(1100L, "tier-silver", 7),
                )
                var customersCreated = 0
                customers.forEach { (triple, extra) ->
                    val (id, name, phone) = triple
                    val (points, tierId, visits) = extra
                    val exists = CustomersTable.selectAll().where { CustomersTable.id eq id }.count() > 0
                    if (!exists) {
                        CustomersTable.insert {
                            it[CustomersTable.id] = id
                            it[CustomersTable.name] = name
                            it[CustomersTable.phone] = phone
                            it[loyaltyPoints] = points
                            it[membershipTierId] = tierId
                            it[totalVisits] = visits
                            it[totalSpendMinorUnit] = points * 100
                            it[lastVisitAt] = now - Random.nextLong(0, 5 * day)
                            it[registeredAt] = now - Random.nextLong(30 * day, 365 * day)
                            it[updatedAt] = now   // > 0 so the watermark pull (since=0) returns it
                        }
                        customersCreated++
                    }
                }
                val customerIds = customers.map { it.first.first }

                // ── Staff (POS users) ──
                val staff = listOf(
                    Triple("staff-cashier-1", "周婷", "cashier"),
                    Triple("staff-waiter-1", "孙浩", "waiter"),
                    Triple("staff-waiter-2", "吴双", "waiter"),
                    Triple("staff-manager-1", "郑凯", "manager"),
                )
                var staffCreated = 0
                staff.forEach { (id, name, role) ->
                    val exists = UsersTable.selectAll().where { UsersTable.id eq id }.count() > 0
                    if (!exists) {
                        UsersTable.insert {
                            it[UsersTable.id] = id
                            it[displayName] = name
                            it[UsersTable.role] = role
                            it[pinHash] = sha256("1234")
                            it[isActive] = true
                            it[createdAt] = now - Random.nextLong(30 * day, 200 * day)
                        }
                        staffCreated++
                    }
                }

                // ── Reservations (upcoming) ──
                val reservationNames = listOf("黄先生", "林女士", "马先生", "杨女士", "何先生", "韩女士")
                var reservationsCreated = 0
                reservationNames.forEachIndexed { idx, name ->
                    val id = "resv-$idx"
                    val exists = ReservationsTable.selectAll().where { ReservationsTable.id eq id }.count() > 0
                    if (!exists) {
                        val futureDay = java.time.LocalDate.now().plusDays((idx + 1).toLong())
                        ReservationsTable.insert {
                            it[ReservationsTable.id] = id
                            it[customerName] = name
                            it[phone] = "139000${10000 + idx}"
                            it[partySize] = listOf(2, 3, 4, 6).random()
                            it[date] = futureDay.toString()
                            it[time] = listOf("11:30", "12:30", "18:00", "19:00", "19:30").random()
                            it[status] = listOf("PENDING", "CONFIRMED", "CONFIRMED").random()
                            it[bookingSource] = listOf("ONLINE", "PHONE", "WALK_IN").random()
                            it[createdAt] = now - Random.nextLong(0, 3 * day)
                        }
                        reservationsCreated++
                    }
                }

                // ── Orders + items + payments (past 7 days, CLOSED, for reports) ──
                val menuItemIds = menuItems.map { it.first.first to it.first.second to it.first.third to it.second }
                val paymentMethods = listOf("CASH", "CARD", "WECHAT", "ALIPAY")
                var ordersCreated = 0
                if (OrdersTable.selectAll().where { OrdersTable.orderNotes eq "MOCK_SEED" }.count() == 0L) {
                    repeat(60) { i ->
                        val daysAgo = Random.nextInt(0, 7)
                        val createdAt = now - daysAgo * day - Random.nextLong(0, day)
                        val orderId = "mock-order-${UUID.randomUUID()}"
                        val itemCount = Random.nextInt(1, 5)
                        val chosen = menuItemIds.shuffled().take(itemCount)
                        var subtotal = 0L
                        val orderItemRows = chosen.map { (info, _) ->
                            val (idAndNames, price) = info
                            val (menuItemId, names) = idAndNames
                            val qty = Random.nextInt(1, 4)
                            subtotal += price * qty
                            Triple(menuItemId, names, price) to qty
                        }
                        val tax = (subtotal * 0.08).toLong()
                        val guestCount = Random.nextInt(1, 6)
                        val tableId = if (Random.nextInt(0, 5) > 0) allTableIds.random() else null
                        val type = if (tableId != null) "DINE_IN" else listOf("TAKEOUT", "QR_TABLE").random()

                        OrdersTable.insert {
                            it[OrdersTable.id] = orderId
                            it[OrdersTable.type] = type
                            it[OrdersTable.tableId] = tableId
                            it[OrdersTable.guestCount] = guestCount
                            it[sourceTerminalId] = "terminal-1"
                            it[operatorId] = staff.random().first
                            it[subtotalMinorUnit] = subtotal
                            it[taxTotalMinorUnit] = tax
                            it[serviceChargeMinorUnit] = 0L
                            it[tipMinorUnit] = if (Random.nextBoolean()) Random.nextLong(0, 1000) else 0L
                            it[discountMinorUnit] = if (Random.nextInt(0, 4) == 0) Random.nextLong(100, 1000) else 0L
                            it[status] = "CLOSED"
                            it[orderNotes] = "MOCK_SEED"
                            it[OrdersTable.createdAt] = createdAt
                            it[updatedAt] = createdAt + Random.nextLong(10 * 60 * 1000, 90 * 60 * 1000)
                        }
                        orderItemRows.forEach { (info, qty) ->
                            val (menuItemId, names, price) = info
                            OrderItemsTable.insert {
                                it[id] = "mock-oi-${UUID.randomUUID()}"
                                it[OrderItemsTable.orderId] = orderId
                                it[OrderItemsTable.menuItemId] = menuItemId
                                it[menuItemNameSnapshot] = names
                                it[quantity] = qty
                                it[unitPriceMinorUnit] = price
                                it[course] = 1
                                it[status] = "SERVED"
                            }
                        }
                        val grandTotal = subtotal + tax
                        PaymentsTable.insert {
                            it[id] = "mock-pay-${UUID.randomUUID()}"
                            it[PaymentsTable.orderId] = orderId
                            it[amountMinorUnit] = grandTotal
                            it[method] = paymentMethods.random()
                            it[status] = "COMPLETED"
                            it[operatorId] = staff.random().first
                            it[PaymentsTable.createdAt] = createdAt + Random.nextLong(5 * 60 * 1000, 60 * 60 * 1000)
                        }
                        if (customerIds.isNotEmpty() && Random.nextInt(0, 3) == 0) {
                            // no-op: associating customers with orders is out of scope of current schema
                        }
                        ordersCreated++
                    }
                }

                mapOf(
                    "menuItemsCreated" to menuCreated,
                    "tablesCreated" to tablesCreated,
                    "customersCreated" to customersCreated,
                    "staffCreated" to staffCreated,
                    "reservationsCreated" to reservationsCreated,
                    "ordersCreated" to ordersCreated,
                )
            }
            call.respond(summary)
        }
    }
}

private fun sha256(input: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
