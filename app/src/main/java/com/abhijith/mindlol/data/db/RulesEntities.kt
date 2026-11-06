package com.abhijith.mindlol.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abhijith.mindlol.data.settings.SettingsCodec
import com.abhijith.mindlol.domain.Cheat
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.PeriodKind
import com.abhijith.mindlol.domain.RulesSnapshot
import java.time.LocalDate

/**
 * The rules one night or day is judged by, frozen once it starts (added in database v2).
 * Lists are stored as text: [lockedApps] is "pkg1,pkg2", [limits] is "pkg1=30,pkg2=45".
 */
@Entity(tableName = "judged_period", primaryKeys = ["kind", "date"])
data class JudgedPeriodEntity(
    val kind: PeriodKind,
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    val overrideApplied: Boolean,
    val lockedApps: String,
    val limits: String,
    val finalized: Boolean
)

/**
 * One cheat (added in database v2). The unique index is the cheat's identity, so a re-sync can
 * never save the same cheat twice: LIMIT rows always have firstSeenStartMillis = 0, which makes
 * them one per app per day.
 */
@Entity(
    tableName = "cheat",
    indices = [
        Index(value = ["packageName", "type", "periodDate", "firstSeenStartMillis"], unique = true),
        Index("startMillis")
    ]
)
data class CheatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val type: CheatType,
    val periodDate: LocalDate,
    val firstSeenStartMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val minutes: Int
)

// ---------- Converting between Room rows and domain objects ----------

fun JudgedPeriodEntity.toDomain() = RulesSnapshot(
    kind = kind,
    date = date,
    startMillis = startMillis,
    endMillis = endMillis,
    overrideApplied = overrideApplied,
    lockedApps = SettingsCodec.splitList(lockedApps).toSet(),
    limits = SettingsCodec.decodeLimits(SettingsCodec.splitList(limits).toSet()),
    finalized = finalized
)

fun RulesSnapshot.toEntity() = JudgedPeriodEntity(
    kind = kind,
    date = date,
    startMillis = startMillis,
    endMillis = endMillis,
    overrideApplied = overrideApplied,
    lockedApps = SettingsCodec.joinList(lockedApps),
    limits = SettingsCodec.joinList(SettingsCodec.encodeLimits(limits)),
    finalized = finalized
)

fun CheatEntity.toDomain() = Cheat(
    packageName = packageName,
    type = type,
    periodDate = periodDate,
    startMillis = startMillis,
    endMillis = endMillis,
    minutes = minutes,
    firstSeenStartMillis = firstSeenStartMillis
)

fun Cheat.toEntity(id: Long = 0) = CheatEntity(
    id = id,
    packageName = packageName,
    type = type,
    periodDate = periodDate,
    firstSeenStartMillis = firstSeenStartMillis,
    startMillis = startMillis,
    endMillis = endMillis,
    minutes = minutes
)
