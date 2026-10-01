package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository
import java.time.Instant

sealed interface ArchiveActivityTypeResult {
    data object Archived : ArchiveActivityTypeResult

    data object TypeNotFound : ArchiveActivityTypeResult

    data object AlreadyArchived : ArchiveActivityTypeResult

    data object InvalidEndTime : ArchiveActivityTypeResult
}

class ArchiveActivityTypeUseCase(private val repository: ActivityRepository) {
    operator fun invoke(activityTypeId: String, nowUtc: Instant): ArchiveActivityTypeResult =
        repository.inTransaction {
            val activityType = repository.getActivityTypeById(activityTypeId)
                ?: return@inTransaction ArchiveActivityTypeResult.TypeNotFound
            if (activityType.isArchived) return@inTransaction ArchiveActivityTypeResult.AlreadyArchived

            val activeSession = repository.getActiveSessionByTypeId(activityTypeId)
            val nowUtcMillis = nowUtc.toEpochMilli()
            val persistedNowUtc = Instant.ofEpochMilli(nowUtcMillis)
            if (activeSession != null) {
                if (nowUtcMillis <= activeSession.startedAtUtc.toEpochMilli()) {
                    return@inTransaction ArchiveActivityTypeResult.InvalidEndTime
                }
                check(repository.endSession(activeSession.id, persistedNowUtc)) { "Active session disappeared" }
            }
            check(repository.archiveActivityType(activityTypeId, persistedNowUtc)) {
                "Activity type disappeared"
            }
            ArchiveActivityTypeResult.Archived
        }
}
