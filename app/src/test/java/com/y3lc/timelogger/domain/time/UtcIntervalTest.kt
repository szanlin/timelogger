package com.y3lc.timelogger.domain.time

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UtcIntervalTest {
    private val interval = UtcInterval(
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-10-02T00:00:00Z"),
    )

    @Test
    fun 无交集时返回零时长() {
        val duration = interval.overlapDuration(
            Instant.parse("2026-09-30T20:00:00Z"),
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-03T00:00:00Z"),
        )

        assertEquals(Duration.ZERO, duration)
    }

    @Test
    fun 跨界记录只计算区间内时长() {
        val duration = interval.overlapDuration(
            Instant.parse("2026-09-30T23:00:00Z"),
            Instant.parse("2026-10-01T02:30:00Z"),
            Instant.parse("2026-10-03T00:00:00Z"),
        )

        assertEquals(Duration.ofMinutes(150), duration)
    }

    @Test
    fun 进行中记录在传入的查询时刻截止() {
        val duration = interval.overlapDuration(
            Instant.parse("2026-10-01T22:00:00Z"),
            null,
            Instant.parse("2026-10-01T23:15:00Z"),
        )

        assertEquals(Duration.ofMinutes(75), duration)
    }

    @Test
    fun 区间结束不晚于开始时拒绝创建() {
        val start = Instant.parse("2026-10-01T00:00:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            UtcInterval(start, start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UtcInterval(start, start.minusSeconds(1))
        }
    }
}
