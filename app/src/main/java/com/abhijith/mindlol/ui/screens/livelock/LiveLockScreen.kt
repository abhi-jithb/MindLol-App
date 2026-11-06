package com.abhijith.mindlol.ui.screens.livelock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.domain.LiveLockState
import com.abhijith.mindlol.domain.LiveLockStatus
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood

/**
 * The disclosure shown before MindLol sends you to Accessibility settings (Play's "prominent
 * disclosure" pattern): what the live lock sees, what it never sees, and the shortcut warning.
 * Every sentence must stay true: check it whenever the service's config changes.
 */
@Composable
fun LiveLockScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val state = rememberLiveLockState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(24.dp))
            Mascot(mood = MascotMood.NEUTRAL, size = 96.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Text("Live lock", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                "Right now: ${LiveLockStatus.status(state)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (state == LiveLockState.ON) FontWeight.Normal else FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Block(
                "What it does",
                "When you open an app you locked for the night, during your sleep window, or an app that's over " +
                    "its daily limit, MindLol covers it with the lock screen."
            )
            Block(
                "What it sees",
                "Only which app comes to the front (for example \"Instagram\") and when. That's all it needs to decide."
            )
            Block(
                "What it never sees",
                "What's on your screen, what you type, your messages or your photos. It's set up so it can't read " +
                    "screen content at all."
            )
            Block(
                "Nothing leaves your phone",
                "MindLol has no internet permission. Everything stays on this phone."
            )
            Block(
                "Why Accessibility",
                "It's the only way an app can notice instantly which app you opened. MindLol isn't an accessibility " +
                    "tool; it just uses this one signal."
            )
            Block(
                "Keep the shortcut off",
                "Some phones offer a \"MindLol live lock shortcut\" (a floating button) when you turn it on. " +
                    "Tapping that button switches the lock off. Leave the shortcut off: Accessibility → " +
                    "MindLol live lock → shortcut."
            )
            Block(
                "How to turn it on",
                "On the next screen, find \"MindLol live lock\", turn it on and allow it. If the switch is greyed " +
                    "out: App info → ⋮ menu → Allow restricted settings, then try again."
            )
            Block(
                "Turning it off",
                "Any time, in the same Accessibility settings."
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { openAccessibilitySettings(context) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Open Accessibility settings", style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Log.w("Settings", "Couldn't open Accessibility settings", e)
    }
}
