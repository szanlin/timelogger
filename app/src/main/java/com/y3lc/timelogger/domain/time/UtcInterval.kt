package com.y3lc.timelogger.domain.time

import java.time.Duration
import java.time.Instant

data class UtcInterval(
    val start: Instant,
    val endExclusive: Instant,
) {
    init {
        require(endExclusive > start) { "区间结束时刻必须晚于开始时刻" }
    }

    fun overlapDuration(
        startedAtUtc: Instant,
        endedAtUtc: Instant?,
        nowUtc: Instant,
    ): Duration {
        return clip(startedAtUtc, endedAtUtc, nowUtc)?.let {
            Duration.between(it.start, it.endExclusive)
        } ?: Duration.ZERO
    }

    fun clip(startedAtUtc: Instant, endedAtUtc: Instant?, nowUtc: Instant): UtcInterval? {
        val sessionEndUtc = endedAtUtc ?: nowUtc
        val overlapStart = maxOf(startedAtUtc, start)
        val overlapEnd = minOf(sessionEndUtc, endExclusive)
        return if (overlapEnd > overlapStart) {
            UtcInterval(overlapStart, overlapEnd)
        } else {
            null
        }
    }
}
