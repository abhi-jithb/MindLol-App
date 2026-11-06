package com.abhijith.mindlol.domain

/**
 * Every roast line, grouped by when it fits. EDIT FREELY: add, remove or reword lines.
 * RoastGeneratorTest checks every line still fills all its placeholders.
 *
 * Tone rule: savage about the choice and the behaviour, never about worth, looks or intelligence.
 *
 * Placeholders (filled by RoastGenerator):
 *   {app}      app name               "Instagram"
 *   {time}     when the cheat started "12:47 AM"
 *   {bed}      bedtime                "11" or "11:30"
 *   {wake}     wake time              "8"
 *   {minutes}  cheat minutes          "23"
 *   {limit}    the daily limit (LIMIT pools only) "30"
 *   {nth}      this cheat's number this week      "3rd"
 *   {count}    cheats this week, including this   "3"
 *   {appNth}   this app's cheat number this week  "2nd"
 *   {next}     the number the next cheat would be "4th"  (lock screen bargain)
 *
 * The STEP_ pools are the lock screen's lines (Phase 3b), one pool per unlock step. The
 * interrogation step has no pool here: its lines live with the cards in InterrogationCards.kt.
 */
enum class RoastPool {
    BEDTIME,          // any bedtime cheat
    BEDTIME_LATE,     // bedtime cheat that started between 1 AM and 6 AM
    BEDTIME_REPEAT,   // same app already cheated on this week
    WEEK,             // 3rd cheat or more this week (any type)
    LIMIT,            // any limit cheat
    LIMIT_BIG,        // 30+ minutes over the limit
    LIMIT_REPEAT,     // same app already cheated on this week
    MILD_BEDTIME,     // MILD intensity only
    MILD_LIMIT,
    PERSONAL_BEDTIME, // added to the pool only with PERSONAL intensity
    PERSONAL_LIMIT,
    LOCK_DISABLED,    // the live lock was turned off during a sleep window
    MILD_LOCK_DISABLED,

    // Lock screen steps. SAVAGE and PERSONAL use these; MILD uses the MILD_STEP_ ones.
    STEP_STARE_BEDTIME,
    STEP_STARE_LIMIT,
    STEP_CONFESSION,
    STEP_BARGAIN,
    STEP_LAST_CHANCE,
    MILD_STEP_STARE,
    MILD_STEP_STARE_LIMIT,
    MILD_STEP_CONFESSION,
    MILD_STEP_BARGAIN,
    MILD_STEP_LAST_CHANCE
}

object RoastLines {

