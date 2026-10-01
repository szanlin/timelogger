package com.y3lc.timelogger.ui

import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TimeLoggerStateTest {
    private val now = Instant.parse("2026-10-02T00:00:00Z")

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
