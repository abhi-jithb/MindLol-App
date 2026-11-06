package com.abhijith.mindlol.ui.screens.livelock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhijith.mindlol.data.usage.AccessibilityState
import com.abhijith.mindlol.domain.LiveLockState
import com.abhijith.mindlol.domain.LiveLockStatus
import com.abhijith.mindlol.service.LockAccessibilityService

/**
 * The live lock's state for a screen: the Accessibility setting (re-read every time you come back,
 * e.g. from system settings) plus whether the service is running in this process.
 */
@Composable
fun rememberLiveLockState(): LiveLockState {
    val context = LocalContext.current
    val running by LockAccessibilityService.running.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(AccessibilityState.isLiveLockEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = AccessibilityState.isLiveLockEnabled(context)
        onPauseOrDispose { }
    }
    // The running flag changes as the service connects or stops; re-read the setting then too.
    LaunchedEffect(running) { enabled = AccessibilityState.isLiveLockEnabled(context) }
    return LiveLockStatus.state(enabled, running)
}
