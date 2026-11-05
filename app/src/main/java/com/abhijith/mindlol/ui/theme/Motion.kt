package com.abhijith.mindlol.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Shared animation timings, so every screen moves the same way.
 *
 * Compose animations (animate*AsState, Animatable, AnimatedVisibility, tween)
 * already follow the phone's "Animator duration scale" developer setting.
 * If that is set to off, they jump straight to the end value.
 */
object Motion {
    // Durations in milliseconds.
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 500
    const val EXTRA_LONG = 900

    // Number count-up on Home.
    const val COUNT_UP = 1200

    // Gap between items that enter one after another (lists, bars).
    const val STAGGER = 40

    // Default easing: starts quickly, settles gently.
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // For things entering the screen: decelerates into place.
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    // For things leaving the screen: accelerates away.
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(durationMillis: Int = MEDIUM, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Standard)

    fun <T> enter(durationMillis: Int = MEDIUM, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Enter)

    fun <T> exit(durationMillis: Int = SHORT, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Exit)
}
