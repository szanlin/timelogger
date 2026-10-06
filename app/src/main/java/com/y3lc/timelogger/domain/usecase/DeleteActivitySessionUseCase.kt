package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository

sealed interface DeleteActivitySessionResult {
    data object Deleted : DeleteActivitySessionResult
    data object NotFound : DeleteActivitySessionResult
    data object StillRunning : DeleteActivitySessionResult
}

class DeleteActivitySessionUseCase(private val repository: ActivityRepository) {
    operator fun invoke(id: String): DeleteActivitySessionResult {
        val session = repository.getSessionById(id) ?: return DeleteActivitySessionResult.NotFound
        if (session.endedAtUtc == null) return DeleteActivitySessionResult.StillRunning
        return if (repository.deleteClosedSession(id)) DeleteActivitySessionResult.Deleted else DeleteActivitySessionResult.NotFound
    }
}
