package com.abhijith.mindlol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * All database reads and writes for usage. Room generates the code from these annotations.
 * `suspend` functions run once; functions returning Flow keep emitting whenever the table changes.
 */
@Dao
interface UsageDao {

    // ---------- Writes ----------

    @Upsert
    suspend fun upsertSummaries(rows: List<DailySummaryEntity>)

    @Upsert
    suspend fun upsertAppUsage(rows: List<AppUsageEntity>)

    /** Adds rows only where the key is free. Used by the import, so it never overwrites anything. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSummariesIfAbsent(rows: List<DailySummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppUsageIfAbsent(rows: List<AppUsageEntity>)

    @Query("DELETE FROM app_usage WHERE date = :date AND source = 'EVENTS'")
    suspend fun deleteEventAppUsage(date: LocalDate)

    /**
     * Replaces one day's EVENTS rows. Deleting first drops apps that no longer appear.
     * @Transaction makes it all-or-nothing, so running it twice gives the same result.
     */
    @Transaction
    suspend fun replaceEventDay(summary: DailySummaryEntity, apps: List<AppUsageEntity>) {
        deleteEventAppUsage(summary.date)
        upsertSummaries(listOf(summary))
        upsertAppUsage(apps)
    }

    // ---------- Reads ----------

    @Query("SELECT date FROM daily_summary WHERE source = 'EVENTS'")
    suspend fun eventDates(): List<LocalDate>

    @Query("SELECT * FROM daily_summary WHERE periodEnd >= :from AND date <= :to ORDER BY date")
    fun summariesOverlapping(from: LocalDate, to: LocalDate): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM app_usage WHERE periodEnd >= :from AND date <= :to")
    fun appUsageOverlapping(from: LocalDate, to: LocalDate): Flow<List<AppUsageEntity>>

    @Query("SELECT COUNT(*) FROM daily_summary")
    fun summaryCount(): Flow<Int>
}
