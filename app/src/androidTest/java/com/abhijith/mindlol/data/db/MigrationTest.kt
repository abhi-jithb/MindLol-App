package com.abhijith.mindlol.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the upgrades on a real device: builds an old database from its exported schema file
 * (1.json, 2.json), runs the AutoMigrations, then checks the result matches the newer schema
 * and the old rows survived.
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

    @Test
    fun migrate2To3_keepsCheatsAndSnapshots_andAddsLockTables() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO judged_period (kind, date, startMillis, endMillis, overrideApplied, lockedApps, limits, finalized) VALUES ('NIGHT', '2026-11-05', 1, 2, 0, 'com.instagram.android', '', 1)")
            execSQL("INSERT INTO cheat (packageName, type, periodDate, firstSeenStartMillis, startMillis, endMillis, minutes) VALUES ('com.instagram.android', 'BEDTIME', '2026-11-05', 1, 1, 2, 7)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true)
        db.query("SELECT minutes FROM cheat").use {
            it.moveToFirst()
            assertEquals(7, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM judged_period").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        // The new tables exist, are empty, and accept rows.
        db.execSQL("INSERT INTO lock_session (connectedAtMillis, lastAliveMillis, disconnectedAtMillis, bootAtMillis) VALUES (10, 20, NULL, 5)")
        db.execSQL("INSERT INTO lock_event (packageName, reason, periodDate, shownAtMillis, stepReached, outcome, countdownSeconds, grantedMinutes, grantedAtMillis, cardId, cardAnswer) VALUES ('com.instagram.android', 'BEDTIME', '2026-11-05', 30, 1, 'WENT_BACK', 10, NULL, NULL, NULL, NULL)")
        db.query("SELECT (SELECT COUNT(*) FROM lock_session), (SELECT COUNT(*) FROM lock_event)").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(1, it.getInt(1))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
