package com.abhijith.mindlol.data.repository

import android.util.Log
import com.abhijith.mindlol.data.db.RulesDao
import com.abhijith.mindlol.data.db.toDomain
import com.abhijith.mindlol.data.db.toEntity
import com.abhijith.mindlol.data.prefs.AppPrefs
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.data.usage.AppNames
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.domain.Cheat
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.domain.RoastGenerator
import com.abhijith.mindlol.domain.RulesSnapshot
import com.abhijith.mindlol.domain.SleepTime
import com.abhijith.mindlol.domain.PeriodKind
import com.abhijith.mindlol.domain.RulesPlanner
import com.abhijith.mindlol.domain.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Connects the pure rules engine ([RulesPlanner]) to real data: settings from DataStore,
 * sessions from Android, snapshots and cheats in Room.
 */
class CheatRepository(
    private val dao: RulesDao,
    private val settingsRepository: SettingsRepository,
    private val reader: UsageEventsReader,
    private val appNames: AppNames,
    private val prefs: AppPrefs
) {
    // Judging and writing upcoming snapshots both read-then-write the same rows: one at a time.
    private val lock = Mutex()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * Judges every unfinished night and day of the last [daysBack] days and saves the result.
     * Called from UsageRepository.refresh(), so it runs on app open and in the background worker.
     * Needs usage access (the caller checks it).
     */
    suspend fun judge(daysBack: Int = 3, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        lock.withLock {
            val firstDate = today(nowMillis).minusDays(daysBack - 1L)
            val settings = settingsRepository.settings.first()
            val stored = dao.periodsFrom(firstDate.minusDays(1)).map { it.toDomain() }
            val storedCheats = dao.cheatsFrom(firstDate).map { it.toDomain() }
            val sessions = reader.readSessions(firstDate.atStartOfDay(zone).toInstant().toEpochMilli(), nowMillis)

            val result = RulesPlanner.plan(
                nowMillis, zone, settings, firstDate, stored, storedCheats, sessions,
                rulesSinceMillis = rulesSince(nowMillis)
            )
            dao.saveJudgement(result.snapshots.map { it.toEntity() }, result.cheats.map { it.toEntity() })
        }
    }

    /**
     * Writes the snapshots of every night and day that hasn't started yet, from [settings].
     * Rows that have started are frozen and never touched.
     */
    suspend fun writeUpcoming(settings: Settings, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        lock.withLock {
            val stored = dao.periodsFrom(today(nowMillis).minusDays(1)).map { it.toDomain() }
            rulesSince(nowMillis)
            val rows = RulesPlanner.upcomingSnapshots(nowMillis, zone, settings, stored)
            dao.upsertPeriods(rows.map { it.toEntity() })
        }
    }

    /**
     * Keeps upcoming snapshots in step with settings: every time settings change (and once at
     * start), the next night's and day's rules are rewritten. This is what freezes a night
     * with the right rules even if no refresh runs during it.
     */
    fun watchSettings(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                try {
                    writeUpcoming(settings)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write upcoming snapshots", e) // retried on the next change or refresh
                }
            }
        }
    }

    /** Every saved cheat as domain objects, newest first. Emits again whenever a refresh saves cheats. */
    fun cheats(): Flow<List<Cheat>> = dao.observeCheats().map { rows -> rows.map { it.toDomain() } }

    /** Every saved snapshot, oldest first. Used for streaks (which nights had rules, which are final). */
    fun periods(): Flow<List<RulesSnapshot>> = dao.observePeriods().map { rows -> rows.map { it.toDomain() } }

    /**
     * Cheats ready to show, newest first, each with its roast line. The line uses the rules of
     * the cheat's own night or day (its snapshot), so changing settings later doesn't rewrite it.
     * Only the roast intensity comes from current settings.
     */
    fun cheatLog(): Flow<List<CheatEntry>> =
        combine(cheats(), periods(), settingsRepository.settings) { cheats, periods, settings ->
            val byKey = periods.associateBy { it.kind to it.date }
            cheats.map { cheat ->
                val appName = appNames.labelFor(cheat.packageName)
                val night = byKey[PeriodKind.NIGHT to cheat.periodDate]
                val day = byKey[PeriodKind.DAY to cheat.periodDate]
                val context = RoastGenerator.contextFor(
                    cheat = cheat,
                    allCheats = cheats,
                    appName = appName,
                    bedtime = night?.let { localTime(it.startMillis) } ?: SleepTime.toLocalTime(settings.bedtimeMinutes),
                    wake = night?.let { localTime(it.endMillis) } ?: SleepTime.toLocalTime(settings.wakeMinutes),
                    limitMinutes = day?.limits?.get(cheat.packageName),
                    intensity = settings.roastIntensity,
                    zone = zone
                )
                CheatEntry(cheat, appName, RoastGenerator.roast(context), context)
            }
        }.flowOn(Dispatchers.IO) // app names and roasts are built off the main thread

    private fun localTime(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    /** True while tonight's window is running. The override can't be changed then. */
    suspend fun isTonightLocked(nowMillis: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val stored = dao.periodsFrom(today(nowMillis).minusDays(1)).map { it.toDomain() }
        RulesPlanner.isTonightLocked(nowMillis, zone, settingsRepository.settings.first(), stored)
    }

    /** True once today's limits are frozen (today's row exists, it always started at midnight). */
    suspend fun isTodayLocked(nowMillis: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        dao.period(PeriodKind.DAY, today(nowMillis)) != null
    }

    /** When rules were first saved; recorded the first time snapshots are about to be written. */
    private fun rulesSince(nowMillis: Long): Long =
        prefs.rulesSinceMillis ?: nowMillis.also { prefs.rulesSinceMillis = it }

    private fun today(nowMillis: Long): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    private companion object {
        const val TAG = "CheatRepository"
    }
}
