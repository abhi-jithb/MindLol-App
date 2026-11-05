package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.data.db.AppUsageEntity
import com.abhijith.mindlol.data.db.DailySummaryEntity
import com.abhijith.mindlol.data.db.UsageDao
import com.abhijith.mindlol.data.db.UsageSource
import com.abhijith.mindlol.data.usage.AppNames
import com.abhijith.mindlol.data.usage.HistoryImporter
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.data.usage.UsagePermission
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.domain.UsageSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Today's real numbers for Home. */
data class TodayUsage(
    val totalMinutes: Int,
    val unlocks: Int?,
    val topApps: List<AppUsage>,
    val hasData: Boolean
)

/**
 * The single source of usage numbers for every screen.
 * Writes: [refresh] reads events from Android into Room. Reads: Flows from Room, which
 * emit again automatically whenever a refresh changes the tables.
 */
class UsageRepository(
    private val dao: UsageDao,
    private val reader: UsageEventsReader,
    private val importer: HistoryImporter,
    private val appNames: AppNames,
    private val permission: UsagePermission
) {
    // Only one refresh at a time (app open and the background worker can overlap).
    private val refreshLock = Mutex()

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun today(): LocalDate = LocalDate.now(zone)

    /**
     * Imports old history once, then rebuilds the last [daysBack] days from raw events.
     * Safe to run any number of times: each day's EVENTS rows are replaced, not added to.
     */
    suspend fun refresh(daysBack: Int = 3) = withContext(Dispatchers.IO) {
        if (!permission.check()) return@withContext
        refreshLock.withLock {
            importer.importOnce(zone)
            val end = today()
            reader.readDays(end.minusDays(daysBack - 1L), end, zone).forEach { day ->
                dao.replaceEventDay(
                    summary = DailySummaryEntity(day.date, UsageSource.EVENTS, day.totalMs, day.unlocks, day.date),
                    apps = day.perAppMs.filterValues { it > 0 }.map { (pkg, ms) ->
                        AppUsageEntity(day.date, pkg, UsageSource.EVENTS, ms, day.date)
                    }
                )
            }
        }
    }

    /** True once any usage row exists. Used for the first-launch empty state. */
    fun hasAnyData(): Flow<Boolean> = dao.summaryCount().map { it > 0 }

    fun todayUsage(): Flow<TodayUsage> = resolvedDays(today(), today()).map { days ->
        val day = days.single()
        TodayUsage(
            totalMinutes = (day.totalMs / MINUTE_MS).toInt(),
            unlocks = day.unlocks,
            topApps = rankApps(day.perAppMs).take(3),
            hasData = day.hasData
        )
    }

    fun series(period: StatsPeriod): Flow<UsageSeries> {
        val end = today()
        return when (period) {
            StatsPeriod.DAY -> resolvedDays(end, end).map { days ->
                val day = days.single()
                val hours = reader.readHours(end, zone) // live from events; Room has no hourly data
                UsageSeries(
                    period = period,
                    labels = (0..23).map { hourLabel(it, short = true) },
                    minutes = hours.map { (it / MINUTE_MS).toInt() },
                    apps = rankApps(day.perAppMs).take(TOP_APPS),
                    fullLabels = (0..23).map { hourLabel(it, short = false) },
                    hasEstimates = day.hasData && !day.precise,
                    exactTotalMinutes = (day.totalMs / MINUTE_MS).toInt()
                )
            }
            StatsPeriod.WEEK -> daySeries(period, end.minusDays(6), end) { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            StatsPeriod.MONTH -> daySeries(period, end.minusDays(29), end) { "${it.dayOfMonth}" }
            StatsPeriod.YEAR -> {
                val firstMonth = YearMonth.from(end).minusMonths(11)
                resolvedDays(firstMonth.atDay(1), end).map { days ->
                    val byMonth = days.groupBy { YearMonth.from(it.date) }
                    val months = (0L..11L).map { firstMonth.plusMonths(it) }
                    UsageSeries(
                        period = period,
                        labels = months.map { it.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                        minutes = months.map { m -> (byMonth[m].orEmpty().sumOf { it.totalMs } / MINUTE_MS).toInt() },
                        apps = rankApps(sumApps(days)).take(TOP_APPS),
                        fullLabels = months.map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                        hasEstimates = days.any { it.hasData && !it.precise },
                        exactTotalMinutes = (days.sumOf { it.totalMs } / MINUTE_MS).toInt()
                    )
                }
            }
        }
    }

    private fun daySeries(
        period: StatsPeriod,
        from: LocalDate,
        to: LocalDate,
        label: (LocalDate) -> String
    ): Flow<UsageSeries> = resolvedDays(from, to).map { days ->
        UsageSeries(
            period = period,
            labels = days.map { label(it.date) },
            minutes = days.map { (it.totalMs / MINUTE_MS).toInt() },
            apps = rankApps(sumApps(days)).take(TOP_APPS),
            fullLabels = days.map { "${it.date.dayOfMonth} ${it.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" },
            hasEstimates = days.any { it.hasData && !it.precise },
            exactTotalMinutes = (days.sumOf { it.totalMs } / MINUTE_MS).toInt()
        )
    }

    /** Both tables for the range, combined into one resolved list whenever either changes. */
    private fun resolvedDays(from: LocalDate, to: LocalDate): Flow<List<ResolvedDay>> =
        combine(dao.summariesOverlapping(from, to), dao.appUsageOverlapping(from, to)) { summaries, apps ->
            DayResolver.resolve(from, to, summaries, apps)
        }.flowOn(Dispatchers.IO) // resolving and reading app names happen off the main thread

    private fun sumApps(days: List<ResolvedDay>): Map<String, Long> {
        val sums = mutableMapOf<String, Long>()
        days.forEach { day -> day.perAppMs.forEach { (pkg, ms) -> sums[pkg] = (sums[pkg] ?: 0L) + ms } }
        return sums
    }

    /**
     * Biggest apps first, with readable names. The launcher and MindLol itself are left out
     * (see UsageEventsReader.excludedPackages), and anything under a minute is dropped.
     */
    private fun rankApps(perAppMs: Map<String, Long>): List<AppUsage> = perAppMs
        .filterKeys { it !in reader.excludedPackages }
        .map { (pkg, ms) -> AppUsage(appNames.labelFor(pkg), (ms / MINUTE_MS).toInt()) }
        .filter { it.minutes > 0 }
        .sortedByDescending { it.minutes }

    private fun hourLabel(hour: Int, short: Boolean): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return if (short) "$h12${if (hour < 12) "a" else "p"}" else "$h12 ${if (hour < 12) "AM" else "PM"}"
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val TOP_APPS = 6
    }
}
