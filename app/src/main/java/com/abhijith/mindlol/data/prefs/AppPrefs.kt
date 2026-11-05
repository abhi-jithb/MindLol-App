package com.abhijith.mindlol.data.prefs

import android.content.Context

/**
 * Small on-device flags, stored with SharedPreferences (a key-value file, like localStorage).
 * Kept here so a later move to DataStore only touches this file.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("mindlol_prefs", Context.MODE_PRIVATE)

    /** True once the one-time history import has finished. */
    var historyImported: Boolean
        get() = prefs.getBoolean(KEY_HISTORY_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_HISTORY_IMPORTED, value).apply()

    private companion object {
        const val KEY_HISTORY_IMPORTED = "history_imported"
    }
}
