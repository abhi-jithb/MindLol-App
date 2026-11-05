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
 * v2 adds the judged_period and cheat tables. It only adds tables, so Room can write the
 * migration itself (AutoMigration) by comparing 1.json with 2.json. Existing usage rows are untouched.
 */
@Database(
    entities = [AppUsageEntity::class, DailySummaryEntity::class, JudgedPeriodEntity::class, CheatEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@TypeConverters(Converters::class)
abstract class MindLolDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun rulesDao(): RulesDao

    companion object {
        fun build(context: Context): MindLolDatabase =
            Room.databaseBuilder(context, MindLolDatabase::class.java, "mindlol.db").build()
    }
}
