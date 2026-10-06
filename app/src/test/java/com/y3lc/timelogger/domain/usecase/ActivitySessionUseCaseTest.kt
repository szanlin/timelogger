package com.y3lc.timelogger.domain.usecase

import com.y3lc.timelogger.data.repository.ActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySessionUseCaseTest {
    private val startedAtUtc = Instant.parse("2026-10-01T08:00:00Z")
    private val stoppedAtUtc = Instant.parse("2026-10-01T09:00:00Z")
    private val sourceZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun editChangesOnlyClosedSessionTimesAndPreservesAuditFields() {
        val original = activeSession("history", "work").copy(endedAtUtc = stoppedAtUtc, note = "备注")
        val repository = FakeActivityRepository().apply {
            sessions["history"] = original
            sessions["running"] = activeSession("running", "walk")
        }
        val result = EditActivitySessionUseCase(repository)("history", startedAtUtc.minusSeconds(60), stoppedAtUtc.plusSeconds(60), stoppedAtUtc.plusSeconds(120))

        assertTrue(result is EditActivitySessionResult.Updated)
        assertEquals(original.copy(startedAtUtc = startedAtUtc.minusSeconds(60), endedAtUtc = stoppedAtUtc.plusSeconds(60), updatedAtUtc = stoppedAtUtc.plusSeconds(120)), repository.sessions["history"])
        assertNull(repository.sessions["running"]?.endedAtUtc)
    }

    @Test
    fun editRejectsMissingRunningAndNonpositiveMillisecondIntervals() {
        val original = activeSession("history", "work").copy(endedAtUtc = stoppedAtUtc)
        val repository = FakeActivityRepository().apply {
            sessions["history"] = original
            sessions["running"] = activeSession("running", "walk")
        }
        val edit = EditActivitySessionUseCase(repository)
        assertEquals(EditActivitySessionResult.NotFound, edit("missing", startedAtUtc, stoppedAtUtc, stoppedAtUtc))
        assertEquals(EditActivitySessionResult.StillRunning, edit("running", startedAtUtc, stoppedAtUtc, stoppedAtUtc))
        for (end in listOf(startedAtUtc.minusSeconds(1), startedAtUtc, startedAtUtc.plusNanos(999_999))) {
            assertEquals(EditActivitySessionResult.InvalidEndTime, edit("history", startedAtUtc, end, stoppedAtUtc))
        }
        assertEquals(original, repository.sessions["history"])
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun startRejectsArchivedType() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work", isArchived = true)
        }

        val result = StartActivitySessionUseCase(repository) {
            "new-session"
        }("work", startedAtUtc, sourceZoneId)

        assertEquals(StartActivitySessionResult.TypeArchived, result)
        assertTrue(repository.sessions.isEmpty())
    }

    @Test
    fun startCreatesUtcSessionWithSourceZoneAndKeepsOtherTypeRunning() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            types["walk"] = activityType("walk")
            sessions["walking"] = activeSession("walking", "walk")
        }

        val result = StartActivitySessionUseCase(repository) {
            "working"
        }("work", startedAtUtc, sourceZoneId)

        assertTrue(result is StartActivitySessionResult.Started)
        assertEquals(startedAtUtc, repository.sessions["working"]?.startedAtUtc)
        assertEquals("Asia/Shanghai", repository.sessions["working"]?.sourceZoneId)
        assertNull(repository.sessions["walking"]?.endedAtUtc)
        assertEquals(
            StartActivitySessionResult.AlreadyActive,
            StartActivitySessionUseCase(repository) { "duplicate" }("work", stoppedAtUtc, sourceZoneId),
        )
        assertFalse(repository.sessions.containsKey("duplicate"))
    }

    @Test
    fun startStoresAndReturnsWholeUtcMilliseconds() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
        }

        val result = StartActivitySessionUseCase(repository) { "working" }(
            "work",
            startedAtUtc.plusNanos(123_456_789),
            sourceZoneId,
        )

        assertEquals(
            startedAtUtc.plusMillis(123),
            (result as StartActivitySessionResult.Started).session.startedAtUtc,
        )
        assertEquals(startedAtUtc.plusMillis(123), repository.sessions["working"]?.createdAtUtc)
    }

    @Test
    fun stopUsesSuppliedTimeAndLeavesOtherTypeRunning() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            types["walk"] = activityType("walk")
            sessions["working"] = activeSession("working", "work")
            sessions["walking"] = activeSession("walking", "walk")
        }

        val result = StopActivitySessionUseCase(repository)("work", stoppedAtUtc)

        assertTrue(result is StopActivitySessionResult.Stopped)
        assertEquals(stoppedAtUtc, repository.sessions["working"]?.endedAtUtc)
        assertEquals(stoppedAtUtc, repository.sessions["working"]?.updatedAtUtc)
        assertNull(repository.sessions["walking"]?.endedAtUtc)
    }

    @Test
    fun stopRejectsEndTimeAtStart() {
        val repository = FakeActivityRepository().apply {
            sessions["working"] = activeSession("working", "work")
        }

        val result = StopActivitySessionUseCase(repository)("work", startedAtUtc)

        assertEquals(StopActivitySessionResult.InvalidEndTime, result)
        assertNull(repository.sessions["working"]?.endedAtUtc)
    }

    @Test
    fun stopRejectsEndTimeWithinStartMillisecondWithoutWriting() {
        val repository = FakeActivityRepository().apply {
            sessions["working"] = activeSession("working", "work")
        }

        val result = StopActivitySessionUseCase(repository)("work", startedAtUtc.plusNanos(999_999))

        assertEquals(StopActivitySessionResult.InvalidEndTime, result)
        assertNull(repository.sessions["working"]?.endedAtUtc)
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun stopPersistsAndReturnsWholeUtcMilliseconds() {
        val repository = FakeActivityRepository().apply {
            sessions["working"] = activeSession("working", "work")
        }

        val result = StopActivitySessionUseCase(repository)("work", stoppedAtUtc.plusNanos(987_654))

        assertEquals(
            stoppedAtUtc,
            (result as StopActivitySessionResult.Stopped).session.endedAtUtc,
        )
        assertEquals(stoppedAtUtc, repository.sessions["working"]?.endedAtUtc)
    }

    @Test
    fun archiveEndsOnlyOwnActiveSessionBeforeMarkingTypeArchived() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            types["walk"] = activityType("walk")
            sessions["working"] = activeSession("working", "work")
            sessions["walking"] = activeSession("walking", "walk")
        }

        val result = ArchiveActivityTypeUseCase(repository)("work", stoppedAtUtc)

        assertEquals(ArchiveActivityTypeResult.Archived, result)
        assertEquals(stoppedAtUtc, repository.sessions["working"]?.endedAtUtc)
        assertEquals(stoppedAtUtc, repository.types["work"]?.updatedAtUtc)
        assertTrue(repository.types["work"]?.isArchived == true)
        assertNull(repository.sessions["walking"]?.endedAtUtc)
        assertFalse(repository.types["walk"]?.isArchived ?: true)
        assertTrue(repository.events.indexOf("end:working") < repository.events.indexOf("archive:work"))
        assertEquals(
            StartActivitySessionResult.TypeArchived,
            StartActivitySessionUseCase(repository) { "new-session" }("work", stoppedAtUtc, sourceZoneId),
        )
    }

    @Test
    fun archiveRollsBackEndWhenArchiveWriteFails() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            sessions["working"] = activeSession("working", "work")
            failArchive = true
        }

        try {
            ArchiveActivityTypeUseCase(repository)("work", stoppedAtUtc)
            throw AssertionError("Expected repository failure")
        } catch (_: IllegalStateException) {
            assertNull(repository.sessions["working"]?.endedAtUtc)
            assertFalse(repository.types["work"]?.isArchived ?: true)
        }
    }

    @Test
    fun archiveRejectsEndTimeWithinStartMillisecondWithoutWriting() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            sessions["working"] = activeSession("working", "work")
        }

        val result = ArchiveActivityTypeUseCase(repository)("work", startedAtUtc.plusNanos(999_999))

        assertEquals(ArchiveActivityTypeResult.InvalidEndTime, result)
        assertNull(repository.sessions["working"]?.endedAtUtc)
        assertFalse(repository.types["work"]?.isArchived ?: true)
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun archivePersistsWholeUtcMilliseconds() {
        val repository = FakeActivityRepository().apply {
            types["work"] = activityType("work")
            sessions["working"] = activeSession("working", "work")
        }

        val result = ArchiveActivityTypeUseCase(repository)("work", stoppedAtUtc.plusNanos(987_654))

        assertEquals(ArchiveActivityTypeResult.Archived, result)
        assertEquals(stoppedAtUtc, repository.sessions["working"]?.endedAtUtc)
        assertEquals(stoppedAtUtc, repository.types["work"]?.updatedAtUtc)
    }

    @Test
    fun deletesOnlyClosedActivitySessions() {
        val repository = FakeActivityRepository().apply {
            sessions["closed"] = activeSession("closed", "walking").copy(endedAtUtc = stoppedAtUtc)
            sessions["running"] = activeSession("running", "walking")
        }
        val useCase = DeleteActivitySessionUseCase(repository)

        assertEquals(DeleteActivitySessionResult.Deleted, useCase("closed"))
        assertFalse(repository.sessions.containsKey("closed"))
        assertEquals(DeleteActivitySessionResult.StillRunning, useCase("running"))
        assertTrue(repository.sessions.containsKey("running"))
        assertEquals(DeleteActivitySessionResult.NotFound, useCase("missing"))
    }

    private fun activityType(id: String, isArchived: Boolean = false): ActivityType = ActivityType(
        id = id,
        name = id,
        iconKey = "circle",
        colorArgb = 0xFF000000,
        isArchived = isArchived,
        sortOrder = 0,
        createdAtUtc = startedAtUtc,
        updatedAtUtc = startedAtUtc,
    )

    private fun activeSession(id: String, typeId: String): ActivitySession = ActivitySession(
        id = id,
        activityTypeId = typeId,
        startedAtUtc = startedAtUtc,
        endedAtUtc = null,
        note = null,
        sourceZoneId = sourceZoneId.id,
        createdAtUtc = startedAtUtc,
        updatedAtUtc = startedAtUtc,
    )

    private class FakeActivityRepository : ActivityRepository {
        val types = mutableMapOf<String, ActivityType>()
        val sessions = mutableMapOf<String, ActivitySession>()
        val events = mutableListOf<String>()
        var failArchive = false

        override fun getActivityTypeById(id: String): ActivityType? = types[id]

        override fun getSessionById(id: String): ActivitySession? = sessions[id]

        override fun updateClosedSession(id: String, startedAtUtc: Instant, endedAtUtc: Instant, updatedAtUtc: Instant): Boolean {
            val existing = sessions[id] ?: return false
            if (existing.endedAtUtc == null) return false
            sessions[id] = existing.copy(startedAtUtc = startedAtUtc, endedAtUtc = endedAtUtc, updatedAtUtc = updatedAtUtc)
            events += "edit:$id"
            return true
        }

        override fun deleteClosedSession(id: String): Boolean {
            val existing = sessions[id] ?: return false
            if (existing.endedAtUtc == null) return false
            sessions.remove(id)
            events += "delete:$id"
            return true
        }

        override fun getActiveSessionByTypeId(activityTypeId: String): ActivitySession? =
            sessions.values.firstOrNull { it.activityTypeId == activityTypeId && it.endedAtUtc == null }

        override fun insertSession(session: ActivitySession) {
            check(getActiveSessionByTypeId(session.activityTypeId) == null)
            sessions[session.id] = session
        }

        override fun endSession(id: String, endedAtUtc: Instant): Boolean {
            val session = sessions[id] ?: return false
            if (session.endedAtUtc != null) return false
            sessions[id] = session.copy(endedAtUtc = endedAtUtc, updatedAtUtc = endedAtUtc)
            events += "end:$id"
            return true
        }

        override fun archiveActivityType(id: String, updatedAtUtc: Instant): Boolean {
            if (failArchive) throw IllegalStateException("Archive write failed")
            val type = types[id] ?: return false
            types[id] = type.copy(isArchived = true, updatedAtUtc = updatedAtUtc)
            events += "archive:$id"
            return true
        }

        override fun <T> inTransaction(action: () -> T): T {
            val previousTypes = types.toMap()
            val previousSessions = sessions.toMap()
            val previousEvents = events.toList()
            return try {
                action()
            } catch (error: Exception) {
                types.clear()
                types.putAll(previousTypes)
                sessions.clear()
                sessions.putAll(previousSessions)
                events.clear()
                events.addAll(previousEvents)
                throw error
            }
        }
    }
}
