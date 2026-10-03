package com.y3lc.timelogger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface ActivityTypeDao {
    @Query("SELECT * FROM activity_types ORDER BY sortOrder, id")
    fun getAll(): List<ActivityTypeEntity>

    @Query("SELECT * FROM activity_types WHERE id = :id")
    fun getById(id: String): ActivityTypeEntity?

    @Insert
    fun insert(activityType: ActivityTypeEntity)

    @Query("UPDATE activity_types SET isArchived = 1, updatedAtUtc = :updatedAtUtc WHERE id = :id")
    fun archive(id: String, updatedAtUtc: Instant): Int

}
