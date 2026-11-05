package com.abhijith.mindlol.ui.lock

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhijith.mindlol.data.repository.LiveLockRepository
import com.abhijith.mindlol.data.repository.LockScreenInfo
import com.abhijith.mindlol.domain.Confession
import com.abhijith.mindlol.domain.LockOutcome
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.domain.LockStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which screen of the unlock flow is showing. Steps 3–5 arrive in item 7. */
enum class LockScreenStep { STARE, CONFESSION, NOT_BUILT_YET }

data class LockUiState(
    val info: LockScreenInfo? = null,
    val step: LockScreenStep = LockScreenStep.STARE,
    /** Seconds left before "I want to cheat" can be pressed. */
    val countdownLeft: Int = 0
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
    fun open(packageName: String, reason: LockReason, periodDate: LocalDate, test: Boolean = false) {
        val key = Triple(packageName, reason, periodDate) to test
        if (openedFor == key) return
        openedFor = key
        finished = false
        confession.edit { replace(0, length, "") }
        viewModelScope.launch {
            val info = repository.openLockScreen(packageName, reason, periodDate, test = test)
            _state.value = LockUiState(info = info, step = LockScreenStep.STARE, countdownLeft = info.countdownSeconds)
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

    /** Confession accepted. Steps 3–5 don't exist yet, so this build stops here. */
    fun confess() {
        if (!confessionMatches()) return
        _state.update { it.copy(step = LockScreenStep.NOT_BUILT_YET) }
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
}
