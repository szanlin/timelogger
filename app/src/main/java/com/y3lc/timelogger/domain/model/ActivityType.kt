package com.y3lc.timelogger.domain.model

import java.time.Instant

data class ActivityType(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)
