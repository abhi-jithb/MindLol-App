package com.abhijith.mindlol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import androidx.activity.ComponentActivity

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.abhijith.mindlol.data.usage.UsagePermission
import com.abhijith.mindlol.ui.theme.MindLolTheme
import kotlinx.coroutines.CancellationException
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
                    onOpenUsageSettings = { UsagePermission.openSettings(this) },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        )
                    }
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
        // Pull fresh numbers from Android into Room on every app open (also runs the one-time import).
        if (container.usagePermission.granted.value) {
            container.applicationScope.launch {
                try {
                    container.usageRepository.refresh()
                } catch (e: CancellationException) {
                    throw e // normal coroutine cancellation, not an error: let it through
                } catch (e: Exception) {
                    // A failed refresh must never crash the app. Each day is written in its own
                    // transaction, so whatever is already in Room stays and screens keep showing it.
                    Log.w(TAG, "Refresh on app open failed; keeping old data", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}