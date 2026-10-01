package com.y3lc.timelogger.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "activity_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ActivityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["startedAtUtc"]),
        Index(value = ["endedAtUtc"]),
        Index(value = ["activityTypeId", "startedAtUtc"]),
    ],
)
data class ActivitySessionEntity(
    @PrimaryKey val id: String,
    val activityTypeId: String,
    val startedAtUtc: Instant,
    val endedAtUtc: Instant?,
    val note: String?,
    val sourceZoneId: String?,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)
