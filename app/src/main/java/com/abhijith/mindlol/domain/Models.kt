package com.abhijith.mindlol.domain

// Plain data shapes the screens draw from.
// Phase 1 fills them from FakeData; Phase 2 fills the same shapes with real usage.
// All durations are in whole minutes.

data class AppUsage(
    val appName: String,
    val minutes: Int
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
    val apps: List<AppUsage>,
    /** Longer names for the same buckets ("Jul" instead of "J"), for text outside the chart. */
    val fullLabels: List<String> = labels,
    /** True when some days come from Android's coarse summaries instead of exact events. */
    val hasEstimates: Boolean = false,
    /**
     * Exact total, when known. Summing [minutes] loses the seconds each bar drops when rounded
     * down (up to 23 minutes for the 24 hourly bars), so real data passes the exact number.
     */
    val exactTotalMinutes: Int? = null
) {
    val totalMinutes: Int get() = exactTotalMinutes ?: minutes.sum()

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

/** One app's rules, as Settings shows them. Stored by package name; [appName] is for display. */
data class AppRule(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int?, // null means no limit
    val lockedAtBedtime: Boolean
)

data class PermissionInfo(
    val name: String,
    val reason: String,
    val status: String
)

/** The parts of Settings that are still fake. The real rules live in [Settings]. */
data class SettingsValues(
    val permissions: List<PermissionInfo>,
    val themeOptions: List<String>,
    val selectedTheme: Int
)
