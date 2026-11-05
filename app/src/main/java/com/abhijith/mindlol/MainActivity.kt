package com.abhijith.mindlol

import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.abhijith.mindlol.data.usage.UsagePermission
import com.abhijith.mindlol.ui.theme.MindLolTheme
import kotlinx.coroutines.launch

import com.abhijith.mindlol.ui.navigation.MindLolApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent{
            MindLolTheme {
                val container = (application as MindLolApplication).container
                // collectAsState turns the StateFlow into Compose state: when it changes, the UI redraws.
                val granted by container.usagePermission.granted.collectAsState()
                MindLolApp(
                    usageAccessGranted = granted,
                    onOpenUsageSettings = { UsagePermission.openSettings(this) }
                )
            }
        }
    }

    // Runs every time the app returns to the foreground, including coming back from
    // system Settings, so a freshly granted (or revoked) usage access shows up at once.
    override fun onResume() {
        super.onResume()
        val container = (application as MindLolApplication).container
        container.usagePermission.refresh()
        // One-time import of older history. Moves into the repository's refresh in the next step.
        if (container.usagePermission.granted.value) {
            container.applicationScope.launch { container.historyImporter.importOnce() }
        }
    }
}