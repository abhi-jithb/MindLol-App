package com.abhijith.mindlol.ui.lock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.domain.AppTheme
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.ui.theme.MindLolTheme
import java.time.LocalDate

/**
 * The lock screen, opened by the live lock service over a locked app. Its own task (see the
 * manifest: singleTask, own taskAffinity, excluded from recents), so it never mixes with MindLol's
 * normal screens. Leaving it any way except finishing the steps sends you to the home screen.
 */
class LockActivity : ComponentActivity() {

    private val viewModel: LockViewModel by viewModels {
        viewModelFactory {
            initializer { LockViewModel((application as MindLolApplication).container.liveLockRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openFrom(intent)
        // Back = give up and go home, never back into the locked app.
        onBackPressedDispatcher.addCallback(this) { goBackToSleep() }
        setContent {
            val container = (application as MindLolApplication).container
            val settings by container.settingsRepository.settings.collectAsState(initial = null)
            MindLolTheme(theme = settings?.theme ?: AppTheme.PURE_BLACK) {
                LockScreen(viewModel = viewModel, onGoBackToSleep = ::goBackToSleep, onUnlocked = ::openUnlockedApp)
            }
        }
    }

    /** Already showing and another lock comes in (singleTask): switch to it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /** Pressing Home, the screen turning off, or switching apps: the attempt ends as LEFT. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            viewModel.left()
            finish()
        }
    }

    private fun openFrom(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { runCatching { LockReason.valueOf(it) }.getOrNull() } ?: return finish()
        val date = intent.getStringExtra(EXTRA_PERIOD)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return finish()
        // A test lock screen is only honoured in a debuggable (debug) build; release ignores the flag.
        val test = intent.getBooleanExtra(EXTRA_TEST, false) && isDebuggable()
        // Test only: open straight at a step, for screenshots. Ignored on a real lock screen.
        val startAt = if (test) intent.getStringExtra(EXTRA_TEST_STEP)?.let { runCatching { LockScreenStep.valueOf(it) }.getOrNull() } else null
        val answer = if (test) intent.getIntExtra(EXTRA_TEST_ANSWER, -1) else -1
        viewModel.open(pkg, reason, date, test, startAt, answer)
    }

    private fun isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Closes the lock and goes to the home screen. The locked app stays behind it, unused. */
    private fun goBackToSleep() {
        viewModel.goBack()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /**
     * All five steps done and the grant saved: close the lock and bring the app back. Its launch
     * intent brings its existing task to the front, so you land where you were.
     */
    private fun openUnlockedApp(packageName: String) {
        if (isFinishing) return // already left (Home, screen off) while the grant was being saved
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_TEST = "test"
        const val EXTRA_TEST_STEP = "testStep"
        const val EXTRA_TEST_ANSWER = "testAnswer"

        /** [test] = a debug test lock screen that writes nothing; only honoured in debug builds. */
        fun intent(context: Context, packageName: String, reason: LockReason, periodDate: LocalDate, test: Boolean = false): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_PERIOD, periodDate.toString())
                .putExtra(EXTRA_TEST, test)
                // A service has no task of its own, so the lock needs a new one.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
