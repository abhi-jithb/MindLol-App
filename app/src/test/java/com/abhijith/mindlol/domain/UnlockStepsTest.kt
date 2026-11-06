package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockStepsTest {

    @Test
    fun bargain_offersOnly2_5_10Minutes() {
        assertEquals(listOf(2, 5, 10), UnlockSteps.BARGAIN_MINUTES)
        assertTrue(UnlockSteps.isBargainOption(5))
        assertFalse(UnlockSteps.isBargainOption(0))
        assertFalse(UnlockSteps.isBargainOption(11))
        assertFalse(UnlockSteps.isBargainOption(60))
    }

    @Test
    fun hold_needsTheFullThreeSeconds() {
        assertFalse(UnlockSteps.holdDone(0))
        assertFalse(UnlockSteps.holdDone(2_999))
        assertTrue(UnlockSteps.holdDone(3_000))
        assertTrue(UnlockSteps.holdDone(10_000))
    }

    @Test
    fun holdProgress_growsLinearlyAndStaysInRange() {
        assertEquals(0f, UnlockSteps.holdProgress(-50), 0f)
        assertEquals(0f, UnlockSteps.holdProgress(0), 0f)
        assertEquals(0.5f, UnlockSteps.holdProgress(1_500), 0.0001f)
        assertEquals(1f, UnlockSteps.holdProgress(3_000), 0f)
        assertEquals(1f, UnlockSteps.holdProgress(9_000), 0f)
    }

    @Test
    fun typedLength_typesOneLetterPerStepAndNeverOverruns() {
        assertEquals(0, UnlockSteps.typedLength(0, 10, millisPerChar = 35))
        assertEquals(0, UnlockSteps.typedLength(34, 10, millisPerChar = 35))
        assertEquals(1, UnlockSteps.typedLength(35, 10, millisPerChar = 35))
        assertEquals(4, UnlockSteps.typedLength(149, 10, millisPerChar = 35))
        assertEquals(10, UnlockSteps.typedLength(100_000, 10, millisPerChar = 35))
        assertEquals(0, UnlockSteps.typedLength(-10, 10, millisPerChar = 35))
    }

    @Test
    fun everyCardAnswer_hasAReplyThatTypesOut() {
        InterrogationCards.cards.flatMap { it.answers }.forEach { answer ->
            val reply = InterrogationCards.reply(answer, "Instagram")
            assertTrue(reply.isNotBlank())
            assertEquals(reply.length, UnlockSteps.typedLength(reply.length * UnlockSteps.TYPE_MILLIS_PER_CHAR, reply.length))
        }
    }

    @Test
    fun everyCard_hasExactlyTwoAnswers() {
        InterrogationCards.cards.forEach { assertEquals(it.id, 2, it.answers.size) }
    }
}
