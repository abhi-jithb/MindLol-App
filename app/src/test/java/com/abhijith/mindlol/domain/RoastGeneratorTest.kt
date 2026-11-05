package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.YT
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class RoastGeneratorTest {

    private fun ctx(
        type: CheatType = CheatType.BEDTIME,
        start: LocalTime = LocalTime.of(23, 40),
        minutes: Int = 12,
        week: Int = 1,
        sameApp: Int = 1,
        intensity: RoastIntensity = RoastIntensity.SAVAGE,
        seed: String = "seed"
    ) = RoastContext("Instagram", type, start, minutes, week, sameApp, LocalTime.of(23, 0), LocalTime.of(8, 0),
        limitMinutes = 30, intensity = intensity, seed = seed)

    private fun linesOf(vararg pools: RoastPool) = pools.flatMap { RoastLines.lines.getValue(it) }

    @Test
    fun atLeast40Lines() {
        assertTrue(RoastLines.lines.values.sumOf { it.size } >= 40)
    }

    @Test
    fun everyLine_fillsAllPlaceholders() {
        RoastLines.lines.values.flatten().forEach { line ->
            val filled = RoastGenerator.fill(line, ctx())
            assertFalse("Unfilled placeholder in: $line", filled.contains('{') || filled.contains('}'))
        }
    }

    @Test
    fun sameCheat_alwaysGetsSameLine() {
        val a = RoastGenerator.roast(ctx(seed = "insta|BEDTIME|2026-11-05|123"))
        repeat(5) { assertEquals(a, RoastGenerator.roast(ctx(seed = "insta|BEDTIME|2026-11-05|123"))) }
    }

    @Test
    fun differentCheats_spreadOverLines() {
        val seen = (1..50).map { RoastGenerator.roast(ctx(seed = "cheat-$it")) }.toSet()
        assertTrue(seen.size > 5)
    }

    @Test
    fun firstBedtimeCheat_usesGeneralBedtimeLines() {
        assertEquals(listOf(RoastPool.BEDTIME), RoastGenerator.poolsFor(ctx()))
        val line = RoastGenerator.roast(ctx())
        assertTrue(linesOf(RoastPool.BEDTIME).map { RoastGenerator.fill(it, ctx()) }.contains(line))
    }

    @Test
    fun sameAppAgainThisWeek_usesRepeatLines() {
        assertEquals(listOf(RoastPool.BEDTIME_REPEAT), RoastGenerator.poolsFor(ctx(sameApp = 2, week = 2)))
        assertEquals(listOf(RoastPool.LIMIT_REPEAT), RoastGenerator.poolsFor(ctx(type = CheatType.LIMIT, sameApp = 2, week = 2)))
    }

    @Test
    fun thirdCheatThisWeek_usesWeekLines() {
        assertTrue(RoastPool.WEEK in RoastGenerator.poolsFor(ctx(week = 3)))
        assertTrue(RoastGenerator.fill("{nth} cheat", ctx(week = 3)) == "3rd cheat")
    }

    @Test
    fun lateNight_usesLateLines() {
        assertEquals(listOf(RoastPool.BEDTIME_LATE), RoastGenerator.poolsFor(ctx(start = LocalTime.of(2, 15))))
        assertEquals(listOf(RoastPool.BEDTIME), RoastGenerator.poolsFor(ctx(start = LocalTime.of(0, 30))))
    }

    @Test
    fun limitCheat_neverGetsBedtimeLines() {
        val pools = RoastGenerator.poolsFor(ctx(type = CheatType.LIMIT, minutes = 10))
        assertEquals(listOf(RoastPool.LIMIT), pools)
        assertEquals(listOf(RoastPool.LIMIT_BIG), RoastGenerator.poolsFor(ctx(type = CheatType.LIMIT, minutes = 45)))
    }

    @Test
    fun mild_onlyUsesMildLines() {
        assertEquals(listOf(RoastPool.MILD_BEDTIME), RoastGenerator.poolsFor(ctx(intensity = RoastIntensity.MILD, week = 5, sameApp = 3)))
        assertEquals(listOf(RoastPool.MILD_LIMIT), RoastGenerator.poolsFor(ctx(type = CheatType.LIMIT, intensity = RoastIntensity.MILD)))
    }

    @Test
    fun personalLines_onlyWithPersonalIntensity() {
        assertFalse(RoastPool.PERSONAL_BEDTIME in RoastGenerator.poolsFor(ctx()))
        assertTrue(RoastPool.PERSONAL_BEDTIME in RoastGenerator.poolsFor(ctx(intensity = RoastIntensity.PERSONAL)))
    }

    @Test
    fun fill_formatsTimesAndOrdinals() {
        val filled = RoastGenerator.fill("Bedtime was {bed}. {time}. {appNth}. Up at {wake}.", ctx(start = LocalTime.of(0, 47), sameApp = 2))
        assertEquals("Bedtime was 11. 12:47 AM. 2nd. Up at 8.", filled)
        assertEquals("11:30", RoastGenerator.shortTime(LocalTime.of(23, 30)))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st"), listOf(1, 2, 3, 4, 11, 12, 13, 21).map(RoastGenerator::ordinal))
    }

    @Test
    fun context_countsOnlyEarlierCheatsThisWeek_soOldLinesNeverChange() {
        val first = Cheat(INSTA, CheatType.BEDTIME, thu, at(thu, 23, 30), at(thu, 23, 40), 10)
        val second = Cheat(YT, CheatType.BEDTIME, thu, at(fri, 1), at(fri, 1, 10), 10)
        val third = Cheat(INSTA, CheatType.BEDTIME, fri, at(fri, 23, 30), at(fri, 23, 40), 10)
        val all = listOf(first, second, third)

        fun build(c: Cheat, known: List<Cheat>) = RoastGenerator.contextFor(c, known, "Instagram",
            LocalTime.of(23, 0), LocalTime.of(8, 0), null, RoastIntensity.SAVAGE, zone)

        assertEquals(1, build(first, all).cheatsThisWeek)
        assertEquals(3, build(third, all).cheatsThisWeek)
        assertEquals(2, build(third, all).sameAppThisWeek)
        // Adding later cheats doesn't change the first cheat's context or line.
        assertEquals(build(first, listOf(first)), build(first, all))
    }
}
