package com.y3lc.timelogger.domain.time

import com.y3lc.timelogger.domain.model.ActivitySession
import java.time.Duration
import java.time.Instant

data class PeriodStatistics(
    val coverageDuration: Duration,
    val durationByActivityTypeId: Map<String, Duration>,
)

object StatisticsCalculator {
    fun calculate(
        sessions: List<ActivitySession>,
        period: StatisticsPeriod,
        nowUtc: Instant,
    ): PeriodStatistics {
        val clippedIntervals = mutableListOf<UtcInterval>()
        val durationByActivityTypeId = mutableMapOf<String, Duration>()
        val periodInterval = period.interval

        for (session in sessions) {
            val sessionEndUtc = session.endedAtUtc ?: nowUtc
            val clippedStart = maxOf(session.startedAtUtc, periodInterval.start)
            val clippedEnd = minOf(sessionEndUtc, periodInterval.endExclusive)
            if (clippedEnd <= clippedStart) continue

            clippedIntervals.add(UtcInterval(clippedStart, clippedEnd))
            val duration = Duration.between(clippedStart, clippedEnd)
            val previous = durationByActivityTypeId[session.activityTypeId] ?: Duration.ZERO
            durationByActivityTypeId[session.activityTypeId] = previous.plus(duration)
        }

        val sortedIntervals = clippedIntervals.sortedBy(UtcInterval::start)
        if (sortedIntervals.isEmpty()) {
            return PeriodStatistics(Duration.ZERO, durationByActivityTypeId)
        }

        var coverageDuration = Duration.ZERO
        var mergedStart = sortedIntervals.first().start
        var mergedEnd = sortedIntervals.first().endExclusive

        for (interval in sortedIntervals.drop(1)) {
            if (interval.start <= mergedEnd) {
                mergedEnd = maxOf(mergedEnd, interval.endExclusive)
            } else {
                coverageDuration = coverageDuration.plus(Duration.between(mergedStart, mergedEnd))
                mergedStart = interval.start
                mergedEnd = interval.endExclusive
            }
        }
        coverageDuration = coverageDuration.plus(Duration.between(mergedStart, mergedEnd))

        return PeriodStatistics(coverageDuration, durationByActivityTypeId)
    }
}
