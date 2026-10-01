package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import java.time.Instant

sealed interface StopActivitySessionResult {
    data class Stopped(val session: ActivitySession) : StopActivitySessionResult

    data object NotActive : StopActivitySessionResult

    data object InvalidEndTime : StopActivitySessionResult
}

class StopActivitySessionUseCase(private val repository: ActivityRepository) {
    operator fun invoke(activityTypeId: String, nowUtc: Instant): StopActivitySessionResult =
        repository.inTransaction {
            val session = repository.getActiveSessionByTypeId(activityTypeId)
                ?: return@inTransaction StopActivitySessionResult.NotActive
            if (!nowUtc.isAfter(session.startedAtUtc)) {
                return@inTransaction StopActivitySessionResult.InvalidEndTime
            }
            check(repository.endSession(session.id, nowUtc)) { "Active session disappeared" }
            StopActivitySessionResult.Stopped(session.copy(endedAtUtc = nowUtc, updatedAtUtc = nowUtc))
        }
}
