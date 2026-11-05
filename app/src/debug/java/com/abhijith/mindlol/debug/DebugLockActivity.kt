package com.abhijith.mindlol.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.ui.lock.LockActivity
import java.time.LocalDate

/**
 * DEBUG BUILDS ONLY (lives in src/debug). Opens a TEST lock screen for any app:
 *
 *   adb shell am start -n com.abhijith.mindlol/.debug.DebugLockActivity \
 *       --es pkg com.instagram.android --es reason BEDTIME
 *
 * reason: BEDTIME (default) or LIMIT. The test lock screen writes nothing: no lock_event, no grant,
 * no cheat, so it never counts in stats or escalation. It's an invisible trampoline: it starts the
 * lock screen and closes itself straight away. (`am start` rather than `am broadcast`: since
 * Android 10 a broadcast receiver can't open a screen while the app is in the background.)
 */
class DebugLockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        val reason = intent.getStringExtra("reason")?.let { runCatching { LockReason.valueOf(it.uppercase()) }.getOrNull() }
            ?: LockReason.BEDTIME
        if (pkg.isNullOrBlank()) {
            Log.w(TAG, "Test lock: missing --es pkg <package name>")
        } else {
            Log.w(TAG, "Test lock: $pkg ($reason), nothing will be saved")
            startActivity(LockActivity.intent(this, pkg, reason, LocalDate.now(), test = true))
        }
        finish() // Theme.NoDisplay activities must finish before they would be shown
    }

    private companion object {
        const val TAG = "LiveLock"
    }
}
