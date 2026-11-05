package com.abhijith.mindlol

import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import android.os.Build
import com.abhijith.mindlol.ui.theme.MindLolTheme

import com.abhijith.mindlol.ui.screens.home.HomeScreen

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
                HomeScreen()
            }
        }
    }
}