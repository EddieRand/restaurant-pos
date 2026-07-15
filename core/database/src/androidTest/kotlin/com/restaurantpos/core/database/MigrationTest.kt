package com.restaurantpos.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.restaurantpos.core.database.migration.MIGRATION_10_11
import com.restaurantpos.core.database.migration.MIGRATION_11_12
import com.restaurantpos.core.database.migration.MIGRATION_12_13
import com.restaurantpos.core.database.migration.MIGRATION_13_14
import com.restaurantpos.core.database.migration.MIGRATION_14_15
import com.restaurantpos.core.database.migration.MIGRATION_15_16
import com.restaurantpos.core.database.migration.MIGRATION_16_17
import com.restaurantpos.core.database.migration.MIGRATION_17_18
import com.restaurantpos.core.database.migration.MIGRATION_18_19
import com.restaurantpos.core.database.migration.MIGRATION_19_20
import com.restaurantpos.core.database.migration.MIGRATION_20_21
import com.restaurantpos.core.database.migration.MIGRATION_1_2
import com.restaurantpos.core.database.migration.MIGRATION_2_3
import com.restaurantpos.core.database.migration.MIGRATION_3_4
import com.restaurantpos.core.database.migration.MIGRATION_4_5
import com.restaurantpos.core.database.migration.MIGRATION_5_6
import com.restaurantpos.core.database.migration.MIGRATION_6_7
import com.restaurantpos.core.database.migration.MIGRATION_7_8
import com.restaurantpos.core.database.migration.MIGRATION_8_9
import com.restaurantpos.core.database.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests against the exported Room schemas (core/database/schemas).
 *
 * Covers F-009 regression (the 16→17 link was missing entirely) and the
 * pickup-code feature migration 19→20 with data preservation.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val allMigrations = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        MIGRATION_20_21,
    )

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PosDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate19To20_addsPickupColumns_andPreservesOrderData() {
        val dbName = "migration-19-20"
        helper.createDatabase(dbName, 19).apply {
            execSQL(
                """INSERT INTO orders (id, type, tableId, guestCount, sourceTerminalId, operatorId,
                   subtotalMinorUnit, taxTotalMinorUnit, serviceChargeMinorUnit, tipMinorUnit,
                   discountMinorUnit, status, createdAt, updatedAt, orderNotes, mergedTableIds)
                   VALUES ('o-keep', 'TAKEAWAY', NULL, 1, 'kiosk-1', '', 2500, 250, 0, 0, 0,
                   'PLACED', 1000, 2000, '', '')""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 20, true, MIGRATION_19_20)

        db.query("SELECT pickupCode, fulfillmentStatus, subtotalMinorUnit FROM orders WHERE id = 'o-keep'").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))                       // new column defaults to NULL
            assertEquals("NOT_READY", c.getString(1))        // new column default
            assertEquals(2500L, c.getLong(2))                // existing data preserved
        }
    }

    @Test
    fun migrate16To20_throughRepairedLink() {
        // F-009 regression: this chain crashed before MIGRATION_16_17 existed.
        val dbName = "migration-16-20"
        helper.createDatabase(dbName, 16).apply {
            execSQL(
                """INSERT INTO orders (id, type, tableId, guestCount, sourceTerminalId, operatorId,
                   subtotalMinorUnit, taxTotalMinorUnit, serviceChargeMinorUnit, tipMinorUnit,
                   discountMinorUnit, status, createdAt, updatedAt, orderNotes, mergedTableIds)
                   VALUES ('o-16', 'DINE_IN', 't1', 2, 'cashier-1', 'op1', 5000, 500, 0, 0, 0,
                   'CLOSED', 100, 200, '', '')""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 20, true,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        )

        db.query("SELECT subtotalMinorUnit, fulfillmentStatus FROM orders WHERE id = 'o-16'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(5000L, c.getLong(0))
            assertEquals("NOT_READY", c.getString(1))
        }
        // 17→18 seeded the default role matrix
        db.query("SELECT COUNT(*) FROM roles").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4L, c.getLong(0))
        }
    }

    @Test
    fun migrateFullChain1To21() {
        val dbName = "migration-1-21"
        helper.createDatabase(dbName, 1).close()

        // Validates the final schema matches 21.json exactly.
        helper.runMigrationsAndValidate(dbName, 21, true, *allMigrations)
    }

    @Test
    fun migrate20To21_addsUpdatedAt_andPreservesData_andBackfillsCustomer() {
        val dbName = "migration-20-21"
        helper.createDatabase(dbName, 20).apply {
            execSQL(
                """INSERT INTO tables (id, name, sectionId, capacity, currentOrderId, status)
                   VALUES ('t1', 'T1', 'indoor', 4, NULL, 'OCCUPIED')""",
            )
            execSQL(
                """INSERT INTO customers (id, name, phone, tags, totalSpendMinorUnit, loyaltyPoints,
                   totalVisits, lastVisitAt, registeredAt)
                   VALUES ('c1', '李明', '13800000000', '', 0, 0, 0, 0, 5000)""",
            )
            execSQL(
                """INSERT INTO reservations (id, tableId, guestName, guestCount, scheduledAt, notes, status)
                   VALUES ('r1', 't1', '王芳', 2, 9000, '', 'CONFIRMED')""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 21, true, MIGRATION_20_21)

        db.query("SELECT status, updatedAt FROM tables WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("OCCUPIED", c.getString(0)) // existing data preserved
            assertEquals(0L, c.getLong(1))            // new column default
        }
        db.query("SELECT updatedAt FROM customers WHERE id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(5000L, c.getLong(0))         // backfilled from registeredAt
        }
        db.query("SELECT guestName, updatedAt FROM reservations WHERE id = 'r1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("王芳", c.getString(0))
            assertEquals(0L, c.getLong(1))
        }
    }
}
