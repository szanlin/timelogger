package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import java.time.Instant

sealed interface EditActivitySessionResult {
    data class Updated(val session: ActivitySession) : EditActivitySessionResult
    data object NotFound : EditActivitySessionResult
    data object StillRunning : EditActivitySessionResult
    data object InvalidEndTime : EditActivitySessionResult
}

class EditActivitySessionUseCase(private val repository: ActivityRepository) {
    operator fun invoke(id: String, startedAtUtc: Instant, endedAtUtc: Instant, nowUtc: Instant): EditActivitySessionResult =
        repository.inTransaction {
            val session = repository.getSessionById(id) ?: return@inTransaction EditActivitySessionResult.NotFound
            if (session.endedAtUtc == null) return@inTransaction EditActivitySessionResult.StillRunning
            val start = Instant.ofEpochMilli(startedAtUtc.toEpochMilli())
            val end = Instant.ofEpochMilli(endedAtUtc.toEpochMilli())
            if (end <= start) return@inTransaction EditActivitySessionResult.InvalidEndTime
            val updated = Instant.ofEpochMilli(nowUtc.toEpochMilli())
            check(repository.updateClosedSession(id, start, end, updated)) { "历史记录已不存在" }
            EditActivitySessionResult.Updated(session.copy(startedAtUtc = start, endedAtUtc = end, updatedAtUtc = updated))
        }
}
