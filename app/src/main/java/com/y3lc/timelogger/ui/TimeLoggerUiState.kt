package com.y3lc.timelogger.ui

import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.ZoneId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import com.y3lc.timelogger.domain.time.StatisticsCalculator
import com.y3lc.timelogger.domain.time.StatisticsPeriod
import com.y3lc.timelogger.domain.time.UtcInterval

enum class MainTab(val label: String) {
    RECORD("记录"),
    STATISTICS("统计"),
    SETTINGS("设置"),
}

data class ActivityTypeItem(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val isRunning: Boolean,
    val runningDuration: Duration = Duration.ZERO,
    val isArchived: Boolean = false,
)

enum class StatisticsRange(val label: String, val periodLabel: String) {
    DAY("日", "今天"), WEEK("周", "本周"), MONTH("月", "本月"),
}

data class TypeRanking(val typeId: String, val name: String, val colorArgb: Long, val duration: Duration)

data class TimelineItem(val sessionId: String, val name: String, val colorArgb: Long, val interval: UtcInterval, val isRunning: Boolean)

data class HistorySessionItem(val id: String, val name: String, val startedAtUtc: Instant, val endedAtUtc: Instant)

data class SessionTimeEdit(val id: String, val startedAtUtc: Instant, val endedAtUtc: Instant)

data class DailyBreakdown(
    val date: LocalDate,
    val coverageDuration: Duration,
    val durationByTypeId: Map<String, Duration>,
) {
    val totalDuration: Duration = durationByTypeId.values.fold(Duration.ZERO, Duration::plus)
}

data class PeriodSummary(
    val dateLabel: String = "",
    val interval: UtcInterval? = null,
    val coverageDuration: Duration = Duration.ZERO,
    val totalDuration: Duration = Duration.ZERO,
    val ranking: List<TypeRanking> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val dailyBreakdown: List<DailyBreakdown> = emptyList(),
)

data class TimeLoggerUiState(
    val selectedTab: MainTab = MainTab.RECORD,
    val activityTypes: List<ActivityTypeItem> = emptyList(),
    val statisticsZoneId: ZoneId = ZoneId.systemDefault(),
    val fixedStatisticsZoneId: String? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val managedActivityTypes: List<ActivityTypeItem> = emptyList(),
    val historySessions: List<HistorySessionItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val statisticsRange: StatisticsRange = StatisticsRange.DAY,
    val today: PeriodSummary = PeriodSummary(),
    val statistics: PeriodSummary = PeriodSummary(),
)

fun mapActivityTypes(types: List<ActivityType>, sessions: List<ActivitySession>): List<ActivityTypeItem> {
    val runningTypeIds = sessions.filter { it.endedAtUtc == null }.mapTo(mutableSetOf()) { it.activityTypeId }
    return types.asSequence()
        .filterNot { it.isArchived }
        .sortedWith(compareBy<ActivityType> { it.sortOrder }.thenBy { it.id })
        .map { type ->
            ActivityTypeItem(type.id, type.name, type.iconKey, type.colorArgb, type.id in runningTypeIds)
        }
        .toList()
}

fun mapManagedActivityTypes(types: List<ActivityType>, sessions: List<ActivitySession>): List<ActivityTypeItem> {
    val runningTypeIds = sessions.filter { it.endedAtUtc == null }.mapTo(mutableSetOf()) { it.activityTypeId }
    return types.sortedWith(compareBy<ActivityType> { it.isArchived }.thenBy { it.sortOrder }.thenBy { it.id })
        .map { type -> ActivityTypeItem(type.id, type.name, type.iconKey, type.colorArgb, type.id in runningTypeIds, isArchived = type.isArchived) }
}

fun buildPeriodSummary(
    types: List<ActivityType>,
    sessions: List<ActivitySession>,
    range: StatisticsRange,
    anchorDate: LocalDate,
    zoneId: ZoneId,
    nowUtc: Instant,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): PeriodSummary {
    val period = when (range) {
        StatisticsRange.DAY -> StatisticsPeriod.day(anchorDate, zoneId)
        StatisticsRange.WEEK -> StatisticsPeriod.week(anchorDate, zoneId, firstDayOfWeek)
        StatisticsRange.MONTH -> StatisticsPeriod.month(anchorDate, zoneId)
    }
    val statistics = StatisticsCalculator.calculate(sessions, period, nowUtc)
    val typesById = types.associateBy { it.id }
    val startDate = period.interval.start.atZone(zoneId).toLocalDate()
    val endDate = period.interval.endExclusive.atZone(zoneId).toLocalDate().minusDays(1)
    val ranking = statistics.durationByActivityTypeId.map { (id, duration) ->
        val type = typesById[id]
        TypeRanking(id, type?.name ?: "未知类型", type?.colorArgb ?: 0xFF777777, duration)
    }.sortedWith(compareByDescending<TypeRanking> { it.duration }.thenBy { it.typeId })
    val timeline = sessions.mapNotNull { session ->
        val clipped = period.interval.clip(session.startedAtUtc, session.endedAtUtc, nowUtc) ?: return@mapNotNull null
        val type = typesById[session.activityTypeId]
        TimelineItem(session.id, type?.name ?: "未知类型", type?.colorArgb ?: 0xFF777777, clipped, session.endedAtUtc == null)
    }.sortedWith(compareBy<TimelineItem> { it.interval.start }.thenBy { it.sessionId })
    val lastChartDate = if (range == StatisticsRange.MONTH) minOf(endDate, nowUtc.atZone(zoneId).toLocalDate()) else endDate
    val dailyBreakdown = if (range == StatisticsRange.DAY || ranking.isEmpty()) emptyList() else
        generateSequence(startDate) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(lastChartDate) }
            .map { date ->
                val daily = StatisticsCalculator.calculate(sessions, StatisticsPeriod.day(date, zoneId), nowUtc)
                DailyBreakdown(date, daily.coverageDuration, daily.durationByActivityTypeId)
            }.toList()
    return PeriodSummary(
        dateLabel = if (startDate == endDate) "$startDate" else "$startDate — $endDate",
        interval = period.interval,
        coverageDuration = statistics.coverageDuration,
        totalDuration = ranking.fold(Duration.ZERO) { total, item -> total.plus(item.duration) },
        ranking = ranking,
        timeline = timeline,
        dailyBreakdown = dailyBreakdown,
    )
}

fun formatDuration(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
}
