package com.abhijith.mindlol.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-device SQLite database. All data stays on the phone.
 * exportSchema writes app/schemas/<version>.json, committed to git for future migrations.
 *
 * v2 adds the judged_period and cheat tables; v3 adds lock_event and lock_session (the live lock).
 * Both only add tables, so Room writes the migrations itself (AutoMigration) by comparing the
 * exported schema files. Existing rows are untouched.
 */
@Database(
    entities = [
        AppUsageEntity::class, DailySummaryEntity::class, JudgedPeriodEntity::class, CheatEntity::class,
        LockEventEntity::class, LockSessionEntity::class
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
@TypeConverters(Converters::class)
abstract class MindLolDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun rulesDao(): RulesDao
    abstract fun lockDao(): LockDao

    companion object {
        fun build(context: Context): MindLolDatabase =
            Room.databaseBuilder(context, MindLolDatabase::class.java, "mindlol.db").build()
    }
}
