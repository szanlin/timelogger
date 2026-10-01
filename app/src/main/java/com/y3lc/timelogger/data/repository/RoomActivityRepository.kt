package com.y3lc.timelogger.data.repository

import com.y3lc.timelogger.data.local.ActivitySessionEntity
import com.y3lc.timelogger.data.local.ActivityTypeEntity
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.Instant
import java.util.concurrent.Callable

class RoomActivityRepository(private val database: TimeLoggerDatabase) : ActivityRepository {
    private val activityTypeDao = database.activityTypeDao()
    private val activitySessionDao = database.activitySessionDao()

    override fun getActivityTypeById(id: String): ActivityType? =
        activityTypeDao.getById(id)?.toDomain()

    override fun getActiveSessionByTypeId(activityTypeId: String): ActivitySession? =
        activitySessionDao.getActiveSessionByTypeId(activityTypeId)?.toDomain()

    override fun insertSession(session: ActivitySession) {
        activitySessionDao.insert(session.toEntity())
    }

    override fun endSession(id: String, endedAtUtc: Instant): Boolean =
        activitySessionDao.endSession(id, endedAtUtc, endedAtUtc) == 1

    override fun archiveActivityType(id: String, updatedAtUtc: Instant): Boolean =
        activityTypeDao.archive(id, updatedAtUtc) == 1

    override fun <T> inTransaction(action: () -> T): T =
        database.runInTransaction(Callable { action() })
}

private fun ActivityTypeEntity.toDomain(): ActivityType = ActivityType(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtUtc = createdAtUtc,
    updatedAtUtc = updatedAtUtc,
)

private fun ActivitySessionEntity.toDomain(): ActivitySession = ActivitySession(
    id = id,
    activityTypeId = activityTypeId,
    startedAtUtc = startedAtUtc,
    endedAtUtc = endedAtUtc,
    note = note,
    sourceZoneId = sourceZoneId,
    createdAtUtc = createdAtUtc,
    updatedAtUtc = updatedAtUtc,
)

private fun ActivitySession.toEntity(): ActivitySessionEntity = ActivitySessionEntity(
    id = id,
    activityTypeId = activityTypeId,
    startedAtUtc = startedAtUtc,
    endedAtUtc = endedAtUtc,
    note = note,
    sourceZoneId = sourceZoneId,
    createdAtUtc = createdAtUtc,
    updatedAtUtc = updatedAtUtc,
)
