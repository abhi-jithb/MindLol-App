package com.abhijith.mindlol.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * SQLite has no date type. Dates are stored as "2026-11-05" text, which also sorts
 * correctly, so `BETWEEN` and `ORDER BY` work on them.
 */
class Converters {
    @TypeConverter
    fun dateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}
