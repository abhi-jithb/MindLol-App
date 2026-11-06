package com.abhijith.mindlol.domain

/**
 * The timing rules of unlock steps 3–5, kept pure so they're JVM-tested.
 * Wall-clock based on purpose: Compose animations follow the phone's "Animator duration scale",
 * and with animations off a 3-second animated hold would finish instantly.
 */
object UnlockSteps {

    /** Step 4, the bargain: the only lengths you can ask for. */
    val BARGAIN_MINUTES: List<Int> = listOf(2, 5, 10)

    /** Step 5, last chance: how long the button must be held down without letting go. */
    const val HOLD_MILLIS = 3_000L

    /** Step 3: how fast the mascot's reply types itself out. */
    const val TYPE_MILLIS_PER_CHAR = 35L

    /** 0..1 progress of the hold after [heldMillis] of holding. Letting go starts again from 0. */
    fun holdProgress(heldMillis: Long): Float =
        (heldMillis.coerceAtLeast(0L).toFloat() / HOLD_MILLIS).coerceIn(0f, 1f)

    fun holdDone(heldMillis: Long): Boolean = heldMillis >= HOLD_MILLIS

    /** How many characters of a [length]-character reply are visible after [elapsedMillis]. */
    fun typedLength(elapsedMillis: Long, length: Int, millisPerChar: Long = TYPE_MILLIS_PER_CHAR): Int =
        (elapsedMillis.coerceAtLeast(0L) / millisPerChar).coerceAtMost(length.toLong()).toInt()

    fun isBargainOption(minutes: Int): Boolean = minutes in BARGAIN_MINUTES
}
