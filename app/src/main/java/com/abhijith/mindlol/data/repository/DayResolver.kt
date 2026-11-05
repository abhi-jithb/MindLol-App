package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.data.db.AppUsageEntity
import com.abhijith.mindlol.data.db.DailySummaryEntity
import com.abhijith.mindlol.data.db.UsageSource
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What we know about one calendar day after picking the best available source. */
data class ResolvedDay(
    val date: LocalDate,
    val totalMs: Long,
    val perAppMs: Map<String, Long>,
    val unlocks: Int?,
    /** True when the numbers come from raw events (exact), false when estimated or missing. */
    val precise: Boolean,
    /** False when no source covers this day at all. */
    val hasData: Boolean
)

/**
 * Picks, for each day, the most precise source that covers it:
 * EVENTS, then IMPORTED_DAILY, then a weekly, monthly or yearly bucket.
 * A multi-day bucket is spread evenly over its days (an estimate).
 * Pure Kotlin, so it is unit-tested on the JVM.
 */
object DayResolver {

    private val priority = listOf(
        UsageSource.EVENTS,
        UsageSource.IMPORTED_DAILY,
        UsageSource.IMPORTED_WEEKLY,
        UsageSource.IMPORTED_MONTHLY,
        UsageSource.IMPORTED_YEARLY
    )

    fun resolve(
        from: LocalDate,
        to: LocalDate,
        summaries: List<DailySummaryEntity>,
        apps: List<AppUsageEntity>
    ): List<ResolvedDay> {
        val appsByBucket = apps.groupBy { it.source to it.date }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
            val best = summaries
                .filter { !day.isBefore(it.date) && !day.isAfter(it.periodEnd) }
                .minByOrNull { priority.indexOf(it.source) }
            if (best == null) {
                ResolvedDay(day, 0, emptyMap(), unlocks = null, precise = false, hasData = false)
            } else {
                val bucketDays = ChronoUnit.DAYS.between(best.date, best.periodEnd) + 1
                val share = 1.0 / bucketDays
                ResolvedDay(
                    date = day,
                    totalMs = (best.totalMs * share).toLong(),
                    perAppMs = appsByBucket[best.source to best.date].orEmpty()
                        .associate { it.packageName to (it.foregroundMs * share).toLong() },
                    unlocks = if (bucketDays == 1L) best.unlocks else null,
                    precise = best.source == UsageSource.EVENTS,
                    hasData = true
                )
            }
        }.toList()
    }
}
