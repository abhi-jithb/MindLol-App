package com.abhijith.mindlol.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/** A day of usage as read from the phone. [unlocks] is null when Android can't report it (below API 28). */
data class DailyUsageReading(
    val date: LocalDate,
    val perAppMs: Map<String, Long>,
    val totalMs: Long,
    val unlocks: Int?
)

/**
 * Reads raw usage events from Android and turns them into per-day numbers with [SessionPairing].
 *
 * We deliberately don't use UsageStats.totalTimeInForeground for daily numbers: its buckets
 * don't line up with local midnight and can include time from neighbouring days.
 */
class UsageEventsReader(private val context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    /**
     * Packages that never appear in rankings or in the daily total:
     * - the home-screen launcher (time on the home screen isn't "using an app"),
     * - MindLol itself (checking your screen time shouldn't count against you).
     * Their raw time is still stored per app; the repository filters them out.
     */
    val excludedPackages: Set<String> by lazy { setOf(context.packageName) + launcherPackages() }

    /**
     * Usage for each day from [firstDay] to [lastDay] (inclusive) in [zone].
     * Today stops at [nowMs]. Android keeps events for only about a week, so older days come back empty.
     */
    fun readDays(
        firstDay: LocalDate,
        lastDay: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis()
    ): List<DailyUsageReading> {
        val rangeStart = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = minOf(nowMs, lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        // Start reading 6 hours early, so an app opened before midnight is paired correctly.
        val events = readEvents(rangeStart - LEAD_IN_MS, rangeEnd)
        val unlocksSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        return SessionPairing.dailyUsage(events, rangeStart, rangeEnd, zone, excludedPackages).map {
            DailyUsageReading(
                date = it.date,
                perAppMs = it.perAppMs,
                totalMs = it.totalMs,
                unlocks = if (unlocksSupported) it.unlocks else null // unknown, not zero
            )
        }
    }

    /** Screen time per hour for [day] (24 values), up to [nowMs]. Computed live from events. */
    fun readHours(
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis()
    ): List<Long> {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = minOf(nowMs, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val events = readEvents(dayStart - LEAD_IN_MS, dayEnd)
        return SessionPairing.hourlyTotals(events, dayStart, dayEnd, zone, excludedPackages)
    }

    private fun readEvents(startMs: Long, endMs: Long): List<UsageEventRecord> {
        val result = mutableListOf<UsageEventRecord>()
        val events = usageStats?.queryEvents(startMs, endMs) ?: return result
        val event = UsageEvents.Event() // reused for every event, as the API intends
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> EventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> EventKind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN -> EventKind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> EventKind.UNLOCK
                else -> null
            } ?: continue
            result += UsageEventRecord(event.packageName, event.className ?: "", event.timeStamp, kind)
        }
        return result
    }

    private fun launcherPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            // Settings registers a "FallbackHome" that only shows while the phone boots.
            // It isn't a real launcher, so time in Settings must still count.
            .filterNot { it.activityInfo.name.endsWith("FallbackHome") }
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private companion object {
        const val LEAD_IN_MS = 6 * 60 * 60 * 1000L
    }
}
