package com.abhijith.mindlol.domain

/**
 * "The interrogation" (unlock step 3): one question card per unlock, two answers, and a mascot
 * reaction plus a reply for EITHER answer. EDIT FREELY: add cards or reword them.
 * {app} in a reply becomes the app's name. Cards marked personal only appear with PERSONAL intensity.
 */

/** The mascot's reaction to an answer. Mapped to the UI's MascotReaction on screen. */
enum class CardReaction { STARE, FACEPALM, HEAD_SHAKE, TURN_AWAY, NOD }

data class CardAnswer(
    val label: String,
    val reaction: CardReaction,
    val reply: String
)

data class InterrogationCard(
    /** Stable id, saved in lock_event.cardId so a card never repeats the same night. Never reuse one. */
    val id: String,
    val question: String,
    val personal: Boolean,
    val answers: List<CardAnswer>
)

object InterrogationCards {

    val cards: List<InterrogationCard> = listOf(
        InterrogationCard(
            id = "brain",
            question = "Still think your brain can take it?",
            personal = false,
            answers = listOf(
                CardAnswer("Yes", CardReaction.STARE, "Unfortunately, it's about to burst."),
                CardAnswer("No", CardReaction.HEAD_SHAKE, "Then why are you still here?")
            )
        ),
        InterrogationCard(
            id = "girlfriend",
            question = "Do you have a girlfriend?",
            personal = true,
            answers = listOf(
                CardAnswer("Yes", CardReaction.FACEPALM, "And she's asleep while you're here with {app}."),
                CardAnswer("No", CardReaction.NOD, "Makes sense.")
            )
        ),
        InterrogationCard(
            id = "exes",
            question = "How many exes do you have?",
            personal = true,
            answers = listOf(
                CardAnswer("0", CardReaction.NOD, "Makes sense."),
                CardAnswer("1+", CardReaction.HEAD_SHAKE, "And you still can't break up with {app}.")
            )
        ),
        InterrogationCard(
            id = "loyal",
            question = "Will you expect your future wife to be loyal and caring?",
            personal = true,
            answers = listOf(
                CardAnswer("Yes", CardReaction.TURN_AWAY, "You can't even stay loyal to your own bedtime."),
                CardAnswer("No", CardReaction.NOD, "Good. At least you're not a hypocrite.")
            )
        )
    )

    /**
     * The card for this unlock. Personal cards only with PERSONAL intensity.
     * - A card not shown tonight is preferred. The choice looks random but is fixed by [seed]
     *   (the lock screen's shown time), so it survives a rotation.
     * - If every allowed card was already shown tonight, the one shown longest ago comes back:
     *   the interrogation never disappears on repeat unlocks.
     *
     * @param shownTonight card ids from tonight's lock_event rows, oldest first (repeats allowed).
     * @return null only if no card is allowed at all (an empty card list).
     */
    fun pick(
        intensity: RoastIntensity,
        shownTonight: List<String>,
        seed: Long,
        cards: List<InterrogationCard> = this.cards
    ): InterrogationCard? {
        val allowed = cards.filter { !it.personal || intensity == RoastIntensity.PERSONAL }
        val fresh = allowed.filter { it.id !in shownTonight }
        if (fresh.isNotEmpty()) return fresh[Math.floorMod(seed.hashCode(), fresh.size)]
        // All shown: the card whose latest showing is the oldest.
        return allowed.minByOrNull { card -> shownTonight.lastIndexOf(card.id) }
    }

    fun reply(answer: CardAnswer, appName: String): String = answer.reply.replace("{app}", appName)
}
