package com.abhijith.mindlol.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.abhijith.mindlol.domain.RoastIntensity
import com.abhijith.mindlol.domain.Settings
import com.abhijith.mindlol.domain.TonightOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The user's rules, stored with DataStore (a small on-disk key-value store that hands back a Flow).
 * [settings] emits again after every update, so screens and the rules engine always see the latest.
 * Every update is a suspend function: DataStore writes on its own thread and never blocks the UI.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data
        // A corrupt or unreadable file shouldn't crash the app: fall back to defaults.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs.toSettings() }

    suspend fun setBedtime(minutes: Int) = edit { it[BEDTIME] = minutes }

    suspend fun setWake(minutes: Int) = edit { it[WAKE] = minutes }

    /** Pass null to turn the override off. */
    suspend fun setTonightOverride(override: TonightOverride?) = edit {
        if (override == null) {
            it.remove(OVERRIDE_BEDTIME)
            it.remove(OVERRIDE_WAKE)
            it.remove(OVERRIDE_EXPIRES)
        } else {
            it[OVERRIDE_BEDTIME] = override.bedtimeMinutes
            it[OVERRIDE_WAKE] = override.wakeMinutes
            it[OVERRIDE_EXPIRES] = override.expiresAtMillis
        }
    }

    suspend fun setLocked(packageName: String, locked: Boolean) = edit {
        val current = it[LOCKED_APPS].orEmpty()
        it[LOCKED_APPS] = if (locked) current + packageName else current - packageName
    }

    /** 0 or less removes the limit. */
    suspend fun setLimit(packageName: String, minutes: Int) = edit {
        val limits = SettingsCodec.decodeLimits(it[APP_LIMITS].orEmpty()).toMutableMap()
        if (minutes > 0) limits[packageName] = minutes else limits.remove(packageName)
        it[APP_LIMITS] = SettingsCodec.encodeLimits(limits)
    }

    suspend fun setRoastIntensity(intensity: RoastIntensity) = edit { it[ROAST_INTENSITY] = intensity.name }

    // edit {} runs the block as one atomic read-modify-write, then saves to disk.
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): Settings {
        val overrideBed = this[OVERRIDE_BEDTIME]
        val overrideWake = this[OVERRIDE_WAKE]
        val overrideExpires = this[OVERRIDE_EXPIRES]
        return Settings(
            bedtimeMinutes = this[BEDTIME] ?: Settings.DEFAULT_BEDTIME,
            wakeMinutes = this[WAKE] ?: Settings.DEFAULT_WAKE,
            tonightOverride = if (overrideBed != null && overrideWake != null && overrideExpires != null) {
                TonightOverride(overrideBed, overrideWake, overrideExpires)
            } else null,
            lockedApps = this[LOCKED_APPS].orEmpty(),
            appLimits = SettingsCodec.decodeLimits(this[APP_LIMITS].orEmpty()),
            roastIntensity = SettingsCodec.decodeIntensity(this[ROAST_INTENSITY])
        )
    }

    private companion object {
        val BEDTIME = intPreferencesKey("bedtime_minutes")
        val WAKE = intPreferencesKey("wake_minutes")
        val OVERRIDE_BEDTIME = intPreferencesKey("override_bedtime_minutes")
        val OVERRIDE_WAKE = intPreferencesKey("override_wake_minutes")
        val OVERRIDE_EXPIRES = longPreferencesKey("override_expires_at_millis")
        val LOCKED_APPS = stringSetPreferencesKey("locked_apps")
        val APP_LIMITS = stringSetPreferencesKey("app_limits")
        val ROAST_INTENSITY = stringPreferencesKey("roast_intensity")
    }
}
