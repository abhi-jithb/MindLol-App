package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterrogationCardsTest {

    private val cards = InterrogationCards.cards

    @Test
    fun theFourCards_asWritten() {
        assertEquals(listOf("brain", "girlfriend", "exes", "loyal"), cards.map { it.id })
        assertEquals(listOf(false, true, true, true), cards.map { it.personal })
        cards.forEach { assertEquals(2, it.answers.size) }
        assertEquals(cards.size, cards.map { it.id }.toSet().size) // ids are unique
    }

    @Test
    fun decidedReactions() {
        fun reaction(id: String, label: String) = cards.single { it.id == id }.answers.single { it.label == label }.reaction
        assertEquals(CardReaction.STARE, reaction("brain", "Yes"))
        assertEquals(CardReaction.HEAD_SHAKE, reaction("brain", "No"))
        assertEquals(CardReaction.NOD, reaction("girlfriend", "No"))
        assertEquals(CardReaction.FACEPALM, reaction("girlfriend", "Yes"))
        assertEquals(CardReaction.NOD, reaction("exes", "0"))
        assertEquals(CardReaction.HEAD_SHAKE, reaction("exes", "1+"))
        assertEquals(CardReaction.TURN_AWAY, reaction("loyal", "Yes"))
        assertEquals(CardReaction.NOD, reaction("loyal", "No"))
    }

    @Test
    fun reply_fillsAppName() {
        val yes = cards.single { it.id == "girlfriend" }.answers.single { it.label == "Yes" }
        assertEquals("And she's asleep while you're here with Instagram.", InterrogationCards.reply(yes, "Instagram"))
        cards.flatMap { it.answers }.forEach { assertFalse(InterrogationCards.reply(it, "X").contains('{')) }
    }

    @Test
    fun personalCards_onlyWithPersonalIntensity() {
        (0L..50L).forEach { seed ->
            assertEquals("brain", InterrogationCards.pick(RoastIntensity.SAVAGE, emptyList(), seed)?.id)
            assertEquals("brain", InterrogationCards.pick(RoastIntensity.MILD, emptyList(), seed)?.id)
        }
        val personal = (0L..50L).mapNotNull { InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), it)?.id }.toSet()
        assertTrue(personal.size > 1) // personal cards do show up
    }

    @Test
    fun noRepeatSameNight() {
        val shown = listOf("brain", "girlfriend", "exes")
        (0L..50L).forEach { assertEquals("loyal", InterrogationCards.pick(RoastIntensity.PERSONAL, shown, it)?.id) }
    }

    @Test
    fun everyCardShownTonight_repeatsTheOneShownLongestAgo_neverSkips() {
        // SAVAGE has one allowed card: it keeps coming back.
        assertEquals("brain", InterrogationCards.pick(RoastIntensity.SAVAGE, listOf("brain"), 1L)?.id)
        assertEquals("brain", InterrogationCards.pick(RoastIntensity.SAVAGE, listOf("brain", "brain", "brain"), 7L)?.id)
        // PERSONAL, all four shown (exes twice): brain's last showing is the oldest.
        val shown = listOf("exes", "brain", "girlfriend", "loyal", "exes")
        (0L..20L).forEach { assertEquals("brain", InterrogationCards.pick(RoastIntensity.PERSONAL, shown, it)?.id) }
    }

    @Test
    fun noCardsAtAll_isTheOnlyNull() {
        assertNull(InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), 1L, cards = emptyList()))
    }

    @Test
    fun sameSeed_sameCard() {
        val a = InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), 1_793_873_924_724L)
        repeat(3) { assertEquals(a, InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), 1_793_873_924_724L)) }
    }
}
