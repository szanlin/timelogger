package com.y3lc.timelogger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "activity_types")
data class ActivityTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)
