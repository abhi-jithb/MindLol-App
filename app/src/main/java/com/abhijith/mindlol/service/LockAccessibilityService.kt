package com.abhijith.mindlol.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.LiveLockRepository
import com.abhijith.mindlol.domain.LiveLockDecider
import com.abhijith.mindlol.domain.LockDecision
import com.abhijith.mindlol.ui.lock.LockActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The live lock (Phase 3b). The system starts it once you switch it on in Settings → Accessibility,
 * and tells it every time a new window comes to the front.
 *
 * When the decision is "lock", it opens LockActivity over the app. Every decision is also written
 * to logcat (Log.w, because Vivo drops Log.i).
 *
 * It also records when it was running (lock_session), which is how a disabled lock is noticed.
 */
class LockAccessibilityService : AccessibilityService() {

    // Its own scope: cancelled when the system unbinds the service.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Live lock error", e) }
    )
    private lateinit var repository: LiveLockRepository
    private var sessionId: Long? = null
    private var heartbeatJob: Job? = null
    private var lastPackage: String? = null
    // Apps and the home screen; null until worked out (it lists installed apps, so it runs off the main thread).
    private var tracked: Set<String>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = (application as MindLolApplication).container.liveLockRepository
        _running.value = true
        val now = System.currentTimeMillis()
        // elapsedRealtime = time since the phone booted, so this is the boot moment.
        val bootAt = now - SystemClock.elapsedRealtime()
        scope.launch {
            val id = repository.startSession(now, bootAt)
            sessionId = id
            Log.w(TAG, "Live lock connected: session $id, phone booted at $bootAt")
            heartbeatJob = launch { heartbeatLoop(id) }
        }
        scope.launch(Dispatchers.IO) { tracked = repository.trackedPackages() }
    }

    /** Every 5 minutes: write the heartbeat if a night with locks or a day with limits is running. */
    private suspend fun heartbeatLoop(id: Long) {
        while (scope.isActive) {
            repository.heartbeatIfItMatters(id)
            delay(LiveLockDecider.HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Only apps and the home screen count; overlays (keyboard, status bar, Vivo gestures) are ignored.
        val known = tracked
        if (known != null && pkg !in known) return
        if (pkg == lastPackage) return // the same app, still in front
        lastPackage = pkg
        scope.launch {
            when (val decision = repository.decide(pkg)) {
                is LockDecision.Lock -> {
                    Log.w(TAG, "LOCK $pkg (${decision.reason}, period ${decision.periodDate})")
                    // Allowed from the background because the system has bound this accessibility service.
                    startActivity(LockActivity.intent(this@LockAccessibilityService, pkg, decision.reason, decision.periodDate))
                }
                is LockDecision.Allow -> Log.w(TAG, "allow $pkg, recheck at ${decision.recheckAtMillis}")
            }
        }
    }

    override fun onInterrupt() = Unit // only called for spoken/feedback services; nothing to stop

    /**
     * A clean stop: you switched the lock off in Settings, or the system unbound it. The end
     * time is saved right here, synchronously, because the scope is about to be cancelled.
     * (A force-stop or a kill never reaches this point; see LiveLockDecider.lockOffIntervals.)
     */
    override fun onUnbind(intent: Intent?): Boolean {
        recordStop("unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        recordStop("destroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun recordStop(why: String) {
        val id = sessionId ?: return
        sessionId = null // record once, even though both onUnbind and onDestroy run
        _running.value = false
        heartbeatJob?.cancel()
        // runBlocking is acceptable here: one tiny write, capped at 1.5 s, at shutdown only.
        runBlocking { withTimeoutOrNull(1_500) { repository.endSession(id, System.currentTimeMillis()) } }
        Log.w(TAG, "Live lock stopped ($why): session $id")
    }

    companion object {
        private const val TAG = "LiveLock"

        // True while the service runs in this process. Read by the off-time detection and,
        // later, by Settings' "Live lock" row.
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
    }
}
