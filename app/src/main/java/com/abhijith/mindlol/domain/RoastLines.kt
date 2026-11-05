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
    PERSONAL_LIMIT
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
        )
    )
}
