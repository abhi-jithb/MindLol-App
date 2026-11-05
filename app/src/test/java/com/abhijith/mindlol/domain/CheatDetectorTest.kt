package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.YT
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.minutes
import com.abhijith.mindlol.domain.RulesTestTime.session
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatDetectorTest {

    // Thursday night, 23:00 → Fri 08:00, Instagram and YouTube locked.
    private val night = NightCalendar.night(thu, Settings(lockedApps = setOf(INSTA, YT)), zone)

    private fun day(limits: Map<String, Int>) = NightCalendar.day(thu, Settings(appLimits = limits), zone)

    @Test
    fun sessionInsideWindow_isBedtimeCheat() {
        val cheat = CheatDetector.bedtimeCheats(night, listOf(session(INSTA, at(fri, 0, 30), at(fri, 0, 50)))).single()
        assertEquals(INSTA, cheat.packageName)
        assertEquals(CheatType.BEDTIME, cheat.type)
        assertEquals(thu, cheat.periodDate)
        assertEquals(at(fri, 0, 30), cheat.startMillis)
        assertEquals(at(fri, 0, 50), cheat.endMillis)
        assertEquals(20, cheat.minutes)
    }

    @Test
    fun unlockedApp_isNeverBedtimeCheat() {
        val cheats = CheatDetector.bedtimeCheats(night, listOf(session("com.whatsapp", at(fri, 1), at(fri, 2))))
        assertTrue(cheats.isEmpty())
    }

    @Test
    fun sessionOutsideWindow_isNotCheat() {
        val cheats = CheatDetector.bedtimeCheats(
            night,
            listOf(session(INSTA, at(thu, 21), at(thu, 22)), session(INSTA, at(fri, 9), at(fri, 10)))
        )
        assertTrue(cheats.isEmpty())
    }

    @Test
    fun sessionCrossingBedtime_countsOnlyMinutesInsideWindow() {
        val cheat = CheatDetector.bedtimeCheats(night, listOf(session(INSTA, at(thu, 22, 40), at(thu, 23, 15)))).single()
        assertEquals(at(thu, 23), cheat.startMillis)
        assertEquals(15, cheat.minutes)
    }

    @Test
    fun sessionCrossingWake_countsOnlyMinutesInsideWindow() {
        val cheat = CheatDetector.bedtimeCheats(night, listOf(session(INSTA, at(fri, 7, 50), at(fri, 8, 30)))).single()
        assertEquals(at(fri, 8), cheat.endMillis)
        assertEquals(10, cheat.minutes)
    }

    @Test
    fun windowCrossingMidnight_catchesSessionsOnBothSides() {
        val cheats = CheatDetector.bedtimeCheats(
            night,
            listOf(session(INSTA, at(thu, 23, 30), at(thu, 23, 40)), session(YT, at(fri, 3), at(fri, 3, 20)))
        )
        assertEquals(listOf(INSTA, YT), cheats.map { it.packageName })
    }

    @Test
    fun tenSecondGlance_countsAsOneMinuteCheat() {
        val cheat = CheatDetector.bedtimeCheats(night, listOf(session(INSTA, at(fri, 1), at(fri, 1, 0, 10)))).single()
        assertEquals(1, cheat.minutes)
    }

    @Test
    fun sameAppSessionsUnder5MinApart_mergeIntoOneCheat() {
        val cheat = CheatDetector.bedtimeCheats(
            night,
            listOf(
                session(INSTA, at(fri, 1, 0), at(fri, 1, 10)),
                session(INSTA, at(fri, 1, 14, 59), at(fri, 1, 20)) // 4:59 gap
            )
        ).single()
        assertEquals(at(fri, 1, 0), cheat.startMillis)
        assertEquals(at(fri, 1, 20), cheat.endMillis)
    }

    @Test
    fun sameAppSessionsExactly5MinApart_stayTwoCheats() {
        val cheats = CheatDetector.bedtimeCheats(
            night,
            listOf(
                session(INSTA, at(fri, 1, 0), at(fri, 1, 10)),
                session(INSTA, at(fri, 1, 15), at(fri, 1, 20)) // exactly 5:00 gap
            )
        )
        assertEquals(2, cheats.size)
    }

    @Test
    fun merge_excludesGapFromMinutes() {
        val cheat = CheatDetector.bedtimeCheats(
            night,
            listOf(session(INSTA, at(fri, 1, 0), at(fri, 1, 10)), session(INSTA, at(fri, 1, 13), at(fri, 1, 20)))
        ).single()
        assertEquals(17, cheat.minutes) // 10 + 7, not the 20 from first start to last end
    }

    @Test
    fun differentApps_neverMerge() {
        val cheats = CheatDetector.bedtimeCheats(
            night,
            listOf(session(INSTA, at(fri, 1, 0), at(fri, 1, 10)), session(YT, at(fri, 1, 11), at(fri, 1, 20)))
        )
        assertEquals(2, cheats.size)
    }

    @Test
    fun limitCrossed_logsOneCheatAtCrossingMoment() {
        // 30-minute limit: 20 min at 10:00, then 25 min at 15:00 → crossed at 15:10, 15 min over.
        val cheat = CheatDetector.limitCheats(
            day(mapOf(INSTA to 30)),
            listOf(session(INSTA, at(thu, 10), at(thu, 10, 20)), session(INSTA, at(thu, 15), at(thu, 15, 25)))
        ).single()
        assertEquals(CheatType.LIMIT, cheat.type)
        assertEquals(at(thu, 15, 10), cheat.startMillis)
        assertEquals(15, cheat.minutes)
        assertEquals(Cheat.NO_FIRST_SEEN, cheat.firstSeenStartMillis)
    }

    @Test
    fun limitNotReached_noCheat() {
        val cheats = CheatDetector.limitCheats(day(mapOf(INSTA to 30)), listOf(session(INSTA, at(thu, 10), at(thu, 10, 30))))
        assertTrue(cheats.isEmpty()) // exactly at the limit is not over it
    }

    @Test
    fun manySessionsOverLimit_stillOneCheatPerAppPerDay() {
        val cheats = CheatDetector.limitCheats(
            day(mapOf(INSTA to 10)),
            listOf(
                session(INSTA, at(thu, 9), at(thu, 9, 20)),
                session(INSTA, at(thu, 12), at(thu, 12, 20)),
                session(INSTA, at(thu, 18), at(thu, 18, 20))
            )
        )
        assertEquals(1, cheats.size)
        assertEquals(50, cheats.single().minutes)
    }

    @Test
    fun appWithoutLimit_neverLimitCheat() {
        val cheats = CheatDetector.limitCheats(day(mapOf(INSTA to 10)), listOf(session(YT, at(thu, 9), at(thu, 12))))
        assertTrue(cheats.isEmpty())
    }

    @Test
    fun sessionCrossingMidnight_countsTowardEachDaySeparately() {
        // 23:40 → 00:20 with a 30-min limit: 20 min Thursday, 20 min Friday, neither over.
        val s = listOf(session(INSTA, at(thu, 23, 40), at(fri, 0, 20)))
        assertTrue(CheatDetector.limitCheats(day(mapOf(INSTA to 30)), s).isEmpty())
        val friday = NightCalendar.day(fri, Settings(appLimits = mapOf(INSTA to 30)), zone)
        assertTrue(CheatDetector.limitCheats(friday, s).isEmpty())
    }

    @Test
    fun minutesOverMergeGap_isFiveMinutes() {
        assertEquals(minutes(5), CheatDetector.MERGE_GAP_MS)
    }
}
