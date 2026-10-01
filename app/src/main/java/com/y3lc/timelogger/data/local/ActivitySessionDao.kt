package com.y3lc.timelogger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface ActivitySessionDao {
    @Query("SELECT * FROM activity_sessions WHERE id = :id")
    fun getById(id: String): ActivitySessionEntity?

    @Query("SELECT * FROM activity_sessions WHERE endedAtUtc IS NULL ORDER BY startedAtUtc, id")
    fun getActiveSessions(): List<ActivitySessionEntity>

    @Query("SELECT * FROM activity_sessions WHERE activityTypeId = :activityTypeId AND endedAtUtc IS NULL")
    fun getActiveSessionByTypeId(activityTypeId: String): ActivitySessionEntity?

    @Insert
    fun insert(session: ActivitySessionEntity)

    @Query("UPDATE activity_sessions SET endedAtUtc = :endedAtUtc, updatedAtUtc = :updatedAtUtc WHERE id = :id AND endedAtUtc IS NULL")
    fun endSession(id: String, endedAtUtc: Instant, updatedAtUtc: Instant): Int
}
