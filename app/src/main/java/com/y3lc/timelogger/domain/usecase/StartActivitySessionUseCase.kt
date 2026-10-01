package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

sealed interface StartActivitySessionResult {
    data class Started(val session: ActivitySession) : StartActivitySessionResult

    data object TypeNotFound : StartActivitySessionResult

    data object TypeArchived : StartActivitySessionResult

    data object AlreadyActive : StartActivitySessionResult
}

class StartActivitySessionUseCase(
    private val repository: ActivityRepository,
    private val createSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    operator fun invoke(
        activityTypeId: String,
        nowUtc: Instant,
        sourceZoneId: ZoneId,
    ): StartActivitySessionResult = repository.inTransaction {
        val activityType = repository.getActivityTypeById(activityTypeId)
            ?: return@inTransaction StartActivitySessionResult.TypeNotFound
        if (activityType.isArchived) return@inTransaction StartActivitySessionResult.TypeArchived
        if (repository.getActiveSessionByTypeId(activityTypeId) != null) {
            return@inTransaction StartActivitySessionResult.AlreadyActive
        }

        val session = ActivitySession(
            id = createSessionId(),
            activityTypeId = activityTypeId,
            startedAtUtc = nowUtc,
            endedAtUtc = null,
            note = null,
            sourceZoneId = sourceZoneId.id,
            createdAtUtc = nowUtc,
            updatedAtUtc = nowUtc,
        )
        repository.insertSession(session)
        StartActivitySessionResult.Started(session)
    }
}
