package com.y3lc.timelogger.data

import com.y3lc.timelogger.data.local.ActivityTypeEntity
import java.time.Instant

private data class DefaultType(
    val id: String,
    val name: String,
    val colorArgb: Long,
)

private val defaultTypes = listOf(
    DefaultType("sleep", "睡觉", 0xFF5266A6),
    DefaultType("walk", "走路", 0xFF4A8D69),
    DefaultType("cycle", "骑车", 0xFFB27142),
    DefaultType("meeting", "开会", 0xFF855C91),
)

fun missingDefaultActivityTypes(existing: List<ActivityTypeEntity>, now: Instant): List<ActivityTypeEntity> {
    val existingIds = existing.mapTo(mutableSetOf()) { it.id }
    return defaultTypes.mapIndexedNotNull { index, type ->
        if (type.id in existingIds) return@mapIndexedNotNull null
        ActivityTypeEntity(
            id = type.id,
            name = type.name,
            iconKey = type.id,
            colorArgb = type.colorArgb,
            isArchived = false,
            sortOrder = index,
            createdAtUtc = now,
            updatedAtUtc = now,
        )
    }
}
