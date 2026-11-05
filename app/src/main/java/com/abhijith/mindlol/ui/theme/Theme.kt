package com.abhijith.mindlol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MindLolColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = DarkGray,
    onSurfaceVariant = Gray,
    secondary = Gray,
    onSecondary = Black
)

@Composable
fun MindLolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MindLolColors,
        typography = MindLolTypography,
        content = content
    )
}