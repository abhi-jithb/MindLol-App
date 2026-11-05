package com.abhijith.mindlol.ui.navigation

import androidx.annotation.DrawableRes
import com.abhijith.mindlol.R

// The five bottom-bar tabs. Order here is the order in the bar.
enum class Destination(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Home", R.drawable.ic_tab_home),
    STATS("stats", "Stats", R.drawable.ic_tab_stats),
    CHEATLOG("cheatlog", "Cheats", R.drawable.ic_tab_cheats),
    PROFILE("profile", "Profile", R.drawable.ic_tab_profile),
    SETTINGS("settings", "Settings", R.drawable.ic_tab_settings)
}
