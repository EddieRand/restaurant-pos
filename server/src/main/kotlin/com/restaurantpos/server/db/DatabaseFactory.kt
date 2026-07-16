package com.restaurantpos.server.db

import com.restaurantpos.server.db.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.util.UUID

object DatabaseFactory {

    fun init(jdbcUrl: String, user: String, password: String) {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        Database.connect(HikariDataSource(config))
        createTables()
    }

    /** For tests: connect to an existing DataSource (e.g. H2 in-memory). */
    fun initWithUrl(jdbcUrl: String) {
        Database.connect(jdbcUrl, driver = "org.h2.Driver")
        createTables()
    }

    private fun createTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                SyncLogTable,
                ChannelsTable,
                MenuCategoriesTable,
                MenuItemsTable,
                OrdersTable,
                OrderItemsTable,
                PaymentsTable,
                QrCodesTable,
                QrSessionsTable,
                PaymentIntentsTable,
                UsersTable,
                AdminUsersTable,
                TablesTable,
                FloorSectionsTable,
                CouponsTable,
                CombosTable,
                ComboComponentsTable,
                ModifierGroupsTable,
                ModifierOptionsTable,
                SettingsTable,
                AiPriceProposalsTable,
                AiPriceProposalChangesTable,
                AiMutationAuditsTable,
                AiWorkspaceSessionsTable,
                AiWorkspaceMessagesTable,
                AiWorkspaceRunsTable,
                AiWorkspaceRunStepsTable,
                AiWorkspaceEventsTable,
                AiGrowthBriefingsTable,
                AiGrowthProposalsTable,
                AiGrowthProposalVersionsTable,
                AiActionAuditsTable,
                MenuProfilesTable,
                MenuItemProfilesTable,
                WaiterCallsTable,
                CustomersTable,
                LoyaltyTransactionsTable,
                MembershipTiersTable,
                CampaignsTable,
                SuppliersTable,
                IngredientsTable,
                StockMovementsTable,
                PurchaseOrdersTable,
                PurchaseOrderItemsTable,
                OutboundOrdersTable,
                OutboundOrderItemsTable,
                BomLinesTable,
                StocktakeOrdersTable,
                StocktakeItemsTable,
                ReservationsTable,
                WaitlistTable,
                ShiftsTable,
                SpecialDaysTable,
                RolesTable,
                RolePermissionsTable,
                DailySnapshotsTable,
                OrderItemModifiersTable,
                CashierShiftsTable,
                CashMovementsTable,
                EmployeeTimecardsTable,
                PaymentMethodsTable,
                KitchenTicketsTable,
                ShiftSchedulesTable,
                GiftCardsTable,
                GiftCardTransactionsTable,
                GroupBuyingVouchersTable,
                GroupBuyingRedemptionsTable,
                CdsStateTable,
            )
            seedDefaultChannels()
            seedDefaultCategories()
            seedDefaultTables()
            seedDefaultQrCode()
            seedDefaultAdmin()
            seedDefaultRolesAndPermissions()
            seedDefaultPaymentMethods()
            seedDemoGroupBuyingVouchers()
        }
    }

    private fun seedDemoGroupBuyingVouchers() {
        if (GroupBuyingVouchersTable.selectAll().count() > 0) return
        val now = System.currentTimeMillis()
        val expiry = now + 30L * 24 * 60 * 60 * 1000
        listOf(
            listOf("demo-douyin-1001", "DOUYIN", "DY-DEMO-1001", "抖音咖啡双人团购套餐", "880"),
            listOf("demo-meituan-1001", "MEITUAN", "MT-DEMO-1001", "美团到店 10 元代金券", "1000"),
        ).forEach { row ->
            val normalizedCode = row[2].uppercase()
            GroupBuyingVouchersTable.insert {
                it[id] = row[0]
                it[provider] = row[1]
                it[codeHash] = sha256(normalizedCode)
                it[codeLast4] = normalizedCode.takeLast(4)
                it[title] = row[3]
                it[faceValueMinorUnit] = row[4].toLong()
                it[expiresAt] = expiry
                it[status] = "AVAILABLE"
                it[demo] = true
                it[createdAt] = now
            }
        }
    }

    private fun seedDefaultChannels() {
        if (ChannelsTable.selectAll().count() > 0) return
        listOf(
            Triple("DINE_IN",  "堂食",    "blue"),
            Triple("TAKEAWAY", "外卖自取", "amber"),
            Triple("DELIVERY", "配送",    "purple"),
            Triple("KIOSK",    "自助机",  "emerald"),
        ).forEachIndexed { idx, (id, name, color) ->
            ChannelsTable.insert {
                it[ChannelsTable.id]        = id
                it[ChannelsTable.name]      = name
                it[ChannelsTable.sortOrder] = (idx + 1) * 10
                it[ChannelsTable.enabled]   = true
                it[ChannelsTable.color]     = color
            }
        }
    }

    private fun seedDefaultPaymentMethods() {
        val defaults = listOf(
            // code, baseType, displayName, color, sortOrder
            arrayOf("CASH", "CASH", "Cash", "green", 10),
            arrayOf("CARD", "CARD", "Card", "blue", 20),
            arrayOf("WECHAT", "OTHER", "WeChat Pay", "emerald", 30),
            arrayOf("ALIPAY", "OTHER", "Alipay", "sky", 40),
        )
        if (PaymentMethodsTable.selectAll().count() > 0) {
            // Backfill baseType/color/displayName for rows created under the old schema
            defaults.forEach { (code, baseType, name, color, _) ->
                PaymentMethodsTable.update({
                    (PaymentMethodsTable.code eq code as String) and (PaymentMethodsTable.baseType eq "OTHER") and (PaymentMethodsTable.color eq "gray")
                }) {
                    it[PaymentMethodsTable.baseType] = baseType as String
                    it[PaymentMethodsTable.color] = color as String
                    it[displayName] = name as String
                }
            }
            return
        }
        defaults.forEach { (code, baseType, name, color, order) ->
            PaymentMethodsTable.insert {
                it[id] = java.util.UUID.randomUUID().toString()
                it[PaymentMethodsTable.code] = code as String
                it[PaymentMethodsTable.baseType] = baseType as String
                it[displayName] = name as String
                it[PaymentMethodsTable.color] = color as String
                it[sortOrder] = order as Int
                it[isActive] = true
            }
        }
    }

    private fun seedDefaultCategories() {
        if (MenuCategoriesTable.selectAll().count() > 0) return
        val cats = listOf(
            Triple("appetizer", "前菜 / 小食", 10),
            Triple("main",      "主食 / 主菜", 20),
            Triple("drink",     "饮品",        30),
            Triple("dessert",   "甜点",        40),
        )
        cats.forEach { (id, name, order) ->
            MenuCategoriesTable.insert {
                it[MenuCategoriesTable.id]        = id
                it[MenuCategoriesTable.name]      = name
                it[MenuCategoriesTable.sortOrder] = order
            }
        }
    }

    private fun seedDefaultTables() {
        val exists = TablesTable.selectAll()
            .where { TablesTable.id eq "table-1" }
            .count() > 0
        if (!exists) {
            TablesTable.insert {
                it[id] = "table-1"
                it[name] = "1 号桌"
                it[sectionId] = "main-hall"
                it[capacity] = 4
                it[currentOrderId] = null
                it[status] = "AVAILABLE"
                it[shape] = "square"
                it[x] = 40
                it[y] = 360
                it[w] = 120
                it[h] = 120
            }
        }
    }

    private fun seedDefaultQrCode() {
        val exists = QrCodesTable.selectAll()
            .where { QrCodesTable.code eq "demo-table-1" }
            .count() > 0
        if (!exists) {
            val now = System.currentTimeMillis()
            QrCodesTable.insert {
                it[code] = "demo-table-1"
                it[scope] = "TABLE"
                it[tableId] = "table-1"
                it[enabled] = true
                it[expiresAt] = null
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    private fun seedDefaultAdmin() {
        val exists = AdminUsersTable.selectAll()
            .where { AdminUsersTable.email eq "admin@pos.local" }
            .count() > 0
        if (!exists) {
            AdminUsersTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[email] = "admin@pos.local"
                it[passwordHash] = sha256("admin123")
                it[role] = "admin"
                it[isActive] = true
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    private fun seedDefaultRolesAndPermissions() {
        val exists = RolesTable.selectAll().count() > 0

        // 1. Insert 4 built-in roles
        val roleIds = listOf("admin", "manager", "cashier", "waiter")
        val roleNames = listOf("role.admin", "role.manager", "role.cashier", "role.waiter")
        if (!exists) {
            roleIds.forEachIndexed { i, id ->
                RolesTable.insert {
                    it[RolesTable.id]          = id
                    it[displayName]            = roleNames[i]
                    it[isBuiltin]             = true
                    it[sortOrder]              = i
                }
            }
        }

        // 2. Insert default permission matrix
        // Admin: all built-in permissions.
        val allKeys = listOf(
            "order.create", "order.modify", "order.void_item", "order.void",
            "order.transfer", "order.merge", "order.split", "order.note",
            "payment.process", "payment.discount", "payment.refund", "payment.split",
            "payment.tip", "payment.coupon",
            "menu.view", "menu.edit", "menu.sold_out", "menu.combo",
            "report.daily", "report.shift", "report.export",
            "settings.region", "settings.printer", "settings.receipt", "settings.tax",
            "staff.manage", "staff.roles",
            "crm.campaign.manage",
        )
        // Manager: all except sensitive system settings (region/tax)
        val managerExcept = setOf("settings.region", "settings.tax")
        val managerKeys = allKeys.filter { it !in managerExcept }
        // Cashier
        val cashierKeys = setOf(
            "order.create", "order.note",
            "payment.process", "payment.discount", "payment.tip", "payment.coupon",
            "menu.view", "menu.sold_out",
            "report.daily",
        )
        // Waiter
        val waiterKeys = setOf("order.create", "order.note", "menu.view")

        fun insertPermissions(roleId: String, keys: Iterable<String>) {
            keys.forEach { key ->
                RolePermissionsTable.insertIgnore {
                    it[RolePermissionsTable.roleId]        = roleId
                    it[RolePermissionsTable.permissionKey]  = key
                }
            }
        }

        insertPermissions("admin", allKeys)
        insertPermissions("manager", managerKeys)
        insertPermissions("cashier", cashierKeys)
        insertPermissions("waiter", waiterKeys)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
