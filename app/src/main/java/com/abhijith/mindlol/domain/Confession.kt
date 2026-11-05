package com.abhijith.mindlol.domain

/** Unlock step 2: the sentence you must type yourself. */
object Confession {

    fun required(appName: String, reason: LockReason): String = when (reason) {
        LockReason.BEDTIME -> "I am choosing $appName over my sleep"
        LockReason.LIMIT -> "I am choosing $appName over my own limit"
    }

    /** Case doesn't matter and extra spaces are ignored, but every word must be exact. */
    fun matches(typed: String, required: String): Boolean = normalize(typed) == normalize(required)

    private fun normalize(text: String): String = text.trim().split(Regex("\\s+")).joinToString(" ").lowercase()
}
