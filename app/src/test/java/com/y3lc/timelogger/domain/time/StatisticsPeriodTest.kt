package com.y3lc.timelogger.domain.time

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsPeriodTest {
    @Test
    fun 日周期按指定时区的本地零点划分() {
        val period = StatisticsPeriod.day(
            LocalDate.of(2026, 10, 1),
            ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), period.interval.start)
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), period.interval.endExclusive)
    }

    @Test
    fun 周周期支持指定周起始日且按左闭右开边界计算() {
        val anchorDate = LocalDate.of(2026, 10, 1)
        val zoneId = ZoneId.of("UTC")

        val mondayPeriod = StatisticsPeriod.week(anchorDate, zoneId, DayOfWeek.MONDAY)
        val sundayPeriod = StatisticsPeriod.week(anchorDate, zoneId, DayOfWeek.SUNDAY)

        assertEquals(Instant.parse("2026-09-28T00:00:00Z"), mondayPeriod.interval.start)
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), mondayPeriod.interval.endExclusive)
        assertEquals(Instant.parse("2026-09-27T00:00:00Z"), sundayPeriod.interval.start)
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"), sundayPeriod.interval.endExclusive)
    }

    @Test
    fun 月周期包含闰年二月二十九日() {
        val period = StatisticsPeriod.month(
            LocalDate.of(2024, 2, 29),
            ZoneId.of("UTC"),
        )

        assertEquals(Instant.parse("2024-02-01T00:00:00Z"), period.interval.start)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), period.interval.endExclusive)
    }

    @Test
    fun 纽约春季夏令时切换日只有二十三小时() {
        val period = StatisticsPeriod.day(
            LocalDate.of(2026, 3, 8),
            ZoneId.of("America/New_York"),
        )

        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), period.interval.start)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), period.interval.endExclusive)
        assertEquals(Duration.ofHours(23), Duration.between(period.interval.start, period.interval.endExclusive))
    }

    @Test
    fun 纽约秋季夏令时切换日有二十五小时() {
        val period = StatisticsPeriod.day(
            LocalDate.of(2026, 11, 1),
            ZoneId.of("America/New_York"),
        )

        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), period.interval.start)
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), period.interval.endExclusive)
        assertEquals(Duration.ofHours(25), Duration.between(period.interval.start, period.interval.endExclusive))
    }
}
