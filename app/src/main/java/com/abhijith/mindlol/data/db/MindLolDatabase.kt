package com.abhijith.mindlol.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-device SQLite database. All data stays on the phone.
 * exportSchema writes app/schemas/<version>.json, committed to git for future migrations.
 */
@Database(
    entities = [AppUsageEntity::class, DailySummaryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MindLolDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        fun build(context: Context): MindLolDatabase =
            Room.databaseBuilder(context, MindLolDatabase::class.java, "mindlol.db").build()
    }
}
