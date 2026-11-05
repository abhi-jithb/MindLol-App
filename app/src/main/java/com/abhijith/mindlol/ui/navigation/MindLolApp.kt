package com.abhijith.mindlol.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.abhijith.mindlol.ui.screens.cheatlog.CheatLogScreen
import com.abhijith.mindlol.ui.screens.home.HomeScreen
import com.abhijith.mindlol.ui.screens.onboarding.UsageAccessScreen
import com.abhijith.mindlol.ui.screens.profile.ProfileScreen
import com.abhijith.mindlol.ui.screens.settings.SettingsScreen
import com.abhijith.mindlol.ui.screens.splash.SplashScreen
import com.abhijith.mindlol.ui.screens.stats.StatsScreen
import com.abhijith.mindlol.ui.theme.Motion

// Not tabs, so they are not in Destination.
private const val SPLASH_ROUTE = "splash"
private const val USAGE_ACCESS_ROUTE = "usage-access"

/**
 * Root of the UI.
 * [usageAccessGranted] is the live permission state; while it is false the tabs are
 * replaced by the usage-access screen. [onOpenUsageSettings] opens system Settings,
 * [onOpenAppSettings] opens MindLol's own App info page.
 */
@Composable
fun MindLolApp(
    usageAccessGranted: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The bar shows only on the five tabs, never on the splash.
    val showBottomBar = Destination.entries.any { it.route == currentRoute }

    // Keep the screen in sync with the permission. Runs again whenever either key changes,
    // e.g. when you come back from Settings or revoke access while the app is open.
    LaunchedEffect(usageAccessGranted, currentRoute) {
        when {
            currentRoute == null || currentRoute == SPLASH_ROUTE -> Unit // splash decides itself
            !usageAccessGranted && currentRoute != USAGE_ACCESS_ROUTE ->
                navController.navigate(USAGE_ACCESS_ROUTE) {
                    popUpTo(navController.graph.id) { inclusive = true } // clear every tab
                }
            usageAccessGranted && currentRoute == USAGE_ACCESS_ROUTE ->
                navController.navigate(Destination.HOME.route) {
                    popUpTo(USAGE_ACCESS_ROUTE) { inclusive = true }
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(Motion.enter(Motion.LONG)) { it } + fadeIn(Motion.enter(Motion.LONG)),
                exit = fadeOut(Motion.exit())
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    // Home is the root once the splash is gone, so tabs pop back to it.
                                    popUpTo(Destination.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(dest.icon),
                                    contentDescription = null // the label already names the tab
                                )
                            },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SPLASH_ROUTE,
            modifier = Modifier.padding(innerPadding),
            // Same animation forwards and back; direction comes from tab order (see Transitions.kt).
            enterTransition = { screenEnter() },
            exitTransition = { screenExit() },
            popEnterTransition = { screenEnter() },
            popExitTransition = { screenExit() }
        ) {
            composable(SPLASH_ROUTE) {
                SplashScreen(
                    onFinished = {
                        val next = if (usageAccessGranted) Destination.HOME.route else USAGE_ACCESS_ROUTE
                        navController.navigate(next) {
                            // Remove the splash from the back stack, so Back on Home exits the app.
                            popUpTo(SPLASH_ROUTE) { inclusive = true }
                        }
                    }
                )
            }
            composable(USAGE_ACCESS_ROUTE) { UsageAccessScreen(onOpenSettings = onOpenUsageSettings) }
            composable(Destination.HOME.route) { HomeScreen() }
            composable(Destination.STATS.route) { StatsScreen() }
            composable(Destination.CHEATLOG.route) { CheatLogScreen() }
            composable(Destination.PROFILE.route) { ProfileScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    usageAccessGranted = usageAccessGranted,
                    onOpenUsageSettings = onOpenUsageSettings,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
        }
    }
}
