package com.abhijith.mindlol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.ui.theme.Motion

/**
 * Fades content in and lifts it 12dp into place when it first appears.
 * Higher [index] starts a little later, so a column of items enters one by one.
 */
@Composable
fun Modifier.revealOnEnter(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, Motion.enter(Motion.LONG, delayMillis = index * Motion.STAGGER * 2))
    }
    // graphicsLayer reads progress while drawing, so the animation never recomposes this screen.
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}
