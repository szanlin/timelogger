package com.y3lc.timelogger.domain.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

sealed interface LocalWallTimeResult {
    data class Resolved(val instant: Instant) : LocalWallTimeResult
    data class Ambiguous(val offsets: List<ZoneOffset>) : LocalWallTimeResult
    data object Nonexistent : LocalWallTimeResult
    data object InvalidOffset : LocalWallTimeResult
    data object OutOfRange : LocalWallTimeResult
}

fun resolveLocalWallTime(local: LocalDateTime, zone: ZoneId, selectedOffset: ZoneOffset? = null): LocalWallTimeResult {
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.isEmpty()) return LocalWallTimeResult.Nonexistent
    if (selectedOffset != null && selectedOffset !in offsets) return LocalWallTimeResult.InvalidOffset
    if (offsets.size > 1 && selectedOffset == null) return LocalWallTimeResult.Ambiguous(offsets)
    val instant = local.toInstant(selectedOffset ?: offsets.single())
    return try {
        instant.toEpochMilli()
        LocalWallTimeResult.Resolved(instant)
    } catch (_: ArithmeticException) {
        LocalWallTimeResult.OutOfRange
    }
}
