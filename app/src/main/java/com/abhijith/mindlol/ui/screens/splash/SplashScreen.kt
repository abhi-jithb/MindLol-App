package com.abhijith.mindlol.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * Opening sequence: the mascot rises in asleep, wakes up, smirks, then the name appears.
 * Calls [onFinished] once at the end; the caller navigates away.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val mascotIn = remember { Animatable(0f) }
    val nameIn = remember { Animatable(0f) }
    var mood by remember { mutableStateOf(MascotMood.SLEEPY) }
    // Always call the latest onFinished, even if the caller passes a new lambda mid-animation.
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        // Plain delay() ignores the system animation scale, so scale pauses by hand.
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        suspend fun pause(ms: Int) = delay((ms * scale).toLong())

        mascotIn.animateTo(1f, Motion.enter(Motion.EXTRA_LONG))
        mood = MascotMood.NEUTRAL // wakes up
        pause(450)
        mood = MascotMood.SMUG // ...and already judges you
        nameIn.animateTo(1f, Motion.enter(Motion.LONG))
        pause(800)
        finish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(
            mood = mood,
            size = 180.dp,
            modifier = Modifier.graphicsLayer {
                val t = mascotIn.value
                alpha = t
                scaleX = 0.6f + 0.4f * t
                scaleY = 0.6f + 0.4f * t
                translationY = (1f - t) * 48.dp.toPx()
            }
        )
        Spacer(Modifier.height(24.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val t = nameIn.value
                alpha = t
                translationY = (1f - t) * 16.dp.toPx()
            }
        ) {
            Text(
                text = "MindLol",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "SCREEN TIME, ROASTED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    MindLolTheme { SplashScreen(onFinished = {}) }
}
