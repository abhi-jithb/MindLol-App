package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.domain.PermissionInfo
import com.abhijith.mindlol.domain.SettingsValues
import com.abhijith.mindlol.domain.Streak
import com.abhijith.mindlol.domain.TotalStat

/**
 * The values that are still fake: reels, the reels streak, screen-time totals, life-cost lines and
 * settings. Usage numbers moved to UsageRepository in Phase 2; the rest becomes real in Phases 3–4.
 */
object FakeData {

    // ---------- Home extras (Phase 3–4 will make these real) ----------

    val reelsToday = 87

    // ---------- Profile ----------

    val streaks = listOf(
        Streak("No reels before 10 AM", currentDays = 1, bestDays = 4)
    )

    val trackingSince = "12 Oct 2026"

    val allTimeTotals = listOf(
        TotalStat("Screen time", "81h"),
        TotalStat("Reels watched", "4,120"),
        TotalStat("Phone pickups", "2,980")
    )

    // A reel is counted as about 20 seconds.
    val lifeCosts = listOf(
        "412 reels ≈ one movie you didn't watch",
        "4,120 reels ≈ 23 hours of strangers' lives",
        "81 hours ≈ 10 working days, given to glass"
    )

    // ---------- Settings ----------

    val settings = SettingsValues(
        permissions = listOf(
            PermissionInfo("Notifications", "To roast you when you cheat", "Not granted"),
            PermissionInfo("Display over other apps", "To show the lock screen", "Not needed yet")
        ),
        themeOptions = listOf("Pure black", "Soft black", "High contrast"),
        selectedTheme = 0
    )
}
