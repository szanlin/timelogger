package com.y3lc.timelogger.ui

import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.ZoneId

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
)

data class TimeLoggerUiState(
    val selectedTab: MainTab = MainTab.RECORD,
    val activityTypes: List<ActivityTypeItem> = emptyList(),
    val statisticsZoneId: ZoneId = ZoneId.systemDefault(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
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
