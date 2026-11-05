package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.session
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.wed
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesPlannerTest {

    private val lockedInsta = Settings(lockedApps = setOf(INSTA))

    private fun plan(
        now: Long,
        settings: Settings,
        stored: List<RulesSnapshot> = emptyList(),
        storedCheats: List<Cheat> = emptyList(),
        sessions: List<AppSession> = emptyList(),
        firstDate: LocalDate = wed,
        rulesSince: Long = 0L // rules existed "forever", unless a test says otherwise
    ) = RulesPlanner.plan(now, zone, settings, firstDate, stored, storedCheats, sessions, rulesSince)

    private fun PlanResult.snapshot(kind: PeriodKind, date: LocalDate) =
        snapshots.firstOrNull { it.kind == kind && it.date == date }

    // ---------- Snapshots written ahead of time ----------

    @Test
    fun upcoming_writesNextNightAndTomorrow_fromCurrentSettings() {
        val settings = Settings(lockedApps = setOf(INSTA), appLimits = mapOf(INSTA to 30))
        val rows = RulesPlanner.upcomingSnapshots(at(thu, 15), zone, settings, emptyList())

        val tonight = rows.single { it.kind == PeriodKind.NIGHT && it.date == thu }
        assertEquals(at(thu, 23), tonight.startMillis)
        assertEquals(setOf(INSTA), tonight.lockedApps)
        val tomorrow = rows.single { it.kind == PeriodKind.DAY && it.date == fri }
        assertEquals(mapOf(INSTA to 30), tomorrow.limits)
        // Already started (last night, today): not "upcoming", never rewritten here.
        assertTrue(rows.none { it.kind == PeriodKind.NIGHT && it.date == wed })
        assertTrue(rows.none { it.kind == PeriodKind.DAY && it.date == thu })
    }

    @Test
    fun snapshotWrittenBeforeNight_usedWithoutRefreshDuringNight() {
        // Thursday 21:00: Instagram locked, tonight's row is written.
        val written = RulesPlanner.upcomingSnapshots(at(thu, 21), zone, lockedInsta, emptyList())
        // Friday noon, first refresh since then. Instagram was unlocked this morning.
        val result = plan(
            now = at(fri, 12),
            settings = Settings(lockedApps = emptySet()),
            stored = written,
            sessions = listOf(session(INSTA, at(fri, 1), at(fri, 1, 20)))
        )
        assertEquals(1, result.cheats.size)
        assertTrue(result.snapshot(PeriodKind.NIGHT, thu)!!.finalized)
    }

    // ---------- "Once a period starts, its rules are locked" ----------

    @Test
    fun changingBedtimeDuringNight_doesNotChangeTonightsWindow() {
        val tonight = NightCalendar.night(thu, lockedInsta, zone) // 23:00 → 08:00, saved before bedtime
        val later = lockedInsta.copy(bedtimeMinutes = 23 * 60 + 45) // changed at 23:30

        assertTrue(RulesPlanner.upcomingSnapshots(at(thu, 23, 30), zone, later, listOf(tonight))
            .none { it.kind == PeriodKind.NIGHT && it.date == thu })

        val result = plan(
            now = at(thu, 23, 40),
            settings = later,
            stored = listOf(tonight),
            sessions = listOf(session(INSTA, at(thu, 23, 32), at(thu, 23, 38)))
        )
        assertEquals(at(thu, 23), result.snapshot(PeriodKind.NIGHT, thu)!!.startMillis)
        assertEquals(1, result.cheats.size) // 23:32 counts: tonight still started at 23:00
    }

    @Test
    fun limitsChangedToday_applyFromTomorrow() {
        val todayRow = NightCalendar.day(thu, Settings(appLimits = mapOf(INSTA to 30)), zone) // frozen at midnight
        val raised = Settings(appLimits = mapOf(INSTA to 120))

        val result = plan(
            now = at(thu, 18),
            settings = raised,
            stored = listOf(todayRow),
            sessions = listOf(session(INSTA, at(thu, 10), at(thu, 11)))
        )
        assertEquals(CheatType.LIMIT, result.cheats.single().type) // 60 min > today's 30
        assertEquals(mapOf(INSTA to 30), result.snapshot(PeriodKind.DAY, thu)!!.limits)
        assertEquals(mapOf(INSTA to 120), result.snapshot(PeriodKind.DAY, fri)!!.limits)
    }

    @Test
    fun bedtimeMovedEarlierThanNow_startsWindowNow_notRetroactively() {
        val saved = NightCalendar.night(thu, lockedInsta, zone) // 23:00, not started yet at 22:30
        val earlier = lockedInsta.copy(bedtimeMinutes = 22 * 60)

        val rows = RulesPlanner.upcomingSnapshots(at(thu, 22, 30), zone, earlier, listOf(saved))
        val tonight = rows.single { it.kind == PeriodKind.NIGHT && it.date == thu }
        assertEquals(at(thu, 22, 30), tonight.startMillis)

        val result = plan(
            now = at(thu, 22, 50),
            settings = earlier,
            stored = rows,
            sessions = listOf(
                session(INSTA, at(thu, 22, 10), at(thu, 22, 20)), // before the change: not a cheat
                session(INSTA, at(thu, 22, 40), at(thu, 22, 45))
            )
        )
        assertEquals(at(thu, 22, 40), result.cheats.single().startMillis)
    }

    // ---------- The tonight override ----------

    @Test
    fun overrideSetAfterBedtime_isRejected() {
        val tonight = NightCalendar.night(thu, Settings(), zone)
        assertTrue(RulesPlanner.isTonightLocked(at(fri, 1), zone, Settings(), listOf(tonight)))
        assertTrue(RulesPlanner.isTonightLocked(at(thu, 23, 30), zone, Settings(), emptyList()))
    }

    @Test
    fun overrideSetBeforeBedtime_isAccepted() {
        val now = at(thu, 22)
        assertFalse(RulesPlanner.isTonightLocked(now, zone, Settings(), emptyList()))

        val override = TonightOverride(2 * 60, 10 * 60, SleepTime.overrideExpiryMillis(now, 10 * 60, zone))
        val rows = RulesPlanner.upcomingSnapshots(now, zone, Settings(tonightOverride = override), emptyList())
        val tonight = rows.single { it.kind == PeriodKind.NIGHT && it.date == thu }
        assertTrue(tonight.overrideApplied)
        assertEquals(at(fri, 2), tonight.startMillis)
        assertEquals(at(fri, 10), tonight.endMillis)
    }

    @Test
    fun overrideNight_judgedWithOverride_afterOverrideExpired() {
        val setAt = at(thu, 20)
        val override = TonightOverride(2 * 60, 10 * 60, SleepTime.overrideExpiryMillis(setAt, 10 * 60, zone))
        val written = RulesPlanner.upcomingSnapshots(setAt, zone, lockedInsta.copy(tonightOverride = override), emptyList())

        // Friday 15:00: the override has expired and was even turned off.
        val result = plan(
            now = at(fri, 15),
            settings = lockedInsta,
            stored = written,
            sessions = listOf(
                session(INSTA, at(thu, 23, 30), at(thu, 23, 50)), // inside the normal window, outside the override
                session(INSTA, at(fri, 9), at(fri, 9, 30))        // outside normal, inside the override
            )
        )
        val cheat = result.cheats.single()
        assertEquals(at(fri, 9), cheat.startMillis)
        assertTrue(result.snapshot(PeriodKind.NIGHT, thu)!!.overrideApplied)
    }

    // ---------- "Once a period has ended, its result is final" ----------

    @Test
    fun changingBedtimeToday_doesNotChangeYesterdaysCheats() {
        val wedNight = NightCalendar.night(wed, lockedInsta, zone).copy(finalized = true)
        val savedCheat = Cheat(INSTA, CheatType.BEDTIME, wed, at(wed, 23, 30), at(wed, 23, 50), 20)
        val laterBedtime = lockedInsta.copy(bedtimeMinutes = 0) // midnight now; 23:30 would no longer count

        val result = plan(
            now = at(thu, 15),
            settings = laterBedtime,
            stored = listOf(wedNight),
            storedCheats = listOf(savedCheat),
            sessions = listOf(session(INSTA, at(wed, 23, 30), at(wed, 23, 50)), session(INSTA, at(thu, 0, 10), at(thu, 0, 20)))
        )
        assertNull(result.snapshot(PeriodKind.NIGHT, wed))
        assertTrue(result.cheats.none { it.periodDate == wed })
    }

    @Test
    fun finalizedPeriod_isSkipped() {
        val wedNight = NightCalendar.night(wed, lockedInsta, zone).copy(finalized = true)
        val result = plan(
            now = at(thu, 15),
            settings = lockedInsta,
            stored = listOf(wedNight),
            sessions = listOf(session(INSTA, at(thu, 1), at(thu, 2))) // a cheat, but the night is final
        )
        assertTrue(result.cheats.isEmpty())
    }

    @Test
    fun endedPeriod_isFinalizedWithItsStoredSnapshot() {
        val wedNight = NightCalendar.night(wed, lockedInsta, zone) // ended this morning, not finalized yet
        val result = plan(
            now = at(thu, 12),
            settings = Settings(lockedApps = emptySet()), // unlocked since
            stored = listOf(wedNight),
            sessions = listOf(session(INSTA, at(thu, 1), at(thu, 1, 10)))
        )
        val row = result.snapshot(PeriodKind.NIGHT, wed)!!
        assertTrue(row.finalized)
        assertEquals(setOf(INSTA), row.lockedApps)
        assertEquals(1, result.cheats.size)
    }

    @Test
    fun runningNight_staysOpen_andOpenSessionCountsUntilNow() {
        val tonight = NightCalendar.night(thu, lockedInsta, zone)
        val result = plan(
            now = at(fri, 1),
            settings = lockedInsta,
            stored = listOf(tonight),
            sessions = listOf(session(INSTA, at(fri, 0, 30), at(fri, 5))) // still open
        )
        assertFalse(result.snapshot(PeriodKind.NIGHT, thu)!!.finalized)
        val cheat = result.cheats.single()
        assertEquals(at(fri, 1), cheat.endMillis)
        assertEquals(30, cheat.minutes)
    }

    @Test
    fun startedPeriodWithNoRow_fallsBackToCurrentSettings() {
        val result = plan(
            now = at(fri, 12),
            settings = lockedInsta,
            firstDate = thu,
            sessions = listOf(session(INSTA, at(fri, 1), at(fri, 1, 5)))
        )
        assertEquals(thu, result.cheats.single().periodDate)
        assertTrue(result.snapshot(PeriodKind.NIGHT, thu)!!.finalized)
    }

    // ---------- Re-syncs never duplicate ----------

    @Test
    fun samePlanTwice_givesSameCheatKeys() {
        val tonight = NightCalendar.night(thu, lockedInsta, zone)
        val sessions = listOf(session(INSTA, at(fri, 0, 30), at(fri, 0, 50)))
        val first = plan(at(fri, 1), lockedInsta, listOf(tonight), emptyList(), sessions)
        val second = plan(at(fri, 1), lockedInsta, first.snapshots, first.cheats, sessions)
        assertEquals(1, first.cheats.size)
        assertTrue(second.cheats.isEmpty()) // nothing new to save
    }

    @Test
    fun openSessionAtRefresh_thenClosed_noDuplicateBedtimeCheat() {
        val tonight = NightCalendar.night(thu, lockedInsta, zone)
        // 01:00 refresh: Instagram open since 00:30.
        val first = plan(at(fri, 1), lockedInsta, listOf(tonight), emptyList(),
            listOf(session(INSTA, at(fri, 0, 30), at(fri, 1))))
        // 02:00 refresh: the session closed at 01:20.
        val second = plan(at(fri, 2), lockedInsta, first.snapshots, first.cheats,
            listOf(session(INSTA, at(fri, 0, 30), at(fri, 1, 20))))

        val updated = second.cheats.single()
        assertEquals(first.cheats.single().key, updated.key)
        assertEquals(at(fri, 1, 20), updated.endMillis)
        assertEquals(50, updated.minutes)
    }

    @Test
    fun bedtimeCheatStartSeenEarlierOnLaterRefresh_extendsExisting() {
        val saved = Cheat(INSTA, CheatType.BEDTIME, thu, at(fri, 1), at(fri, 1, 10), 10)
        val found = Cheat(INSTA, CheatType.BEDTIME, thu, at(fri, 0, 40), at(fri, 1, 10), 30)

        val merged = RulesPlanner.mergeWithSaved(listOf(found), listOf(saved)).single()
        assertEquals(saved.key, merged.key) // key keeps the first-seen start
        assertEquals(at(fri, 1), merged.firstSeenStartMillis)
        assertEquals(at(fri, 0, 40), merged.startMillis)
        assertEquals(30, merged.minutes)
    }

    @Test
    fun bedtimeCheatFarFromSaved_isNewNotExtended() {
        val saved = Cheat(INSTA, CheatType.BEDTIME, thu, at(fri, 1), at(fri, 1, 10), 10)
        val found = Cheat(INSTA, CheatType.BEDTIME, thu, at(fri, 3), at(fri, 3, 5), 5)
        val result = RulesPlanner.mergeWithSaved(listOf(found), listOf(saved))
        assertEquals(listOf(found), result)
    }

    @Test
    fun limitCrossingTimeShifts_betweenRefreshes_stillOneCheat() {
        val todayRow = NightCalendar.day(thu, Settings(appLimits = mapOf(INSTA to 30)), zone)
        // First refresh saw the limit crossed at 15:10.
        val saved = Cheat(INSTA, CheatType.LIMIT, thu, at(thu, 15, 10), at(thu, 15, 25), 15)
        // A later refresh also sees a morning session, so the crossing is now 10:30.
        val result = plan(
            now = at(thu, 18),
            settings = Settings(appLimits = mapOf(INSTA to 30)),
            stored = listOf(todayRow),
            storedCheats = listOf(saved),
            sessions = listOf(session(INSTA, at(thu, 10), at(thu, 10, 40)), session(INSTA, at(thu, 15), at(thu, 15, 25)))
        )
        val cheat = result.cheats.single()
        assertEquals(saved.key, cheat.key)
        assertEquals(at(thu, 15, 10), cheat.startMillis) // the logged crossing time stays
        assertEquals(35, cheat.minutes)
    }

    // ---------- No rules, no cheats ----------

    @Test
    fun periodBeforeFirstEverSnapshot_isNeverJudged() {
        // Rules first saved Thursday 15:00. Instagram locked, 30-min limit.
        val settings = Settings(lockedApps = setOf(INSTA), appLimits = mapOf(INSTA to 30))
        val result = plan(
            now = at(fri, 12),
            settings = settings,
            rulesSince = at(thu, 15),
            sessions = listOf(
                session(INSTA, at(thu, 1), at(thu, 1, 30)),   // Wednesday night: before any rules
                session(INSTA, at(thu, 10), at(thu, 11)),     // Thursday's day began at midnight: before any rules
                session(INSTA, at(fri, 0, 30), at(fri, 0, 40)) // Thursday night began 23:00: after rules → cheat
            )
        )
        val cheat = result.cheats.single()
        assertEquals(CheatType.BEDTIME, cheat.type)
        assertEquals(thu, cheat.periodDate)
        // Nothing is saved for the skipped periods either, so streaks never count them.
        assertNull(result.snapshot(PeriodKind.NIGHT, wed))
        assertNull(result.snapshot(PeriodKind.DAY, thu))
    }
}
