package com.y3lc.timelogger.domain.model

import java.time.Instant

data class ActivitySession(
    val id: String,
    val activityTypeId: String,
    val startedAtUtc: Instant,
    val endedAtUtc: Instant?,
    val note: String?,
    val sourceZoneId: String?,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)
