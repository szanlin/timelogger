package com.y3lc.timelogger.domain.time

import com.y3lc.timelogger.domain.model.ActivitySession
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsCalculatorTest {
    private val period = StatisticsPeriod.day(LocalDate.of(2026, 10, 1), ZoneId.of("UTC"))

    @Test
    fun 跨日记录仅计入统计区间交集且结束边界不计入次日() {
        val sessions = listOf(
            session("a", "work", "2026-09-30T23:00:00Z", "2026-10-01T02:00:00Z"),
            session("b", "work", "2026-10-01T23:00:00Z", "2026-10-02T01:00:00Z"),
            session("c", "sleep", "2026-09-30T22:00:00Z", "2026-10-01T00:00:00Z"),
            session("d", "sleep", "2026-10-02T00:00:00Z", "2026-10-02T01:00:00Z"),
        )

        val statistics = StatisticsCalculator.calculate(sessions, period, Instant.parse("2026-10-03T00:00:00Z"))

        assertEquals(Duration.ofHours(3), statistics.coverageDuration)
        assertEquals(mapOf("work" to Duration.ofHours(3)), statistics.durationByActivityTypeId)
    }

    @Test
    fun 并行记录的覆盖时长取并集而类型时长逐条累计() {
        val sessions = listOf(
            session("a", "work", "2026-10-01T01:00:00Z", "2026-10-01T04:00:00Z"),
            session("b", "work", "2026-10-01T03:00:00Z", "2026-10-01T05:00:00Z"),
            session("c", "exercise", "2026-10-01T04:00:00Z", "2026-10-01T06:00:00Z"),
        )

        val statistics = StatisticsCalculator.calculate(sessions, period, Instant.parse("2026-10-02T00:00:00Z"))

        assertEquals(Duration.ofHours(5), statistics.coverageDuration)
        assertEquals(
            mapOf("work" to Duration.ofHours(5), "exercise" to Duration.ofHours(2)),
            statistics.durationByActivityTypeId,
        )
    }

    @Test
    fun 多条进行中记录共享传入的查询时刻() {
        val sessions = listOf(
            session("a", "work", "2026-10-01T09:00:00Z", null),
            session("b", "exercise", "2026-10-01T10:00:00Z", null),
        )

        val statistics = StatisticsCalculator.calculate(sessions, period, Instant.parse("2026-10-01T11:30:00Z"))

        assertEquals(Duration.ofMinutes(150), statistics.coverageDuration)
        assertEquals(
            mapOf("work" to Duration.ofMinutes(150), "exercise" to Duration.ofMinutes(90)),
            statistics.durationByActivityTypeId,
        )
    }

    @Test
    fun 并行类型累计可以超过自然日长度但覆盖时长不能超过() {
        val sessions = listOf(
            session("a", "work", "2026-09-30T23:00:00Z", "2026-10-02T01:00:00Z"),
            session("b", "sleep", "2026-09-30T23:00:00Z", "2026-10-02T01:00:00Z"),
        )

        val statistics = StatisticsCalculator.calculate(sessions, period, Instant.parse("2026-10-03T00:00:00Z"))

        assertEquals(Duration.ofHours(24), statistics.coverageDuration)
        assertEquals(
            mapOf("work" to Duration.ofHours(24), "sleep" to Duration.ofHours(24)),
            statistics.durationByActivityTypeId,
        )
    }

    @Test
    fun 跨周记录仅计入当前周() {
        val week = StatisticsPeriod.week(LocalDate.of(2026, 10, 1), ZoneId.of("UTC"), DayOfWeek.MONDAY)
        val sessions = listOf(
            session("a", "work", "2026-09-27T23:00:00Z", "2026-09-28T01:00:00Z"),
            session("b", "work", "2026-10-04T23:00:00Z", "2026-10-05T01:00:00Z"),
        )

        val statistics = StatisticsCalculator.calculate(sessions, week, Instant.parse("2026-10-06T00:00:00Z"))

        assertEquals(Duration.ofHours(2), statistics.coverageDuration)
        assertEquals(mapOf("work" to Duration.ofHours(2)), statistics.durationByActivityTypeId)
    }

    @Test
    fun 跨月记录仅计入当前月() {
        val month = StatisticsPeriod.month(LocalDate.of(2026, 10, 1), ZoneId.of("UTC"))
        val sessions = listOf(
            session("a", "work", "2026-09-30T23:00:00Z", "2026-10-01T01:00:00Z"),
            session("b", "work", "2026-10-31T23:00:00Z", "2026-11-01T01:00:00Z"),
        )

        val statistics = StatisticsCalculator.calculate(sessions, month, Instant.parse("2026-11-02T00:00:00Z"))

        assertEquals(Duration.ofHours(2), statistics.coverageDuration)
        assertEquals(mapOf("work" to Duration.ofHours(2)), statistics.durationByActivityTypeId)
    }

    private fun session(id: String, activityTypeId: String, startedAtUtc: String, endedAtUtc: String?): ActivitySession {
        val start = Instant.parse(startedAtUtc)
        return ActivitySession(
            id = id,
            activityTypeId = activityTypeId,
            startedAtUtc = start,
            endedAtUtc = endedAtUtc?.let(Instant::parse),
            note = null,
            sourceZoneId = null,
            createdAtUtc = start,
            updatedAtUtc = start,
        )
    }
}
