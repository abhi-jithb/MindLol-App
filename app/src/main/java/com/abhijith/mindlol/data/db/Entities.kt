package com.abhijith.mindlol.data.db

import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDate

/** Where a row's numbers came from. */
enum class UsageSource {
    /** Built from raw RESUMED/PAUSED events. Precise, available from install day on. */
    EVENTS,

    /** Imported once from Android's own summaries. Coarse: bucket edges don't match local midnight. */
    IMPORTED_DAILY,
    IMPORTED_WEEKLY,
    IMPORTED_MONTHLY,
    IMPORTED_YEARLY
}

/**
 * Foreground time of one app over one period.
 * For EVENTS and IMPORTED_DAILY the period is a single day ([date] == [periodEnd]).
 * For the other imported sources, [date] is the first day of the bucket and [periodEnd] the last.
 */
@Entity(
    tableName = "app_usage",
    primaryKeys = ["date", "packageName", "source"],
    indices = [Index("date")]
)
data class AppUsageEntity(
    val date: LocalDate,
    val packageName: String,
    val source: UsageSource,
    val foregroundMs: Long,
    val periodEnd: LocalDate
)

/**
 * Totals for one period. [unlocks] is null when unknown (imported rows, or Android below 9).
 * Same period rules as [AppUsageEntity].
 */
@Entity(
    tableName = "daily_summary",
    primaryKeys = ["date", "source"]
)
data class DailySummaryEntity(
    val date: LocalDate,
    val source: UsageSource,
    val totalMs: Long,
    val unlocks: Int?,
    val periodEnd: LocalDate
)
