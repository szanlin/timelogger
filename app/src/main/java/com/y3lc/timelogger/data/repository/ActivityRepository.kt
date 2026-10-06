package com.y3lc.timelogger.data.repository

import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.Instant

interface ActivityRepository {
    fun getActivityTypeById(id: String): ActivityType?

    fun getSessionById(id: String): ActivitySession?

    fun updateClosedSession(id: String, startedAtUtc: Instant, endedAtUtc: Instant, updatedAtUtc: Instant): Boolean

    fun deleteClosedSession(id: String): Boolean

    fun getActiveSessionByTypeId(activityTypeId: String): ActivitySession?

    fun insertSession(session: ActivitySession)

    fun endSession(id: String, endedAtUtc: Instant): Boolean

    fun archiveActivityType(id: String, updatedAtUtc: Instant): Boolean

    fun <T> inTransaction(action: () -> T): T
}
