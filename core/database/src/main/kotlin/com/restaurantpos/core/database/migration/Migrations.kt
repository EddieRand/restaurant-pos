package com.restaurantpos.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1→v2: Add split/merge tracking columns to orders table. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN splitFromOrderId TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN mergedIntoOrderId TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN mergedTableIds TEXT NOT NULL DEFAULT ''")
    }
}

/** v4→v5: Add kitchen_tickets table + categoryId column to order_items. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE order_items ADD COLUMN categoryId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS kitchen_tickets (
                id TEXT NOT NULL PRIMARY KEY,
                orderId TEXT NOT NULL,
                orderItemIds TEXT NOT NULL,
                stationId TEXT NOT NULL,
                course INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                bumpedAt INTEGER
            )
            """.trimIndent()
        )
    }
}

/** v3→v4: Add reservations table. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reservations (
                id TEXT NOT NULL PRIMARY KEY,
                tableId TEXT NOT NULL,
                guestName TEXT NOT NULL,
                guestCount INTEGER NOT NULL,
                scheduledAt INTEGER NOT NULL,
                notes TEXT NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v6→v7: Add modifier_groups and modifiers tables. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS modifier_groups (
                id TEXT NOT NULL PRIMARY KEY,
                menuItemId TEXT NOT NULL,
                names TEXT NOT NULL,
                type TEXT NOT NULL,
                required INTEGER NOT NULL,
                minSelect INTEGER NOT NULL,
                maxSelect INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_modifier_groups_menuItemId` ON modifier_groups(menuItemId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS modifiers (
                id TEXT NOT NULL PRIMARY KEY,
                groupId TEXT NOT NULL,
                names TEXT NOT NULL,
                priceAdjustmentMinorUnit INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_modifiers_groupId` ON modifiers(groupId)")
    }
}

/** v5→v6: Add users table + operatorId column to orders. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN operatorId TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                role TEXT NOT NULL,
                pinHash TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_pinHash` ON users(pinHash)")
    }
}

/** v2→v3: Add persistent sync outbox table. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_outbox (
                id TEXT NOT NULL PRIMARY KEY,
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                operation TEXT NOT NULL,
                payload TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
        // 注意：不建额外索引——实体未声明索引，多余索引会导致 Room 迁移后 schema 校验失败（F-011）
    }
}

/** v7→v8: Add tipMinorUnit column to orders table. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN tipMinorUnit INTEGER NOT NULL DEFAULT 0")
    }
}

/** v8→v9: Add allergens column to menu_items; allergenSnapshot to order_items. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE order_items ADD COLUMN allergenSnapshot TEXT NOT NULL DEFAULT ''")
    }
}

/** v10→v11: Add combos + combo_components tables; add comboId column to order_items. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS combos (
                id TEXT NOT NULL PRIMARY KEY,
                names TEXT NOT NULL,
                comboPriceMinorUnit INTEGER NOT NULL,
                taxRateId TEXT,
                isActive INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS combo_components (
                id TEXT NOT NULL PRIMARY KEY,
                comboId TEXT NOT NULL,
                menuItemId TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE order_items ADD COLUMN comboId TEXT")
    }
}

/** v11→v12: Add orderNotes column to orders table. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN orderNotes TEXT NOT NULL DEFAULT ''")
    }
}

/** v9→v10: Add coupons table. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS coupons (
                id TEXT NOT NULL PRIMARY KEY,
                code TEXT NOT NULL,
                type TEXT NOT NULL,
                value INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL,
                isActive INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v13→v14: Add menu_profiles, menu_item_profiles tables; stockCount column to menu_items. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN stockCount INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS menu_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                startTime TEXT,
                endTime TEXT,
                daysOfWeek TEXT,
                channels TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS menu_item_profiles (
                menuItemId TEXT NOT NULL,
                menuProfileId TEXT NOT NULL,
                PRIMARY KEY (menuItemId, menuProfileId),
                FOREIGN KEY (menuItemId) REFERENCES menu_items(id) ON DELETE CASCADE,
                FOREIGN KEY (menuProfileId) REFERENCES menu_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_item_profiles_menuProfileId` ON menu_item_profiles(menuProfileId)")
    }
}

/** v14→v15: Add customers + loyalty_transactions tables for CRM. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS customers (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                gender TEXT,
                birthday TEXT,
                tags TEXT NOT NULL,
                notes TEXT,
                totalSpendMinorUnit INTEGER NOT NULL,
                loyaltyPoints INTEGER NOT NULL,
                membershipTierId TEXT,
                totalVisits INTEGER NOT NULL,
                lastVisitAt INTEGER NOT NULL,
                registeredAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS loyalty_transactions (
                id TEXT NOT NULL PRIMARY KEY,
                customerId TEXT NOT NULL,
                orderId TEXT,
                type TEXT NOT NULL,
                points INTEGER NOT NULL,
                description TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v12→v13: Add waiter_calls table for tableside PAD "call waiter" feature. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS waiter_calls (
                id TEXT NOT NULL PRIMARY KEY,
                tableId TEXT NOT NULL,
                tableDisplayName TEXT NOT NULL,
                reason TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                acknowledgedAt INTEGER
            )
            """.trimIndent()
        )
    }
}

/** v15→v16: Add updatedAt watermark to menu_items for server pull-sync (Batch 44). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}

/** v17→v18: Add roles + role_permissions + daily_snapshots tables; migrate users.role to lowercase. */
/**
 * v16→v17: 版本号提升但无 SQL 层 schema 变更（16.json 与 18.json 中除 17→18 新建的三张表外
 * 完全一致，users 列名未变）。此迁移曾缺失导致 v16 及更早设备升级时崩溃（F-009），补为 no-op 接通链路。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // no-op: v17 had no SQL-level schema change
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create roles table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS roles (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                isBuiltin INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. Create role_permissions table (composite PK)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS role_permissions (
                roleId TEXT NOT NULL,
                permissionKey TEXT NOT NULL,
                PRIMARY KEY (roleId, permissionKey)
            )
            """.trimIndent()
        )
        // 索引名必须与 Room 实体生成名一致，否则迁移后 schema 校验失败（F-011）
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_role_permissions_roleId` ON role_permissions(roleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_role_permissions_permissionKey` ON role_permissions(permissionKey)")

        // 3. Insert default 4 roles
        val roles = listOf(
            arrayOf("admin", "role.admin", 1, 0),
            arrayOf("manager", "role.manager", 1, 1),
            arrayOf("cashier", "role.cashier", 1, 2),
            arrayOf("waiter", "role.waiter", 1, 3),
        )
        for (r in roles) {
            db.execSQL(
                "INSERT OR IGNORE INTO roles (id, displayName, isBuiltin, sortOrder) VALUES (?, ?, ?, ?)",
                r
            )
        }

        // 4. Insert default permission matrix (4 roles × 27 permissions)
        // Admin: ALL 27
        val allKeys = listOf(
            "order.create", "order.modify", "order.void_item", "order.void",
            "order.transfer", "order.merge", "order.split", "order.note",
            "payment.process", "payment.discount", "payment.refund", "payment.split",
            "payment.tip", "payment.coupon",
            "menu.view", "menu.edit", "menu.sold_out", "menu.combo",
            "report.daily", "report.shift", "report.export",
            "settings.region", "settings.printer", "settings.receipt", "settings.tax",
            "staff.manage", "staff.roles",
        )
        // Manager: all except staff.* and settings.region/settings.tax
        val managerExcept = setOf("settings.region", "settings.tax", "staff.manage", "staff.roles")
        val managerKeys = allKeys.filter { it !in managerExcept }
        // Cashier: order.create, order.note, payment.process, payment.discount,
        //           payment.tip, payment.coupon, menu.view, menu.sold_out,
        //           report.daily
        val cashierKeys = setOf(
            "order.create", "order.note",
            "payment.process", "payment.discount", "payment.tip", "payment.coupon",
            "menu.view", "menu.sold_out",
            "report.daily",
        )
        // Waiter: order.create, order.note, menu.view
        val waiterKeys = setOf("order.create", "order.note", "menu.view")

        fun insertPermissions(roleId: String, keys: Iterable<String>) {
            for (key in keys) {
                db.execSQL(
                    "INSERT OR IGNORE INTO role_permissions (roleId, permissionKey) VALUES (?, ?)",
                    arrayOf(roleId, key)
                )
            }
        }
        insertPermissions("admin", allKeys)
        insertPermissions("manager", managerKeys)
        insertPermissions("cashier", cashierKeys)
        insertPermissions("waiter", waiterKeys)

        // 5. Migrate users.role from uppercase enum name to lowercase roleId
        db.execSQL("UPDATE users SET role = LOWER(role)")

        // 6. Create daily_snapshots table for reporting module (P1 quick wins)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_snapshots (
                date TEXT NOT NULL PRIMARY KEY,
                netRevenueMinorUnit INTEGER NOT NULL,
                grossRevenueMinorUnit INTEGER NOT NULL,
                orderCount INTEGER NOT NULL,
                guestCount INTEGER NOT NULL,
                avgCheckMinorUnit INTEGER NOT NULL,
                avgPerGuestMinorUnit INTEGER NOT NULL,
                discountTotalMinorUnit INTEGER NOT NULL,
                taxTotalMinorUnit INTEGER NOT NULL,
                serviceChargeTotalMinorUnit INTEGER NOT NULL,
                tipTotalMinorUnit INTEGER NOT NULL,
                paymentBreakdownJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v18→v19: Add updatedAt column to kitchen_tickets for cross-device KDS sync (last-write-wins). */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE kitchen_tickets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE kitchen_tickets SET updatedAt = createdAt")
    }
}

/** v19->v20: Add pickupCode + fulfillmentStatus to orders for kiosk pickup-display flow. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN pickupCode TEXT")
        db.execSQL("ALTER TABLE orders ADD COLUMN fulfillmentStatus TEXT NOT NULL DEFAULT 'NOT_READY'")
    }
}

/**
 * v20->v21: Add updatedAt to tables / customers / reservations so they can sync
 * cross-device by last-write-wins (F-024). Backfill from an existing timestamp where
 * one exists (customers.registeredAt) so pre-existing rows aren't all stuck at 0.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tables ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE customers SET updatedAt = registeredAt")
        db.execSQL("ALTER TABLE reservations ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}
