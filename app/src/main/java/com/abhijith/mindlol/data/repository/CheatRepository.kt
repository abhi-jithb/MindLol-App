package com.abhijith.mindlol.data.repository

import android.util.Log
import com.abhijith.mindlol.data.db.RulesDao
import com.abhijith.mindlol.data.db.toDomain
import com.abhijith.mindlol.data.db.toEntity
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.domain.PeriodKind
import com.abhijith.mindlol.domain.RulesPlanner
import com.abhijith.mindlol.domain.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Connects the pure rules engine ([RulesPlanner]) to real data: settings from DataStore,
 * sessions from Android, snapshots and cheats in Room.
 */
class CheatRepository(
    private val dao: RulesDao,
    private val settingsRepository: SettingsRepository,
    private val reader: UsageEventsReader
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

            val result = RulesPlanner.plan(nowMillis, zone, settings, firstDate, stored, storedCheats, sessions)
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

    /** True while tonight's window is running. The override can't be changed then. */
    suspend fun isTonightLocked(nowMillis: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val stored = dao.periodsFrom(today(nowMillis).minusDays(1)).map { it.toDomain() }
        RulesPlanner.isTonightLocked(nowMillis, zone, settingsRepository.settings.first(), stored)
    }

    /** True once today's limits are frozen (today's row exists, it always started at midnight). */
    suspend fun isTodayLocked(nowMillis: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        dao.period(PeriodKind.DAY, today(nowMillis)) != null
    }

    private fun today(nowMillis: Long): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    private companion object {
        const val TAG = "CheatRepository"
    }
}
