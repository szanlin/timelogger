package com.y3lc.timelogger.data

import com.y3lc.timelogger.data.local.ActivityTypeEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultActivityTypesTest {
    private val now = Instant.parse("2026-10-02T00:00:00Z")

    @Test
    fun firstLaunchSeedsFourNamedTypesInDisplayOrder() {
        val missing = missingDefaultActivityTypes(emptyList(), now)

        assertEquals(listOf("睡觉", "走路", "骑车", "开会"), missing.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), missing.map { it.sortOrder })
        assertTrue(missing.all { it.createdAtUtc == now && it.updatedAtUtc == now && !it.isArchived })
    }

    @Test
    fun secondLaunchDoesNotRestoreArchivedTypeOrDuplicateExistingTypes() {
        val existing = missingDefaultActivityTypes(emptyList(), now).map { type ->
            if (type.name == "走路") type.copy(isArchived = true) else type
        }

        val missing = missingDefaultActivityTypes(existing, now.plusSeconds(60))

        assertTrue(missing.isEmpty())
    }

    @Test
    fun partialSeedRestoresOnlyMissingDefaultType() {
        val existing = listOf(
            ActivityTypeEntity("sleep", "睡觉", "sleep", 0xFF000000, false, 0, now, now),
            ActivityTypeEntity("walk", "走路", "walk", 0xFF000000, false, 1, now, now),
            ActivityTypeEntity("cycle", "骑车", "cycle", 0xFF000000, false, 2, now, now),
        )

        val missing = missingDefaultActivityTypes(existing, now)

        assertEquals(listOf("开会"), missing.map { it.name })
    }
}
