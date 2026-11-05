package com.abhijith.mindlol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.abhijith.mindlol.domain.AppTheme

// Every slot is set on purpose. Any slot left out falls back to Material's
// default purple, which would leak into dialogs, pickers and switches.
private val PureBlackColors = darkColorScheme(
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

/** Same as pure black, but the page and surfaces are soft dark grays. */
private val SoftBlackColors = PureBlackColors.copy(
    primaryContainer = SoftSurface,
    secondaryContainer = SoftSurfaceHigh,
    tertiaryContainer = SoftSurface,
    background = SoftBlack,
    surface = SoftBlack,
    surfaceVariant = SoftSurface,
    surfaceTint = SoftBlack,
    surfaceDim = SoftBlack,
    surfaceBright = SoftSurfaceHigh,
    surfaceContainerLowest = SoftBlack,
    surfaceContainerLow = SoftSurface,
    surfaceContainer = SoftSurface,
    surfaceContainerHigh = SoftSurfaceHigh,
    surfaceContainerHighest = SoftOutline,
    errorContainer = SoftSurface,
    outlineVariant = SoftOutline,
    scrim = SoftBlack
)

/** Pure black, but secondary gray text becomes white and dividers get much stronger. */
private val HighContrastColors = PureBlackColors.copy(
    secondary = White,
    tertiary = White,
    onSurfaceVariant = White,
    outline = LightGray,
    outlineVariant = Gray
)

fun colorSchemeFor(theme: AppTheme) = when (theme) {
    AppTheme.PURE_BLACK -> PureBlackColors
    AppTheme.SOFT_BLACK -> SoftBlackColors
    AppTheme.HIGH_CONTRAST -> HighContrastColors
}

/** Every screen reads colors from MaterialTheme, so swapping the scheme restyles the whole app. */
@Composable
fun MindLolTheme(theme: AppTheme = AppTheme.PURE_BLACK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorSchemeFor(theme),
        typography = MindLolTypography,
        content = content
    )
}
