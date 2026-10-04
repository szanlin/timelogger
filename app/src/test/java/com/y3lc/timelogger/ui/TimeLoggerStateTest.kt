package com.y3lc.timelogger.ui

import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.Instant
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TimeLoggerStateTest {
    private val now = Instant.parse("2026-10-02T00:00:00Z")

    @Test
    fun clipsTodayTimelineAndSeparatesCoverageFromParallelTotals() {
        val sessions = listOf(
            activitySession("walk").copy(startedAtUtc = Instant.parse("2026-10-01T15:00:00Z"), endedAtUtc = Instant.parse("2026-10-01T18:00:00Z")),
            activitySession("sleep").copy(startedAtUtc = Instant.parse("2026-10-01T17:00:00Z"), endedAtUtc = Instant.parse("2026-10-01T19:00:00Z")),
        )
        val summary = buildPeriodSummary(listOf(activityType("walk", "走路", 0), activityType("sleep", "睡觉", 1)), sessions, StatisticsRange.DAY, LocalDate.parse("2026-10-02"), ZoneId.of("Asia/Shanghai"), now)

        assertEquals(Duration.ofHours(3), summary.coverageDuration)
        assertEquals(Duration.ofHours(4), summary.totalDuration)
        assertEquals("2026-10-02", summary.dateLabel)
        assertEquals(2, summary.timeline.size)
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), summary.timeline.first().interval.start)
        assertEquals(listOf("sleep", "walk"), summary.ranking.map { it.typeId })
    }

    @Test
    fun weekAndMonthShowFullPreciseRangesAndRetainArchivedHistory() {
        val archivedType = activityType("walk", "走路", 0, isArchived = true)
        val sessions = listOf(activitySession("walk"))
        val week = buildPeriodSummary(listOf(archivedType), sessions, StatisticsRange.WEEK, LocalDate.parse("2026-10-02"), ZoneId.of("Asia/Shanghai"), now)
        val month = buildPeriodSummary(listOf(archivedType), sessions, StatisticsRange.MONTH, LocalDate.parse("2026-10-02"), ZoneId.of("Asia/Shanghai"), now)

        assertEquals("2026-09-28 — 2026-10-04", week.dateLabel)
        assertEquals("2026-10-01 — 2026-10-31", month.dateLabel)
        assertEquals("走路", week.ranking.single().name)
        assertEquals(Duration.ofMinutes(1), week.totalDuration)
    }

    @Test
    fun emptyPeriodHasNoRankingOrTimeline() {
        val summary = buildPeriodSummary(emptyList(), emptyList(), StatisticsRange.DAY, LocalDate.parse("2026-10-02"), ZoneId.of("UTC"), now)
        assertEquals(emptyList<Any>(), summary.ranking)
        assertEquals(emptyList<Any>(), summary.timeline)
        assertEquals(Duration.ZERO, summary.totalDuration)
    }

    @Test
    fun filtersPeriodTimelineToTheSelectedActivityType() {
        val summary = buildPeriodSummary(
            listOf(activityType("walk", "走路", 0), activityType("sleep", "睡觉", 1)),
            listOf(
                activitySession("walk").copy(startedAtUtc = Instant.parse("2026-10-01T16:00:00Z"), endedAtUtc = Instant.parse("2026-10-01T17:00:00Z")),
                activitySession("sleep").copy(startedAtUtc = Instant.parse("2026-10-01T17:00:00Z"), endedAtUtc = Instant.parse("2026-10-01T18:00:00Z")),
            ),
            StatisticsRange.DAY,
            LocalDate.parse("2026-10-02"),
            ZoneId.of("Asia/Shanghai"),
            now,
        )

        val items = filterTimelineByActivityType(summary.timeline, "walk")

        assertEquals(1, items.size)
        assertEquals("session-walk", items.single().sessionId)
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), items.single().interval.start)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), items.single().interval.endExclusive)
    }

    @Test
    fun splitsTypeDetailIntoLocalCalendarDays() {
        val summary = buildPeriodSummary(
            listOf(activityType("walk", "走路", 0)),
            listOf(
                activitySession("walk").copy(
                    startedAtUtc = Instant.parse("2026-10-01T15:00:00Z"),
                    endedAtUtc = Instant.parse("2026-10-01T17:00:00Z"),
                ),
            ),
            StatisticsRange.WEEK,
            LocalDate.parse("2026-10-02"),
            ZoneId.of("Asia/Shanghai"),
            now,
        )

        val dailyDetails = buildActivityTypeDailyDetails(summary.timeline, "walk", ZoneId.of("Asia/Shanghai"))

        assertEquals(listOf(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02")), dailyDetails.map { it.date })
        assertEquals(listOf(Duration.ofHours(1), Duration.ofHours(1)), dailyDetails.map { it.totalDuration })
        assertEquals(Instant.parse("2026-10-01T15:00:00Z"), dailyDetails.first().sessions.single().interval.start)
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), dailyDetails.first().sessions.single().interval.endExclusive)
        assertEquals(Instant.parse("2026-10-01T16:00:00Z"), dailyDetails.last().sessions.single().interval.start)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), dailyDetails.last().sessions.single().interval.endExclusive)
    }

    @Test
    fun parallelTimelineSegmentKeepsItsExactWidth() {
        assertEquals(1f, calculateTimelineSegmentWidth(trackWidth = 10_000f, startFraction = 0.5f, endFraction = 0.5001f), 0f)
    }

    @Test
    fun weekChartStartsOnConfiguredSundayAndSplitsTypesAtLocalMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val sessions = listOf(
            activitySession("walk").copy(startedAtUtc = Instant.parse("2026-09-26T15:00:00Z"), endedAtUtc = Instant.parse("2026-09-26T17:00:00Z")),
            activitySession("sleep").copy(startedAtUtc = Instant.parse("2026-09-26T16:30:00Z"), endedAtUtc = Instant.parse("2026-09-26T17:30:00Z")),
            activitySession("walk").copy(id = "next-week", startedAtUtc = Instant.parse("2026-10-03T16:00:00Z"), endedAtUtc = Instant.parse("2026-10-03T17:00:00Z")),
        )

        val summary = buildPeriodSummary(
            listOf(activityType("walk", "走路", 0), activityType("sleep", "睡觉", 1)),
            sessions,
            StatisticsRange.WEEK,
            LocalDate.parse("2026-10-01"),
            zone,
            Instant.parse("2026-10-05T00:00:00Z"),
            DayOfWeek.SUNDAY,
        )

        assertEquals(7, summary.dailyBreakdown.size)
        assertEquals(LocalDate.parse("2026-09-27"), summary.dailyBreakdown.first().date)
        assertEquals(LocalDate.parse("2026-10-03"), summary.dailyBreakdown.last().date)
        assertEquals(Duration.ofMinutes(90), summary.dailyBreakdown.first().coverageDuration)
        assertEquals(mapOf("walk" to Duration.ofHours(1), "sleep" to Duration.ofHours(1)), summary.dailyBreakdown.first().durationByTypeId)
        assertEquals(Duration.ZERO, summary.dailyBreakdown[1].coverageDuration)
        assertEquals(Duration.ZERO, summary.dailyBreakdown.last().totalDuration)
    }

    @Test
    fun monthTrendIncludesEveryCalendarDayAndKeepsCoverageDistinctFromParallelTotal() {
        val sessions = listOf(
            activitySession("walk").copy(startedAtUtc = Instant.parse("2024-03-10T06:00:00Z"), endedAtUtc = Instant.parse("2024-03-10T08:00:00Z")),
            activitySession("sleep").copy(startedAtUtc = Instant.parse("2024-03-10T07:00:00Z"), endedAtUtc = Instant.parse("2024-03-10T09:00:00Z")),
        )

        val summary = buildPeriodSummary(
            listOf(activityType("walk", "走路", 0), activityType("sleep", "睡觉", 1)),
            sessions,
            StatisticsRange.MONTH,
            LocalDate.parse("2024-03-20"),
            ZoneId.of("America/New_York"),
            Instant.parse("2024-04-01T04:00:00Z"),
        )

        assertEquals(31, summary.dailyBreakdown.size)
        assertEquals(LocalDate.parse("2024-03-10"), summary.dailyBreakdown[9].date)
        assertEquals(Duration.ofHours(3), summary.dailyBreakdown[9].coverageDuration)
        assertEquals(Duration.ofHours(4), summary.dailyBreakdown[9].totalDuration)
        assertEquals(Duration.ZERO, summary.dailyBreakdown[10].coverageDuration)
    }

    @Test
    fun currentMonthTrendStopsAtTodayInsteadOfPlottingFutureZeros() {
        val sessions = listOf(
            activitySession("walk").copy(startedAtUtc = Instant.parse("2026-10-03T02:00:00Z"), endedAtUtc = Instant.parse("2026-10-03T03:00:00Z")),
        )
        val summary = buildPeriodSummary(
            listOf(activityType("walk", "走路", 0)),
            sessions,
            StatisticsRange.MONTH,
            LocalDate.parse("2026-10-03"),
            ZoneId.of("Asia/Shanghai"),
            Instant.parse("2026-10-03T04:00:00Z"),
        )

        assertEquals(3, summary.dailyBreakdown.size)
        assertEquals(LocalDate.parse("2026-10-03"), summary.dailyBreakdown.last().date)
    }

    @Test
    fun emptyWeekAndMonthHaveNoChartData() {
        for (range in listOf(StatisticsRange.WEEK, StatisticsRange.MONTH)) {
            val summary = buildPeriodSummary(emptyList(), emptyList(), range, LocalDate.parse("2026-10-02"), ZoneId.of("UTC"), now)
            assertEquals(emptyList<Any>(), summary.dailyBreakdown)
        }
    }

    @Test
    fun mapsVisibleTypesAndTheirRunningStateInSortOrder() {
        val types = listOf(
            activityType("walk", "走路", 2),
            activityType("sleep", "睡觉", 1),
            activityType("archived", "旧类型", 0, isArchived = true),
        )
        val activeSessions = listOf(activitySession("walk"))

        val items = mapActivityTypes(types, activeSessions)

        assertEquals(listOf("sleep", "walk"), items.map { it.id })
        assertEquals(listOf(false, true), items.map { it.isRunning })
        assertEquals(listOf("睡觉", "走路"), items.map { it.name })
    }

    @Test
    fun mapsStoppedSessionAsNotRunning() {
        val endedSession = activitySession("walk").copy(endedAtUtc = now)

        val items = mapActivityTypes(listOf(activityType("walk", "走路", 0)), listOf(endedSession))

        assertFalse(items.single().isRunning)
    }

    private fun activityType(id: String, name: String, sortOrder: Int, isArchived: Boolean = false) = ActivityType(
        id = id,
        name = name,
        iconKey = id,
        colorArgb = 0xFF000000,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAtUtc = now,
        updatedAtUtc = now,
    )

    private fun activitySession(activityTypeId: String) = ActivitySession(
        id = "session-$activityTypeId",
        activityTypeId = activityTypeId,
        startedAtUtc = now.minusSeconds(60),
        endedAtUtc = null,
        note = null,
        sourceZoneId = "Asia/Shanghai",
        createdAtUtc = now.minusSeconds(60),
        updatedAtUtc = now.minusSeconds(60),
    )
}
