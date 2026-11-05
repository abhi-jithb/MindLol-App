package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.data.db.LockDao
import com.abhijith.mindlol.data.db.LockSessionEntity
import com.abhijith.mindlol.data.db.RulesDao
import com.abhijith.mindlol.data.db.toDomain
import com.abhijith.mindlol.data.db.toGrant
import com.abhijith.mindlol.data.usage.SystemApps
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.domain.LiveLockDecider
import com.abhijith.mindlol.domain.LockDecision
import com.abhijith.mindlol.domain.PeriodKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * What the live lock service needs from the data layer: the decision for the app in front,
 * and recording its own sessions (for LOCK_DISABLED). Rules come only from saved snapshots.
 */
class LiveLockRepository(
    private val rulesDao: RulesDao,
    private val lockDao: LockDao,
    private val reader: UsageEventsReader,
    private val systemApps: SystemApps
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // Settings and the dialer don't change while the service runs; worked out once.
    val neverLockable: Set<String> by lazy { systemApps.neverLockable() }
    val notApps: Set<String> by lazy { systemApps.notApps() }

    suspend fun decide(packageName: String, nowMillis: Long = System.currentTimeMillis()): LockDecision =
        withContext(Dispatchers.IO) {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val stored = rulesDao.periodsFrom(today.minusDays(1)).map { it.toDomain() }
            // The night that contains now, or else the next one (so Allow can say when bedtime starts).
            val nights = stored.filter { it.kind == PeriodKind.NIGHT && it.endMillis > nowMillis }
            val night = nights.firstOrNull { it.startMillis <= nowMillis } ?: nights.minByOrNull { it.startMillis }
            val day = stored.firstOrNull { it.kind == PeriodKind.DAY && it.date == today }

            // Today's usage is only read when this app has a limit today (it costs one event query).
            val usedTodayMs = if (day?.limits?.containsKey(packageName) == true) {
                reader.readSessions(day.startMillis, nowMillis)
                    .filter { it.packageName == packageName }
                    .sumOf { it.endMillis - it.startMillis }
            } else 0L

            // Grants last at most 10 minutes, so only recent ones can still be running.
            val grants = lockDao.grantsSince(nowMillis - MAX_GRANT_MS).mapNotNull { it.toGrant() }
            LiveLockDecider.decide(packageName, nowMillis, night, day, usedTodayMs, grants, neverLockable)
        }

    /** Saves a new session at connect time and returns its id. */
    suspend fun startSession(nowMillis: Long, bootAtMillis: Long): Long = withContext(Dispatchers.IO) {
        lockDao.insertSession(LockSessionEntity(connectedAtMillis = nowMillis, lastAliveMillis = nowMillis, disconnectedAtMillis = null, bootAtMillis = bootAtMillis))
    }

    /** Writes the heartbeat only while it matters (a night with locks, or a day with limits). Returns whether it wrote. */
    suspend fun heartbeatIfItMatters(sessionId: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        withContext(Dispatchers.IO) {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val stored = rulesDao.periodsFrom(today.minusDays(1)).map { it.toDomain() }
            val matters = LiveLockDecider.heartbeatMatters(nowMillis, stored)
            if (matters) lockDao.heartbeat(sessionId, nowMillis)
            matters
        }

    suspend fun endSession(sessionId: Long, nowMillis: Long) = withContext(Dispatchers.IO) {
        lockDao.markDisconnected(sessionId, nowMillis)
    }

    private companion object {
        const val MAX_GRANT_MS = 11 * 60_000L
    }
}
