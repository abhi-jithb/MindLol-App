package com.abhijith.mindlol.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.abhijith.mindlol.ui.theme.Motion

@Composable
fun LockScreen(viewModel: LockViewModel, onGoBackToSleep: () -> Unit) {
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
                LockScreenStep.NOT_BUILT_YET -> NotBuiltYet(onGoBackToSleep)
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

/** Steps 3–5 come in the next build; until then the flow stops here. */
@Composable
private fun NotBuiltYet(onGoBackToSleep: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mascot(mood = MascotMood.NEUTRAL, reaction = MascotReaction.HEAD_SHAKE, size = 160.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Confession noted. Steps 3 to 5 aren't built yet, so there's no way in tonight.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        GoBackButton(onGoBackToSleep)
    }
}

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
