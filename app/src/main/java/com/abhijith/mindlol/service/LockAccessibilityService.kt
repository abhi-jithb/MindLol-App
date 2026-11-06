package com.abhijith.mindlol.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.LiveLockRepository
import com.abhijith.mindlol.domain.LiveLockDecider
import com.abhijith.mindlol.domain.LockDecision
import com.abhijith.mindlol.ui.lock.LockActivity
import kotlinx.coroutines.CancellationException
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
    // Null until connected (not lateinit: an early event must never crash the process).
    private var repository: LiveLockRepository? = null
    private var sessionId: Long? = null
    private var heartbeatJob: Job? = null
    private var lastPackage: String? = null
    private var recheckJob: Job? = null
    // True while our lock screen covers lastPackage.
    private var lockShowing = false
    private var receiverRegistered = false
    // Apps and the home screen; null until worked out (it lists installed apps, so it runs off the main thread).
    private var tracked: Set<String>? = null

    /**
     * Runs [block], and if it throws, logs it (Log.w) and carries on. Every callback the system
     * calls goes through this: this service shares its process with the app, so one uncaught
     * exception would kill both, and the system would then show the lock as stopped.
     */
    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Live lock: $what failed, still running", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        guarded("connect") {
            val repo = (application as MindLolApplication).container.liveLockRepository
            repository = repo
            _running.value = true
            val now = System.currentTimeMillis()
            // elapsedRealtime = time since the phone booted, so this is the boot moment.
            val bootAt = now - SystemClock.elapsedRealtime()
            scope.launch {
                val id = repo.startSession(now, bootAt)
                sessionId = id
                Log.w(TAG, "Live lock connected: session $id, phone booted at $bootAt")
                heartbeatJob = launch { heartbeatLoop(repo, id) }
            }
            scope.launch(Dispatchers.IO) { tracked = repo.trackedPackages() }
        }
        guarded("register screen receiver") {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }

    /**
     * Every 5 minutes: write the heartbeat if a night with locks or a day with limits is running.
     * One failed write is logged and the loop keeps going.
     */
    private suspend fun heartbeatLoop(repo: LiveLockRepository, id: Long) {
        while (scope.isActive) {
            try {
                repo.heartbeatIfItMatters(id)
            } catch (e: CancellationException) {
                throw e // the service is stopping: let the loop end
            } catch (e: Exception) {
                Log.w(TAG, "Live lock: heartbeat failed, still running", e)
            }
            delay(LiveLockDecider.HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        guarded("window event") { handleEvent(event) }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Only apps and the home screen count; overlays (keyboard, status bar, Vivo gestures) are ignored.
        val known = tracked
        if (known != null && pkg !in known) return
        // Our own lock screen: the covered app stays "in front" (lastPackage), so it's checked again
        // when the lock closes without a grant (screen off) or when the app comes back.
        if (pkg == packageName && event.className?.toString() == LockActivity::class.java.name) {
            lockShowing = true
            recheckJob?.cancel()
            return
        }
        // The same app, still in front. After a lock screen it's decided again (a grant may now exist).
        if (pkg == lastPackage && !lockShowing) return
        lockShowing = false
        lastPackage = pkg
        // A new app is in front: any timer for the previous one no longer applies.
        recheckJob?.cancel()
        scope.launch { decideAndAct(pkg, "opened") }
    }

    /**
     * Asks for a decision and acts on it. On Allow with a recheck time (a grant ends, bedtime
     * starts, a limit is reached), a timer asks again at that moment if the app is still in front:
     * that's how an app you keep open gets locked again when its unlock time runs out.
     */
    private suspend fun decideAndAct(pkg: String, why: String) {
        val repo = repository ?: return
        val decision = try {
            repo.decide(pkg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Live lock: decision for $pkg failed, still running", e)
            return
        }
        when (decision) {
            is LockDecision.Lock -> {
                Log.w(TAG, "LOCK $pkg (${decision.reason}, period ${decision.periodDate}, $why)")
                // Allowed from the background because the system has bound this accessibility service.
                guarded("open lock screen") {
                    startActivity(LockActivity.intent(this@LockAccessibilityService, pkg, decision.reason, decision.periodDate))
                }
            }
            is LockDecision.Allow -> {
                val at = decision.recheckAtMillis
                Log.w(TAG, "allow $pkg ($why), recheck at $at")
                if (at != null) scheduleRecheck(pkg, at)
            }
        }
    }

    private fun scheduleRecheck(pkg: String, atMillis: Long) {
        recheckJob?.cancel()
        recheckJob = scope.launch {
            delay(LiveLockDecider.recheckDelayMillis(atMillis, System.currentTimeMillis()))
            if (lastPackage != pkg) return@launch // you moved on; the next open decides afresh
            // Screen off or still on the lock screen of the phone: wait for "unlocked" (see screenReceiver).
            if (!isUsable()) {
                Log.w(TAG, "recheck $pkg deferred: screen off or phone locked")
                return@launch
            }
            decideAndAct(pkg, "time up")
        }
    }

    /** The screen is on and past the phone's own lock screen, so a lock screen can actually be seen. */
    private fun isUsable(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive == true &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    /**
     * Timers don't run while the phone sleeps, and a grant often runs out with the screen off.
     * When you unlock the phone (USER_PRESENT), or turn the screen on with no phone lock
     * (SCREEN_ON), the app still in front is checked again, so it can't stay open past its time.
     * System broadcasts: no permission needed, and registered only while the service runs.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            guarded("screen broadcast") {
                val pkg = lastPackage ?: return
                if (intent.action == Intent.ACTION_SCREEN_ON && !isUsable()) return // USER_PRESENT will follow
                recheckJob?.cancel()
                scope.launch { decideAndAct(pkg, "screen back on") }
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
        guarded("unbind") { recordStop("unbind") }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        guarded("destroy") { recordStop("destroy") }
        scope.cancel()
        super.onDestroy()
    }

    private fun recordStop(why: String) {
        // Stop the relock timer and the screen receiver even if the session row was never saved.
        recheckJob?.cancel()
        heartbeatJob?.cancel()
        _running.value = false
        if (receiverRegistered) {
            receiverRegistered = false
            runCatching { unregisterReceiver(screenReceiver) }
        }
        val id = sessionId ?: return
        sessionId = null // record once, even though both onUnbind and onDestroy run
        val repo = repository ?: return
        // runBlocking is acceptable here: one tiny write, capped at 1.5 s, at shutdown only.
        runBlocking { withTimeoutOrNull(1_500) { repo.endSession(id, System.currentTimeMillis()) } }
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
