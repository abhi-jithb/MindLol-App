package com.abhijith.mindlol.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.abhijith.mindlol.ui.theme.Motion

// Screen-change animations for the NavHost.
// Between tabs: a short slide (1/12 of the width) toward the tapped tab, plus a fade.
// Anything involving the splash: a plain crossfade.

private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun tabIndex(entry: NavBackStackEntry): Int =
    Destination.entries.indexOfFirst { it.route == entry.destination.route }

/** +1 when moving to a tab on the right, -1 to the left, null when the splash is involved. */
private fun NavScope.direction(): Int? {
    val from = tabIndex(initialState)
    val to = tabIndex(targetState)
    if (from < 0 || to < 0) return null
    return if (to > from) 1 else -1
}

fun NavScope.screenEnter(): EnterTransition {
    val dir = direction() ?: return fadeIn(Motion.enter(Motion.LONG))
    return fadeIn(Motion.enter(Motion.MEDIUM, delayMillis = Motion.SHORT / 2)) +
        slideInHorizontally(Motion.enter(Motion.MEDIUM)) { width -> dir * width / 12 }
}

fun NavScope.screenExit(): ExitTransition {
    val dir = direction() ?: return fadeOut(Motion.standard(Motion.LONG))
    return fadeOut(Motion.exit(Motion.SHORT)) +
        slideOutHorizontally(Motion.exit(Motion.MEDIUM)) { width -> -dir * width / 12 }
}
