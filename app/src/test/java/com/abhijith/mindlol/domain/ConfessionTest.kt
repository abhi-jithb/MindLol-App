package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfessionTest {

    private val bedtime = Confession.required("Instagram", LockReason.BEDTIME)

    @Test
    fun requiredText_perReason() {
        assertEquals("I am choosing Instagram over my sleep", bedtime)
        assertEquals("I am choosing YouTube over my own limit", Confession.required("YouTube", LockReason.LIMIT))
    }

    @Test
    fun exactText_matches() = assertTrue(Confession.matches("I am choosing Instagram over my sleep", bedtime))

    @Test
    fun caseAndExtraSpaces_areIgnored() {
        assertTrue(Confession.matches("i AM choosing   instagram over my SLEEP", bedtime))
        assertTrue(Confession.matches("  I am choosing Instagram over my sleep  ", bedtime))
    }

    @Test
    fun anyWordDifferent_doesNotMatch() {
        assertFalse(Confession.matches("I am choosing Instagram over sleep", bedtime))         // missing word
        assertFalse(Confession.matches("I am choosing Instagram over my sleep.", bedtime))     // extra character
        assertFalse(Confession.matches("I am choosing Insta over my sleep", bedtime))          // different word
        assertFalse(Confession.matches("", bedtime))
    }
}
