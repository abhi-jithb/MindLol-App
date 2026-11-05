package com.abhijith.mindlol.domain

import java.time.LocalTime

// Plain data shapes the screens draw from.
// Phase 1 fills them from FakeData; Phase 2 fills the same shapes with real usage.
// All durations are in whole minutes.

data class AppUsage(
    val appName: String,
    val minutes: Int
)

data class TodaySummary(
    val totalMinutes: Int,
    val reelsCount: Int,
    val cheatsToday: Int,
    val topApps: List<AppUsage>
)

enum class StatsPeriod(val label: String, val bucketName: String) {
    DAY("Day", "hour"),
    WEEK("Week", "day"),
    MONTH("Month", "day"),
    YEAR("Year", "month")
}

/** One bar per bucket: hours of a day, days of a week or month, months of a year. */
data class UsageSeries(
    val period: StatsPeriod,
    val labels: List<String>,
    val minutes: List<Int>,
    val apps: List<AppUsage>
) {
    val totalMinutes: Int get() = minutes.sum()

    /** Average over buckets that had any use, so empty night hours don't drag it down. */
    val averageMinutes: Int
        get() = minutes.filter { it > 0 }.let { used -> if (used.isEmpty()) 0 else used.sum() / used.size }

    val peakIndex: Int get() = minutes.indices.maxByOrNull { minutes[it] } ?: 0
}

/** One broken rule, with the line the app roasts you with. */
data class CheatEntry(
    val day: String,
    val time: String,
    val appName: String,
    val roast: String
)

data class Streak(
    val name: String,
    val currentDays: Int,
    val bestDays: Int
)

data class TotalStat(
    val label: String,
    val value: String
)

data class AppRule(
    val appName: String,
    val dailyLimitMinutes: Int?, // null means no limit
    val lockedAtBedtime: Boolean
)

data class PermissionInfo(
    val name: String,
    val reason: String,
    val status: String
)

data class SettingsValues(
    val wakeTime: LocalTime,
    val bedTime: LocalTime,
    val appRules: List<AppRule>,
    val permissions: List<PermissionInfo>,
    val themeOptions: List<String>,
    val selectedTheme: Int
)
