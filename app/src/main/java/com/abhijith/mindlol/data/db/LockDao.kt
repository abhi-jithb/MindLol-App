package com.abhijith.mindlol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.abhijith.mindlol.domain.LockOutcome
import com.abhijith.mindlol.domain.LockReason
import java.time.LocalDate

/** Lock screen attempts and service sessions (database v3). Nothing here is ever deleted. */
@Dao
interface LockDao {

    // ---------- lock_event ----------

    /** Returns the new row's id, so the lock screen can update the same row as you go through the steps. */
    @Insert
    suspend fun insertEvent(event: LockEventEntity): Long

    @Update
    suspend fun updateEvent(event: LockEventEntity)

    /** Moves the furthest step forward, never back. */
    @Query("UPDATE lock_event SET stepReached = MAX(stepReached, :step) WHERE id = :id")
    suspend fun reachStep(id: Long, step: Int)

    @Query("UPDATE lock_event SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: LockOutcome)

    /** Step 3 showed this card. Saved when shown, so leaving before answering still counts. */
    @Query("UPDATE lock_event SET cardId = :cardId WHERE id = :id")
    suspend fun setCard(id: Long, cardId: String)

    @Query("UPDATE lock_event SET cardAnswer = :answer WHERE id = :id")
    suspend fun setCardAnswer(id: Long, answer: String)

    /** Card ids already shown for one night (BEDTIME) or day (LIMIT), oldest first, repeats kept. */
    @Query(
        "SELECT cardId FROM lock_event WHERE periodDate = :periodDate AND reason = :reason " +
            "AND cardId IS NOT NULL ORDER BY shownAtMillis"
    )
    suspend fun cardsShown(periodDate: LocalDate, reason: LockReason): List<String>

    @Query("SELECT * FROM lock_event WHERE periodDate >= :from ORDER BY shownAtMillis")
    suspend fun eventsFrom(from: LocalDate): List<LockEventEntity>

    /** Unlocks granted since [sinceMillis]: for active grants and the escalation count. */
    @Query("SELECT * FROM lock_event WHERE outcome = 'UNLOCKED' AND grantedAtMillis >= :sinceMillis ORDER BY grantedAtMillis")
    suspend fun grantsSince(sinceMillis: Long): List<LockEventEntity>

    // ---------- lock_session ----------

    @Insert
    suspend fun insertSession(session: LockSessionEntity): Long

    @Query("UPDATE lock_session SET lastAliveMillis = :at WHERE id = :id")
    suspend fun heartbeat(id: Long, at: Long)

    @Query("UPDATE lock_session SET disconnectedAtMillis = :at, lastAliveMillis = :at WHERE id = :id")
    suspend fun markDisconnected(id: Long, at: Long)

    /**
     * Sessions that could border a gap after [sinceMillis]: every session that was still alive
     * then, plus the last one before it (whose end starts the first gap).
     */
    @Query(
        "SELECT * FROM lock_session WHERE COALESCE(disconnectedAtMillis, lastAliveMillis) >= :sinceMillis " +
            "OR id = (SELECT id FROM lock_session WHERE connectedAtMillis < :sinceMillis ORDER BY connectedAtMillis DESC LIMIT 1) " +
            "ORDER BY connectedAtMillis"
    )
    suspend fun sessionsAround(sinceMillis: Long): List<LockSessionEntity>
}