    val lines: Map<RoastPool, List<String>> = mapOf(
        RoastPool.BEDTIME to listOf(
            "Bedtime was {bed}. You chose {app}.",
            "You set this rule yourself. Remember that.",
            "Future you wakes up at {wake}. Present you just stole from him.",
            "{time}. {app}. Bold choice for someone with an alarm.",
            "{minutes} minutes of {app} at {time}. Was it worth it? It wasn't.",
            "Your pillow waited. {app} won.",
            "The rule said sleep. You said {app}.",
            "Nobody forced you to open {app} at {time}. That was all you.",
            "{minutes} minutes you'll pay back tomorrow, with interest.",
            "You locked {app} for a reason. You just forgot the reason.",
            "Sleep is free. You chose {app} instead.",
            "Bedtime is a rule, not a suggestion. You treated it like a suggestion.",
            "Lights off was {bed}. Your screen didn't get the message.",
            "{app} at {time}? That's a choice you'll feel at {wake}."
        ),
        RoastPool.BEDTIME_LATE to listOf(
            "{time}. Even {app} is surprised you're here.",
            "It's {time}. Your alarm is already disappointed.",
            "Nothing good on {app} has ever happened at {time}.",
            "{time} and still scrolling. Tomorrow-you is drafting a complaint.",
            "At {time} the only thing {app} gives you is a worse tomorrow."
        ),
        RoastPool.BEDTIME_REPEAT to listOf(
            "Bedtime was {bed}. You chose {app}. Again.",
            "{app} again. Same rule, same betrayal.",
            "You and {app} at night. This is becoming a pattern.",
            "{appNth} time this week with {app} after {bed}. It's not a slip anymore, it's a habit."
        ),
        RoastPool.WEEK to listOf(
            "{nth} cheat this week. Your sleep filed a complaint.",
            "{nth} cheat this week. The rule is starting to feel lonely.",
            "{count} cheats this week. You're not breaking the rule, you're ignoring it."
        ),
        RoastPool.LIMIT to listOf(
            "{limit} minutes was your limit. {app} got more.",
            "You gave {app} a limit. Then you gave it more.",
            "Limit crossed at {time}. You noticed. You kept going.",
            "{minutes} minutes over on {app}. The limit was your idea.",
            "Your own limit, your own rule, broken by you.",
            "{app} didn't break the limit. You did.",
            "Over the limit by {minutes} minutes. Math doesn't lie."
        ),
        RoastPool.LIMIT_BIG to listOf(
            "{minutes} minutes past the limit. That's not a slip, that's a second session.",
            "The limit ended {minutes} minutes ago. {app} didn't get the memo. Neither did you."
        ),
        RoastPool.LIMIT_REPEAT to listOf(
            "Over the {app} limit again. The limit isn't the problem.",
            "{appNth} time over the {app} limit this week. You know how this ends."
        ),
        RoastPool.MILD_BEDTIME to listOf(
            "It's past bedtime. {app} can wait till morning.",
            "{minutes} minutes of {app} after {bed}. Try putting it down a bit earlier.",
            "Your sleep window started at {bed}. Tomorrow's a new night.",
            "You opened {app} during sleep time. Let's not make it a habit."
        ),
        RoastPool.MILD_LIMIT to listOf(
            "You went past your {app} limit by {minutes} minutes.",
            "{app} limit crossed. Maybe call it a day for {app}.",
            "A bit over on {app} today. Tomorrow resets."
        ),
        RoastPool.PERSONAL_BEDTIME to listOf(
            "Your parents think you're asleep. You're on {app}.",
            "Your goals are waiting for a rested you. They got {app} instead.",
            "You'd judge a friend for this. You're doing it at {time}.",
            "You promised yourself. Then you opened {app}."
        ),
        RoastPool.PERSONAL_LIMIT to listOf(
            "You told yourself {limit} minutes. You lied to yourself.",
            "Somewhere your future self is tired because of these {minutes} minutes."
        ),
        RoastPool.LOCK_DISABLED to listOf(
            "You turned off your own lock. At {time}. Think about that.",
            "The lock was yours. You switched it off to cheat.",
            "{minutes} minutes with the lock off. That's not freedom, that's sabotage.",
            "Turning off the lock doesn't delete the rule. I noticed.",
            "You didn't beat the lock. You just stopped pretending."
        ),
        RoastPool.MILD_LOCK_DISABLED to listOf(
            "The live lock was off for {minutes} minutes during sleep time.",
            "Your lock was turned off at {time}. Leave it on tonight."
        ),

        // ---------- Lock screen steps ----------

        RoastPool.STEP_STARE_BEDTIME to listOf(
            "It's {time}. Bedtime was {bed}. Put it down.",
            "You set this rule yourself. Remember that.",
            "Future you wakes up at {wake}. Present you is about to steal from him.",
            "{app}? At {time}? I'm watching.",
            "Go back to sleep. This isn't a negotiation. Yet."
        ),
        RoastPool.STEP_STARE_LIMIT to listOf(
            "{limit} minutes. That was your number. You hit it.",
            "Your {app} limit is done for today.",
            "You gave {app} {limit} minutes. It took them all."
        ),
        RoastPool.STEP_CONFESSION to listOf(
            "Say it. Every word.",
            "If you mean it, type it.",
            "Own it. Letter by letter."
        ),
        RoastPool.STEP_BARGAIN to listOf(
            "{count} cheats this week already. How many minutes is this one worth?",
            "This would be your {next} cheat this week. Pick your price.",
            "{count} so far this week. Choose how long you'll regret it."
        ),
        RoastPool.STEP_LAST_CHANCE to listOf(
            "Hold it. Three seconds to change your mind.",
            "Last chance. I'm not watching this.",
            "Hold the button. Or let go and keep your own rule."
        ),
        RoastPool.MILD_STEP_STARE to listOf(
            "It's {time}, past your {bed} bedtime. Maybe tomorrow?",
            "This is your own rule. Want to keep it?"
        ),
        RoastPool.MILD_STEP_STARE_LIMIT to listOf(
            "Your {app} limit for today is used up. Want to keep it?"
        ),
        RoastPool.MILD_STEP_CONFESSION to listOf(
            "If you're sure, type it out."
        ),
        RoastPool.MILD_STEP_BARGAIN to listOf(
            "{count} cheats this week so far. How long do you need?"
        ),
        RoastPool.MILD_STEP_LAST_CHANCE to listOf(
            "Hold to open. Or let go and rest."
        )
    )
}
