package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.data.db.LockDao
import com.abhijith.mindlol.data.db.LockEventEntity
import com.abhijith.mindlol.data.db.LockSessionEntity
import com.abhijith.mindlol.data.db.RulesDao
import com.abhijith.mindlol.data.db.toDomain
import com.abhijith.mindlol.data.db.toGrant
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.data.usage.AppNames
import com.abhijith.mindlol.data.usage.SystemApps
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.Confession
import com.abhijith.mindlol.domain.LiveLockDecider
import com.abhijith.mindlol.domain.LockOutcome
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.domain.LockStep
import com.abhijith.mindlol.domain.RoastContext
import com.abhijith.mindlol.domain.RoastGenerator
import com.abhijith.mindlol.domain.SleepTime
import com.abhijith.mindlol.domain.LockDecision
import com.abhijith.mindlol.domain.PeriodKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * What the live lock service needs from the data layer: the decision for the app in front,
 * and recording its own sessions (for LOCK_DISABLED). Rules come only from saved snapshots.
 */
/** Everything the lock screen shows, worked out once when it opens. */
data class LockScreenInfo(
    val eventId: Long,
    val packageName: String,
    val appName: String,
    val reason: LockReason,
    val countdownSeconds: Int,
    val stareLine: String,
    val confessionLine: String,
    val requiredConfession: String
)

class LiveLockRepository(
    private val rulesDao: RulesDao,
    private val lockDao: LockDao,
    private val reader: UsageEventsReader,
    private val systemApps: SystemApps,
    private val appNames: AppNames,
    private val settingsRepository: SettingsRepository
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // Settings and the dialer don't change while the service runs; worked out once.
    val neverLockable: Set<String> by lazy { systemApps.neverLockable() }

    /**
     * The only windows the lock reacts to: apps with a launcher icon (the ones you can lock) and
     * the home screen (so going home and reopening an app is seen as a new open). Everything else
     * (keyboard, status bar, Vivo's gesture and system overlays) is ignored.
     * Read once per service start, so apps installed later are picked up after the next start.
     */
    fun trackedPackages(): Set<String> =
        appNames.launcherApps().map { it.packageName }.toSet() + reader.excludedPackages - systemApps.notApps()

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

    /**
     * Saves a new lock_event (step 1 reached, outcome LEFT until something else happens) and works
     * out what the lock screen shows: the app's name, step 1's countdown (escalation), and the lines.
     */
    suspend fun openLockScreen(
        packageName: String,
        reason: LockReason,
        periodDate: LocalDate,
        nowMillis: Long = System.currentTimeMillis()
    ): LockScreenInfo = withContext(Dispatchers.IO) {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val stored = rulesDao.periodsFrom(today.minusDays(1)).map { it.toDomain() }
        val grantsToday = lockDao.grantsSince(nowMillis - DAY_MS).mapNotNull { it.toGrant() }
        val countdown = LiveLockDecider.countdownSeconds(LiveLockDecider.unlocksSinceReset(grantsToday, nowMillis, stored))

        val eventId = lockDao.insertEvent(
            LockEventEntity(
                packageName = packageName, reason = reason, periodDate = periodDate, shownAtMillis = nowMillis,
                stepReached = LockStep.STARE.number, outcome = LockOutcome.LEFT, countdownSeconds = countdown,
                grantedMinutes = null, grantedAtMillis = null, cardId = null, cardAnswer = null
            )
        )

        val settings = settingsRepository.settings.first()
        val appName = appNames.labelFor(packageName)
        val night = stored.firstOrNull { it.kind == PeriodKind.NIGHT && it.date == periodDate }
        val day = stored.firstOrNull { it.kind == PeriodKind.DAY && it.date == today }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val cheatsThisWeek = rulesDao.cheatsFrom(monday).map { it.toDomain() }
        val context = RoastContext(
            appName = appName,
            type = if (reason == LockReason.BEDTIME) CheatType.BEDTIME else CheatType.LIMIT,
            startTime = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime(),
            minutes = 0,
            cheatsThisWeek = cheatsThisWeek.size,
            sameAppThisWeek = cheatsThisWeek.count { it.packageName == packageName },
            bedtime = night?.let { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalTime() } ?: SleepTime.toLocalTime(settings.bedtimeMinutes),
            wake = night?.let { Instant.ofEpochMilli(it.endMillis).atZone(zone).toLocalTime() } ?: SleepTime.toLocalTime(settings.wakeMinutes),
            limitMinutes = day?.limits?.get(packageName),
            intensity = settings.roastIntensity,
            seed = "lock-$eventId" // same lock screen, same lines
        )
        LockScreenInfo(
            eventId = eventId,
            packageName = packageName,
            appName = appName,
            reason = reason,
            countdownSeconds = countdown,
            stareLine = RoastGenerator.stepLine(LockStep.STARE, context).orEmpty(),
            confessionLine = RoastGenerator.stepLine(LockStep.CONFESSION, context).orEmpty(),
            requiredConfession = Confession.required(appName, reason)
        )
    }

    suspend fun reachStep(eventId: Long, step: LockStep) = withContext(Dispatchers.IO) { lockDao.reachStep(eventId, step.number) }

    suspend fun endLockScreen(eventId: Long, outcome: LockOutcome) = withContext(Dispatchers.IO) { lockDao.setOutcome(eventId, outcome) }

    private companion object {
        const val MAX_GRANT_MS = 11 * 60_000L
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
