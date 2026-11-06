package com.abhijith.mindlol.ui.lock

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhijith.mindlol.data.repository.LiveLockRepository
import com.abhijith.mindlol.data.repository.LockScreenInfo
import com.abhijith.mindlol.domain.CardAnswer
import com.abhijith.mindlol.domain.Confession
import com.abhijith.mindlol.domain.LockOutcome
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.domain.LockStep
import com.abhijith.mindlol.domain.UnlockSteps
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which screen of the unlock flow is showing. */
enum class LockScreenStep { STARE, CONFESSION, INTERROGATION, BARGAIN, LAST_CHANCE }

data class LockUiState(
    val info: LockScreenInfo? = null,
    val step: LockScreenStep = LockScreenStep.STARE,
    /** Seconds left before "I want to cheat" can be pressed. */
    val countdownLeft: Int = 0,
    /** Step 3: the answer you tapped (null = not answered yet). */
    val answer: CardAnswer? = null,
    /** Step 3: the reply has finished typing out, so "Continue" can show. */
    val replyTyped: Boolean = false,
    /** Step 4: the minutes you bargained for. */
    val minutes: Int? = null
)

/**
 * The lock screen's state. Survives rotation (a ViewModel), and keeps the lock_event row up to date:
 * the furthest step reached, and how the screen ended.
 */
class LockViewModel(private val repository: LiveLockRepository) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state

    /** The confession field. TextFieldState keeps the text and cursor, like a controlled input in React. */
    val confession = TextFieldState()

    private var openedFor: Pair<Triple<String, LockReason, LocalDate>, Boolean>? = null
    private var finished = false

    /**
     * Opens the lock for an app. Calling it again for the same app (e.g. after rotation) does nothing.
     * [test]: a debug test lock screen that writes nothing (see LiveLockRepository.openLockScreen).
     */
    fun open(
        packageName: String,
        reason: LockReason,
        periodDate: LocalDate,
        test: Boolean = false,
        testStartAt: LockScreenStep? = null,
        testAnswer: Int = -1
    ) {
        val key = Triple(packageName, reason, periodDate) to test
        if (openedFor == key) return
        openedFor = key
        finished = false
        confession.edit { replace(0, length, "") }
        viewModelScope.launch {
            val info = repository.openLockScreen(packageName, reason, periodDate, test = test)
            _state.value = LockUiState(info = info, step = LockScreenStep.STARE, countdownLeft = info.countdownSeconds)
            // Debug screenshots only: a test lock (writes nothing) can open straight at a later step.
            if (test && testStartAt != null && testStartAt != LockScreenStep.STARE) {
                val answer = info.card?.answers?.getOrNull(testAnswer)
                _state.value = LockUiState(
                    info = info, step = testStartAt, countdownLeft = 0,
                    answer = answer.takeIf { testStartAt == LockScreenStep.INTERROGATION },
                    minutes = UnlockSteps.BARGAIN_MINUTES[1]
                )
                return@launch
            }
            while (_state.value.countdownLeft > 0) {
                delay(1_000)
                _state.update { it.copy(countdownLeft = (it.countdownLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun wantToCheat() {
        val info = _state.value.info ?: return
        if (_state.value.countdownLeft > 0) return
        _state.update { it.copy(step = LockScreenStep.CONFESSION) }
        viewModelScope.launch { repository.reachStep(info.eventId, LockStep.CONFESSION) }
    }

    fun confessionMatches(): Boolean {
        val info = _state.value.info ?: return false
        return Confession.matches(confession.text.toString(), info.requiredConfession)
    }

    /** Confession accepted: on to step 3, the interrogation. The card counts as shown from here. */
    fun confess() {
        val info = _state.value.info ?: return
        if (_state.value.step != LockScreenStep.CONFESSION || !confessionMatches()) return
        val card = info.card
        if (card == null) {
            // No card is allowed at all (empty card list): skip straight to the bargain.
            toBargain()
            return
        }
        _state.update { it.copy(step = LockScreenStep.INTERROGATION, answer = null, replyTyped = false) }
        viewModelScope.launch { repository.showCard(info.eventId, card.id) }
    }

    /** Step 3: one answer per card. The mascot reacts and the reply types out. */
    fun answer(answer: CardAnswer) {
        val info = _state.value.info ?: return
        val card = info.card ?: return
        if (_state.value.step != LockScreenStep.INTERROGATION || _state.value.answer != null) return
        if (answer !in card.answers) return
        _state.update { it.copy(answer = answer, replyTyped = false) }
        viewModelScope.launch { repository.answerCard(info.eventId, answer.label) }
    }

    fun replyFinished() {
        if (_state.value.answer != null) _state.update { it.copy(replyTyped = true) }
    }

    /** Step 3 → 4. Only after answering and reading the reply. */
    fun continueAfterReply() {
        val s = _state.value
        if (s.step != LockScreenStep.INTERROGATION || s.answer == null || !s.replyTyped) return
        toBargain()
    }

    private fun toBargain() {
        val info = _state.value.info ?: return
        _state.update { it.copy(step = LockScreenStep.BARGAIN) }
        viewModelScope.launch { repository.reachStep(info.eventId, LockStep.BARGAIN) }
    }

    /** Step 4 → 5: 2, 5 or 10 minutes. */
    fun bargain(minutes: Int) {
        val info = _state.value.info ?: return
        if (_state.value.step != LockScreenStep.BARGAIN || !UnlockSteps.isBargainOption(minutes)) return
        _state.update { it.copy(step = LockScreenStep.LAST_CHANCE, minutes = minutes) }
        viewModelScope.launch { repository.reachStep(info.eventId, LockStep.LAST_CHANCE) }
    }

    /**
     * Step 5: the button was held for the full 3 seconds. Saves the grant first, then calls
     * [onUnlocked] so the lock screen closes and the app opens. The order matters: the live lock
     * must find the grant when the app comes back to the front, or it would lock it again.
     * A test lock screen saves nothing and still opens the app.
     */
    fun holdCompleted(onUnlocked: (packageName: String) -> Unit) {
        val s = _state.value
        val info = s.info ?: return
        val minutes = s.minutes ?: return
        if (s.step != LockScreenStep.LAST_CHANCE || finished) return
        finished = true // from here, leaving can't overwrite UNLOCKED with LEFT
        openedFor = null
        viewModelScope.launch {
            val saved = repository.grant(info.eventId, minutes)
            Log.w(TAG, if (saved) "UNLOCKED ${info.packageName} for $minutes min" else "Unlock of ${info.packageName} not saved (test lock screen or already closed)")
            onUnlocked(info.packageName)
        }
    }

    /** "Go back to sleep" or the back button. */
    fun goBack() = end(LockOutcome.WENT_BACK)

    /** Left another way (Home, screen off, another app). LEFT is already the saved default. */
    fun left() = end(LockOutcome.LEFT)

    private fun end(outcome: LockOutcome) {
        val info = _state.value.info ?: return
        if (finished) return
        finished = true
        openedFor = null
        viewModelScope.launch { repository.endLockScreen(info.eventId, outcome) }
    }

    private companion object {
        const val TAG = "LiveLock"
    }
}
