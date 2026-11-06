package com.abhijith.mindlol.domain

/** Words on the lock screen's buttons that depend on why the app is locked. */
object LockLabels {

    /**
     * The big white way out. Same action for both (WENT_BACK, home screen), but "sleep" is only
     * true at night: a daytime limit lock says "Walk away".
     */
    fun goBack(reason: LockReason): String = when (reason) {
        LockReason.BEDTIME -> "Go back to sleep"
        LockReason.LIMIT -> "Walk away"
    }
}
