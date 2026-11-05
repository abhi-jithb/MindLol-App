package com.abhijith.mindlol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.PeriodKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Reads and writes the rules snapshots and cheats. Cheats are never deleted here. */
@Dao
interface RulesDao {

    @Query("SELECT * FROM judged_period WHERE date >= :from")
    suspend fun periodsFrom(from: LocalDate): List<JudgedPeriodEntity>

    /** Every snapshot; emits again whenever the table changes. */
    @Query("SELECT * FROM judged_period ORDER BY date")
    fun observePeriods(): Flow<List<JudgedPeriodEntity>>

    /** Every cheat, newest first; emits again whenever the table changes. */
    @Query("SELECT * FROM cheat ORDER BY startMillis DESC")
    fun observeCheats(): Flow<List<CheatEntity>>

    @Query("SELECT * FROM judged_period WHERE kind = :kind AND date = :date")
    suspend fun period(kind: PeriodKind, date: LocalDate): JudgedPeriodEntity?

    @Upsert
    suspend fun upsertPeriods(periods: List<JudgedPeriodEntity>)

    @Query("SELECT * FROM cheat WHERE periodDate >= :from")
    suspend fun cheatsFrom(from: LocalDate): List<CheatEntity>

    @Query(
        "SELECT id FROM cheat WHERE packageName = :packageName AND type = :type " +
            "AND periodDate = :periodDate AND firstSeenStartMillis = :firstSeenStartMillis"
    )
    suspend fun cheatId(packageName: String, type: CheatType, periodDate: LocalDate, firstSeenStartMillis: Long): Long?

    // IGNORE: if the unique key already exists, keep the saved row instead of failing.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheat(cheat: CheatEntity)

    @Update
    suspend fun updateCheat(cheat: CheatEntity)

    /** Inserts new cheats and updates existing ones (matched by their unique key), all or nothing. */
    @Transaction
    suspend fun saveCheats(cheats: List<CheatEntity>) {
        for (cheat in cheats) {
            val id = cheatId(cheat.packageName, cheat.type, cheat.periodDate, cheat.firstSeenStartMillis)
            if (id == null) insertCheat(cheat) else updateCheat(cheat.copy(id = id))
        }
    }

    /** Snapshots and cheats from one judging run, saved together. */
    @Transaction
    suspend fun saveJudgement(periods: List<JudgedPeriodEntity>, cheats: List<CheatEntity>) {
        upsertPeriods(periods)
        saveCheats(cheats)
    }
}
