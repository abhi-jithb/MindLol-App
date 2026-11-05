package com.abhijith.mindlol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Every slot is set on purpose. Any slot left out falls back to Material's
// default purple, which would leak into dialogs, pickers and switches.
private val MindLolColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = DarkGray,
    onPrimaryContainer = White,
    inversePrimary = Black,

    secondary = Gray,
    onSecondary = Black,
    secondaryContainer = MidGray,
    onSecondaryContainer = White,

    tertiary = LightGray,
    onTertiary = Black,
    tertiaryContainer = DarkGray,
    onTertiaryContainer = White,

    background = Black,
    onBackground = White,

    surface = Black,
    onSurface = White,
    surfaceVariant = DarkGray,
    onSurfaceVariant = Gray,
    surfaceTint = Black, // no tint: elevated surfaces stay neutral gray
    inverseSurface = White,
    inverseOnSurface = Black,

    surfaceDim = Black,
    surfaceBright = MidGray,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Charcoal,
    surfaceContainer = Charcoal,
    surfaceContainerHigh = DarkGray,
    surfaceContainerHighest = MidGray,

    // Errors stay monochrome too: white on dark gray.
    error = White,
    onError = Black,
    errorContainer = DarkGray,
    onErrorContainer = White,

    outline = Gray,
    outlineVariant = MidGray,
    scrim = Black
)

@Composable
fun MindLolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MindLolColors,
        typography = MindLolTypography,
        content = content
    )
}
