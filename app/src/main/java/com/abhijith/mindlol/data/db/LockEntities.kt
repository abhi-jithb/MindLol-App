package com.abhijith.mindlol.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abhijith.mindlol.domain.LockOutcome
import com.abhijith.mindlol.domain.LockReason
import com.abhijith.mindlol.domain.LockSession
import com.abhijith.mindlol.domain.UnlockGrant
import java.time.LocalDate

/**
 * Every time the lock screen was shown (added in database v3), including attempts you gave up on.
 * Rows with outcome UNLOCKED are the grants; [cardId] stops a question card repeating the same night.
 */
@Entity(
    tableName = "lock_event",
    indices = [Index("periodDate"), Index(value = ["packageName", "grantedAtMillis"])]
)
data class LockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: LockReason,
    /** The night (BEDTIME) or day (LIMIT) the lock belongs to. */
    val periodDate: LocalDate,
    val shownAtMillis: Long,
    /** Furthest step reached, 1 to 5. */
    val stepReached: Int,
    val outcome: LockOutcome,
    val countdownSeconds: Int,
    /** 2, 5 or 10 when UNLOCKED. */
    val grantedMinutes: Int?,
    val grantedAtMillis: Long?,
    val cardId: String?,
    val cardAnswer: String?
)

/**
 * One run of the live lock service (added in database v3). Gaps between runs, where the screen
 * was on, become LOCK_DISABLED cheats; see LiveLockDecider.lockOffIntervals.
 */
@Entity(tableName = "lock_session")
data class LockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectedAtMillis: Long,
    /** Heartbeat, only written while it matters (inside a sleep window, or on a day with limits). */
    val lastAliveMillis: Long,
    /** Null when the service died without warning (force-stop, kill, power loss). */
    val disconnectedAtMillis: Long?,
    /** When the phone last booted, worked out at connect time. */
    val bootAtMillis: Long
)

fun LockSessionEntity.toDomain() = LockSession(connectedAtMillis, lastAliveMillis, disconnectedAtMillis, bootAtMillis)

/** Only UNLOCKED rows with a grant become grants. */
fun LockEventEntity.toGrant(): UnlockGrant? {
    val at = grantedAtMillis ?: return null
    val minutes = grantedMinutes ?: return null
    return if (outcome == LockOutcome.UNLOCKED) UnlockGrant(packageName, reason, at, minutes) else null
}
