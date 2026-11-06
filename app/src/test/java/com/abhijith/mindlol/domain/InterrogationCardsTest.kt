package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterrogationCardsTest {

    private val cards = InterrogationCards.cards

    @Test
    fun theSevenCards_asWritten() {
        assertEquals(listOf("brain", "video", "reel", "mom", "girlfriend", "exes", "loyal"), cards.map { it.id })
        assertEquals(listOf(false, false, false, false, true, true, true), cards.map { it.personal })
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
        assertEquals(CardReaction.HEAD_SHAKE, reaction("video", "Yes"))
        assertEquals(CardReaction.STARE, reaction("video", "No"))
        assertEquals(CardReaction.STARE, reaction("reel", "Yes"))
        assertEquals(CardReaction.FACEPALM, reaction("reel", "No"))
        assertEquals(CardReaction.TURN_AWAY, reaction("mom", "Yes"))
        assertEquals(CardReaction.NOD, reaction("mom", "No"))
    }

    @Test
    fun newCards_wordForWord() {
        fun card(id: String) = cards.single { it.id == id }
        assertEquals("Is this video going to change your life?", card("video").question)
        assertEquals("None of the videos tonight did.", card("video").answers[0].reply)
        assertEquals("Then close it.", card("video").answers[1].reply)
        assertEquals("Will you remember this reel tomorrow?", card("reel").question)
        assertEquals("Name the last one you watched. Exactly.", card("reel").answers[0].reply)
        assertEquals("So you're trading sleep for nothing.", card("reel").answers[1].reply)
        assertEquals("Would you show your screen time to your mom right now?", card("mom").question)
        assertEquals("Bold lie.", card("mom").answers[0].reply)
        assertEquals("That's your answer, then.", card("mom").answers[1].reply)
    }

    @Test
    fun reply_fillsAppName() {
        val yes = cards.single { it.id == "girlfriend" }.answers.single { it.label == "Yes" }
        assertEquals("And she's asleep while you're here with Instagram.", InterrogationCards.reply(yes, "Instagram"))
        cards.flatMap { it.answers }.forEach { assertFalse(InterrogationCards.reply(it, "X").contains('{')) }
    }

    @Test
    fun personalCards_onlyWithPersonalIntensity() {
        val personalIds = cards.filter { it.personal }.map { it.id }.toSet()
        (0L..50L).forEach { seed ->
            assertTrue(InterrogationCards.pick(RoastIntensity.SAVAGE, emptyList(), seed)?.id !in personalIds)
            assertTrue(InterrogationCards.pick(RoastIntensity.MILD, emptyList(), seed)?.id !in personalIds)
        }
        val personal = (0L..50L).mapNotNull { InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), it)?.id }.toSet()
        assertTrue(personal.size > 1) // personal cards do show up
    }

    @Test
    fun noRepeatSameNight() {
        val shown = listOf("brain", "video", "reel", "mom", "girlfriend", "exes")
        (0L..50L).forEach { assertEquals("loyal", InterrogationCards.pick(RoastIntensity.PERSONAL, shown, it)?.id) }
    }

    @Test
    fun everyCardShownTonight_repeatsTheOneShownLongestAgo_neverSkips() {
        // SAVAGE, all four non-personal cards shown: the oldest comes back.
        assertEquals("video", InterrogationCards.pick(RoastIntensity.SAVAGE, listOf("video", "brain", "reel", "mom"), 1L)?.id)
        assertEquals("reel", InterrogationCards.pick(RoastIntensity.SAVAGE, listOf("video", "brain", "reel", "mom", "video", "brain"), 7L)?.id)
        // PERSONAL, all seven shown (exes twice): brain's last showing is the oldest.
        val shown = listOf("exes", "brain", "video", "reel", "mom", "girlfriend", "loyal", "exes")
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

    /** No fake facts: no invented numbers in any question or reply (answer labels like "1+" are choices). */
    @Test
    fun noQuestionOrReplyContainsAHardCodedNumber() {
        cards.forEach { card ->
            assertFalse(card.question, card.question.any(Char::isDigit))
            card.answers.forEach { assertFalse(it.reply, it.reply.any(Char::isDigit)) }
        }
    }

    @Test
    fun videoCard_usesTheRewordedReply() {
        val yes = cards.first { it.id == "video" }.answers.first { it.label == "Yes" }
        assertEquals("None of the videos tonight did.", yes.reply)
    }

    /** Replies about sleep or tonight never show on a daytime limit lock. */
    @Test
    fun nightOnlyCards_neverPickedForALimitLock_andEveryNightWordedCardIsMarked() {
        val night = Regex("sleep|asleep|tonight|bedtime", RegexOption.IGNORE_CASE)
        cards.forEach { card ->
            if (card.answers.any { night.containsMatchIn(it.reply) }) assertTrue("${card.id} must be nightOnly", card.nightOnly)
        }
        val nightIds = cards.filter { it.nightOnly }.map { it.id }.toSet()
        (0L..60L).forEach { seed ->
            val picked = InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), seed, reason = LockReason.LIMIT)
            assertTrue(picked?.id !in nightIds)
        }
        val atNight = (0L..60L).mapNotNull { InterrogationCards.pick(RoastIntensity.PERSONAL, emptyList(), it, reason = LockReason.BEDTIME)?.id }.toSet()
        assertTrue(atNight.any { it in nightIds })
    }
}
