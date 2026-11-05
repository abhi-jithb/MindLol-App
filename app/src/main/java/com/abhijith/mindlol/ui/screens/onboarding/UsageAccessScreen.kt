package com.abhijith.mindlol.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme

/**
 * Shown instead of the tabs while usage access is off.
 * Android has no popup for this permission, so the button sends you to Settings.
 */
@Composable
fun UsageAccessScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(mood = MascotMood.SLEEPY, size = 140.dp, modifier = Modifier.revealOnEnter(0))
        Spacer(Modifier.height(32.dp))
        Text(
            text = "I can't roast what I can't see.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(1)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Turn on Usage access for MindLol. Your data never leaves this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(2)
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp).revealOnEnter(3)
        ) {
            Text("Open settings", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "USAGE ACCESS → MINDLOL → ALLOW",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.revealOnEnter(4)
        )
    }
}

@Preview
@Composable
private fun UsageAccessScreenPreview() {
    MindLolTheme { UsageAccessScreen(onOpenSettings = {}) }
}
