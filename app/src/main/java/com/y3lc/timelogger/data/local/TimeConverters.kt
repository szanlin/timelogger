package com.y3lc.timelogger.data.local

import androidx.room.TypeConverter
import java.time.Instant

class TimeConverters {
    @TypeConverter
    fun convertInstantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun convertMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
