package com.abhijith.mindlol.data.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.abhijith.mindlol.data.db.AppUsageEntity
import com.abhijith.mindlol.data.db.DailySummaryEntity
import com.abhijith.mindlol.data.db.UsageDao
import com.abhijith.mindlol.data.db.UsageSource
import com.abhijith.mindlol.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs once, after usage access is granted: copies the older history Android still keeps
 * (roughly 7 days daily, 4 weeks weekly, 6 months monthly, 2 years yearly) into Room.
 *
 * These numbers are coarse (Android's buckets don't start at local midnight), so they are stored
 * with an IMPORTED_* source and never replace EVENTS rows.
 */
class HistoryImporter(
    context: Context,
    private val dao: UsageDao,
    private val prefs: AppPrefs,
    private val excludedPackages: () -> Set<String>
) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    suspend fun importOnce(zone: ZoneId = ZoneId.systemDefault()) {
        if (prefs.historyImported || usageStats == null) return
        // Dispatchers.IO: a background thread pool meant for disk and system calls.
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val eventDays = dao.eventDates().toSet()
            for ((interval, source, lookbackDays) in buckets) {
                val stats = usageStats.queryUsageStats(interval, now - lookbackDays * DAY_MS, now)
                // Android can return several entries for the same app and bucket; add them up.
                val perBucketApp = stats
                    .filter { it.totalTimeInForeground > 0 }
                    .groupBy { Triple(firstDay(it.firstTimeStamp, zone), lastDay(it.firstTimeStamp, it.lastTimeStamp, zone), it.packageName) }
                    .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
                    // A day we already measured precisely doesn't need the coarse copy.
                    .filterKeys { (start, _, _) -> !(source == UsageSource.IMPORTED_DAILY && start in eventDays) }

                val apps = perBucketApp.map { (key, ms) ->
                    AppUsageEntity(date = key.first, packageName = key.third, source = source, foregroundMs = ms, periodEnd = key.second)
                }
                val excluded = excludedPackages()
                val summaries = apps.groupBy { it.date to it.periodEnd }.map { (period, rows) ->
                    DailySummaryEntity(
                        date = period.first,
                        source = source,
                        // A plain sum: buckets don't give sessions, so overlaps can't be removed.
                        totalMs = rows.filter { it.packageName !in excluded }.sumOf { it.foregroundMs },
                        unlocks = null, // Android doesn't keep unlock history in these buckets
                        periodEnd = period.second
                    )
                }
                dao.insertAppUsageIfAbsent(apps)
                dao.insertSummariesIfAbsent(summaries)
            }
            prefs.historyImported = true
        }
    }

    /**
     * Android's buckets don't start at local midnight (on the test Vivo phone daily buckets run
     * 22:00 to 22:00 IST). Rounding each edge to the nearest local midnight puts a bucket on the
     * day that holds most of its hours. It stays coarse, which is why these rows are IMPORTED_*.
     */
    private fun nearestMidnightDate(ms: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(ms + HALF_DAY_MS).atZone(zone).toLocalDate()

    private fun firstDay(startMs: Long, zone: ZoneId): LocalDate = nearestMidnightDate(startMs, zone)

    /** Last day inside the bucket (inclusive). Never before the first day, e.g. for today's partial bucket. */
    private fun lastDay(startMs: Long, endMs: Long, zone: ZoneId): LocalDate =
        maxOf(firstDay(startMs, zone), nearestMidnightDate(endMs, zone).minusDays(1))

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val HALF_DAY_MS = DAY_MS / 2

        // (Android interval, our source, how far back to ask). Asking a bit beyond retention is harmless.
        val buckets = listOf(
            Triple(UsageStatsManager.INTERVAL_DAILY, UsageSource.IMPORTED_DAILY, 10L),
            Triple(UsageStatsManager.INTERVAL_WEEKLY, UsageSource.IMPORTED_WEEKLY, 35L),
            Triple(UsageStatsManager.INTERVAL_MONTHLY, UsageSource.IMPORTED_MONTHLY, 200L),
            Triple(UsageStatsManager.INTERVAL_YEARLY, UsageSource.IMPORTED_YEARLY, 800L)
        )
    }
}
