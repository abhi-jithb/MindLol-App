package com.abhijith.mindlol.ui.lock

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhijith.mindlol.data.repository.LockScreenInfo
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.MascotReaction
import com.abhijith.mindlol.domain.CardReaction
import com.abhijith.mindlol.domain.InterrogationCards
import com.abhijith.mindlol.domain.UnlockSteps
import com.abhijith.mindlol.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun LockScreen(viewModel: LockViewModel, onGoBackToSleep: () -> Unit, onUnlocked: (packageName: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // .background before the inset padding, so the black reaches behind the status and nav bars.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val info = state.info ?: return@Column // the first frame, before the lines are ready
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            modifier = Modifier.weight(1f),
            label = "lockStep"
        ) { step ->
            when (step) {
                LockScreenStep.STARE -> StareStep(info, state.countdownLeft, onGoBackToSleep, viewModel::wantToCheat)
                LockScreenStep.CONFESSION -> ConfessionStep(info, viewModel, onGoBackToSleep)
                LockScreenStep.INTERROGATION -> InterrogationStep(info, state, viewModel, onGoBackToSleep)
                LockScreenStep.BARGAIN -> BargainStep(info, viewModel, onGoBackToSleep)
                LockScreenStep.LAST_CHANCE -> LastChanceStep(info, state.minutes ?: 0, { viewModel.holdCompleted(onUnlocked) }, onGoBackToSleep)
            }
        }
    }
}

