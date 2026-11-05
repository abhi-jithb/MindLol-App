package com.abhijith.mindlol.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the v1 → v2 upgrade on a real device: builds a v1 database from 1.json, runs the
 * AutoMigration, then checks the result matches 2.json and the old rows survived.
 * Uses its own test database file, never the app's mindlol.db.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MindLolDatabase::class.java)

    @Test
    fun migrate1To2_keepsUsageRows_andAddsCheatTables() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO daily_summary (date, source, totalMs, unlocks, periodEnd) VALUES ('2026-11-05', 'EVENTS', 3600000, 12, '2026-11-05')")
            execSQL("INSERT INTO app_usage (date, packageName, source, foregroundMs, periodEnd) VALUES ('2026-11-05', 'com.instagram.android', 'EVENTS', 1800000, '2026-11-05')")
            close()
        }

        // validateDroppedTables = true: fails if the v2 schema doesn't exactly match 2.json.
        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT totalMs, unlocks FROM daily_summary WHERE date = '2026-11-05'").use {
            it.moveToFirst()
            assertEquals(3_600_000L, it.getLong(0))
            assertEquals(12, it.getInt(1))
        }
        db.query("SELECT foregroundMs FROM app_usage").use {
            it.moveToFirst()
            assertEquals(1_800_000L, it.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM judged_period").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun cheatUniqueKey_blocksDuplicates() {
        helper.createDatabase(DB, 1).close()
        val db = helper.runMigrationsAndValidate(DB, 2, true)
        val insert = "INSERT OR IGNORE INTO cheat (packageName, type, periodDate, firstSeenStartMillis, startMillis, endMillis, minutes) " +
            "VALUES ('com.instagram.android', 'LIMIT', '2026-11-05', 0, %d, %d, %d)"
        db.execSQL(insert.format(1000L, 2000L, 5))
        db.execSQL(insert.format(1500L, 3000L, 9)) // same app, type, day: ignored
        db.query("SELECT COUNT(*), MIN(minutes) FROM cheat").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(5, it.getInt(1))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
