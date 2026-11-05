package com.abhijith.mindlol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