/** Step 1: the mascot stares; the big way out is white, the small way in is gray and waits. */
@Composable
private fun StareStep(info: LockScreenInfo, countdownLeft: Int, onGoBackToSleep: () -> Unit, onWantToCheat: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mascot(mood = MascotMood.NEUTRAL, reaction = MascotReaction.STARE, size = 200.dp)
        Spacer(Modifier.height(32.dp))
        Text(info.stareLine, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        GoBackButton(onGoBackToSleep)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onWantToCheat, enabled = countdownLeft == 0) {
            Text(
                if (countdownLeft > 0) "I want to cheat ($countdownLeft s)" else "I want to cheat",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Step 2: type the sentence yourself. Paste, suggestions and swipe-typing are all refused. */
@Composable
private fun ConfessionStep(info: LockScreenInfo, viewModel: LockViewModel, onGoBackToSleep: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Mascot(mood = MascotMood.NEUTRAL, reaction = MascotReaction.FACEPALM, size = 140.dp)
        Spacer(Modifier.height(16.dp))
        Text(info.confessionLine, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("Type exactly:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "\"${info.requiredConfession}\"",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            state = viewModel.confession,
            inputTransformation = TypedOnly,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::confess,
            enabled = viewModel.confessionMatches(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Confess") }
        Spacer(Modifier.height(8.dp))
        GoBackButton(onGoBackToSleep)
    }
}

/**
 * Step 3: one question card. Two answers pop in on a spring; whichever you tap, the mascot reacts
 * and its reply types out letter by letter. "Continue" only shows once the reply is fully typed.
 */
@Composable
private fun InterrogationStep(info: LockScreenInfo, state: LockUiState, viewModel: LockViewModel, onGoBackToSleep: () -> Unit) {
    val card = info.card ?: return
    val answer = state.answer
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Mascot(mood = MascotMood.NEUTRAL, reaction = answer?.reaction?.toMascot() ?: MascotReaction.NONE, size = 140.dp)
        Spacer(Modifier.height(24.dp))
        Text(card.question, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (answer == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                card.answers.forEachIndexed { index, option ->
                    SpringIn(index, Modifier.weight(1f)) {
                        OutlinedButton(onClick = { viewModel.answer(option) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(option.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        } else {
            TypedReply(
                text = InterrogationCards.reply(answer, info.appName),
                alreadyTyped = state.replyTyped,
                onFinished = viewModel::replyFinished
            )
        }
        Spacer(Modifier.weight(1f))
        if (answer != null && state.replyTyped) {
            Button(onClick = viewModel::continueAfterReply, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Continue") }
            Spacer(Modifier.height(8.dp))
        }
        GoBackButton(onGoBackToSleep)
    }
}

/** Step 4: pick how long. The line reminds you how many cheats this week already. */
@Composable
private fun BargainStep(info: LockScreenInfo, viewModel: LockViewModel, onGoBackToSleep: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Mascot(mood = MascotMood.NEUTRAL, reaction = MascotReaction.HEAD_SHAKE, size = 140.dp)
        Spacer(Modifier.height(24.dp))
        Text(info.bargainLine, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UnlockSteps.BARGAIN_MINUTES.forEachIndexed { index, minutes ->
                SpringIn(index, Modifier.weight(1f)) {
                    OutlinedButton(onClick = { viewModel.bargain(minutes) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("$minutes min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        GoBackButton(onGoBackToSleep)
    }
}

/** Step 5: the mascot turns its back. Hold the button for 3 seconds; letting go starts again. */
@Composable
private fun LastChanceStep(info: LockScreenInfo, minutes: Int, onHoldCompleted: () -> Unit, onGoBackToSleep: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Mascot(mood = MascotMood.NEUTRAL, reaction = MascotReaction.TURN_AWAY, size = 140.dp)
        Spacer(Modifier.height(24.dp))
        Text(info.lastChanceLine, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        HoldButton(label = "Hold to open ${info.appName} for $minutes min", onDone = onHoldCompleted)
        Spacer(Modifier.height(8.dp))
        GoBackButton(onGoBackToSleep)
    }
}

/** Pops its content in with a bouncy spring, one after another by [index]. */
@Composable
private fun SpringIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(SPRING_STAGGER_MS * index)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value.coerceIn(0f, 1f) }) { content() }
}

/**
 * The reply, typed out letter by letter. Timed with delay() (wall clock), so it still types
 * with the phone's animations off. TalkBack reads the whole reply at once.
 * [alreadyTyped]: after a rotation, show it complete instead of typing it again.
 */
@Composable
private fun TypedReply(text: String, alreadyTyped: Boolean, onFinished: () -> Unit) {
    var shown by remember(text) { mutableIntStateOf(if (alreadyTyped) text.length else 0) }
    LaunchedEffect(text) {
        val start = SystemClock.uptimeMillis()
        while (shown < text.length) {
            delay(UnlockSteps.TYPE_MILLIS_PER_CHAR)
            shown = UnlockSteps.typedLength(SystemClock.uptimeMillis() - start, text.length)
        }
        onFinished()
    }
    Text(
        text.take(shown),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = text }
    )
}

/**
 * A button you must hold down for 3 seconds. The fill grows from the left while held; letting go
 * (or sliding off) empties it. Timed by the wall clock, not an animation, so turning the phone's
 * animations off can't skip the wait.
 */
@Composable
private fun HoldButton(label: String, onDone: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pressed) {
        if (!pressed) {
            progress = 0f
            return@LaunchedEffect
        }
        val start = SystemClock.uptimeMillis()
        while (true) {
            val held = SystemClock.uptimeMillis() - start
            progress = UnlockSteps.holdProgress(held)
            if (UnlockSteps.holdDone(held)) {
                onDone()
                break
            }
            delay(16)
        }
    }
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    // Null when the finger slides off or the gesture is cancelled: that's letting go too.
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
    }
}

private fun CardReaction.toMascot(): MascotReaction = when (this) {
    CardReaction.STARE -> MascotReaction.STARE
    CardReaction.FACEPALM -> MascotReaction.FACEPALM
    CardReaction.HEAD_SHAKE -> MascotReaction.HEAD_SHAKE
    CardReaction.TURN_AWAY -> MascotReaction.TURN_AWAY
    CardReaction.NOD -> MascotReaction.NOD
}

private const val SPRING_STAGGER_MS = 120L

@Composable
private fun GoBackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background
        ),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text("Go back to sleep", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Accepts only one typed character at a time (and deleting). Pasting, tapping a keyboard
 * suggestion, autocorrect and swipe-typing all insert several characters at once, so they're undone.
 * `changes` (what this edit changed) is still marked experimental in Compose, hence the opt-in.
 */
@OptIn(ExperimentalFoundationApi::class)
private object TypedOnly : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        for (i in 0 until changes.changeCount) {
            val inserted = changes.getRange(i).length - changes.getOriginalRange(i).length
            if (inserted > 1) {
                revertAllChanges()
                return
            }
        }
    }
}
