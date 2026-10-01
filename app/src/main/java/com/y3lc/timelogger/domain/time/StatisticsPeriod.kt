package com.y3lc.timelogger.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class StatisticsPeriod private constructor(val interval: UtcInterval) {
    companion object {
        fun day(anchorDate: LocalDate, zoneId: ZoneId): StatisticsPeriod =
            fromDates(anchorDate, anchorDate.plusDays(1), zoneId)

        fun week(
            anchorDate: LocalDate,
            zoneId: ZoneId,
            firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        ): StatisticsPeriod {
            val startDate = anchorDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return fromDates(startDate, startDate.plusWeeks(1), zoneId)
        }

        fun month(anchorDate: LocalDate, zoneId: ZoneId): StatisticsPeriod {
            val startDate = anchorDate.withDayOfMonth(1)
            return fromDates(startDate, startDate.plusMonths(1), zoneId)
        }

        private fun fromDates(startDate: LocalDate, endDate: LocalDate, zoneId: ZoneId): StatisticsPeriod =
            StatisticsPeriod(
                UtcInterval(
                    startDate.atStartOfDay(zoneId).toInstant(),
                    endDate.atStartOfDay(zoneId).toInstant(),
                ),
            )
    }
}
