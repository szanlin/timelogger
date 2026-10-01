package com.y3lc.timelogger.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivitySessionDaoTest {
    private val testDatabaseName = "activity-session-dao-test.db"
    private lateinit var context: Context
    private lateinit var database: TimeLoggerDatabase
    private lateinit var sessionDao: ActivitySessionDao
    private val start = Instant.parse("2026-10-01T10:00:00Z")

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDatabaseName)
        database = TimeLoggerDatabase.open(context, testDatabaseName)
        sessionDao = database.activitySessionDao()
        database.activityTypeDao().insert(activityType("walking"))
        database.activityTypeDao().insert(activityType("reading"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDatabaseName)
    }

    @Test
    fun insertingSecondOpenSessionForSameTypeFails() {
        sessionDao.insert(activitySession("first", "walking"))

        assertThrows(SQLiteConstraintException::class.java) {
            sessionDao.insert(activitySession("second", "walking"))
        }
        assertEquals(listOf("first"), sessionDao.getActiveSessions().map { it.id })
    }

    @Test
    fun differentTypesCanRunAtSameTimeAndAreRestoredFromStorage() {
        sessionDao.insert(activitySession("first", "walking"))
        sessionDao.insert(activitySession("second", "reading"))
        database.close()
        database = TimeLoggerDatabase.open(context, testDatabaseName)
        sessionDao = database.activitySessionDao()

        assertEquals(setOf("first", "second"), sessionDao.getActiveSessions().map { it.id }.toSet())
        assertEquals(start, sessionDao.getActiveSessionByTypeId("walking")?.startedAtUtc)
    }

    @Test
    fun endingAtOrBeforeStartFailsForInsertAndUpdate() {
        val beforeStart = start.minusMillis(1)
        assertThrows(SQLiteConstraintException::class.java) {
            sessionDao.insert(activitySession("invalid", "walking").copy(endedAtUtc = beforeStart))
        }
        sessionDao.insert(activitySession("valid", "walking"))

        for (invalidEnd in listOf(beforeStart, start)) {
            assertThrows(SQLiteConstraintException::class.java) {
                sessionDao.endSession("valid", invalidEnd, invalidEnd)
            }
        }
        assertEquals(null, sessionDao.getActiveSessionByTypeId("walking")?.endedAtUtc)
    }

    @Test
    fun endSessionPersistsUtcMillisAndReleasesType() {
        sessionDao.insert(activitySession("first", "walking"))
        val end = start.plusMillis(1234)

        assertEquals(1, sessionDao.endSession("first", end, end))
        assertEquals(null, sessionDao.getActiveSessionByTypeId("walking"))
        assertEquals(end, sessionDao.getById("first")?.endedAtUtc)
        sessionDao.insert(activitySession("second", "walking"))
        assertEquals("second", sessionDao.getActiveSessionByTypeId("walking")?.id)
    }

    @Test
    fun sessionMustReferenceExistingActivityType() {
        assertThrows(SQLiteConstraintException::class.java) {
            sessionDao.insert(activitySession("orphan", "unknown"))
        }
    }

    @Test
    fun archiveEndsActiveSessionInSameTransaction() {
        sessionDao.insert(activitySession("first", "walking"))
        val end = start.plusSeconds(1)

        assertEquals(1, database.activityTypeDao().archiveAndEndActive("walking", end, end))
        assertEquals(true, database.activityTypeDao().getById("walking")?.isArchived)
        assertEquals(end, sessionDao.getById("first")?.endedAtUtc)
    }

    @Test
    fun invalidArchiveEndRollsBackArchive() {
        sessionDao.insert(activitySession("first", "walking"))

        assertThrows(SQLiteConstraintException::class.java) {
            database.activityTypeDao().archiveAndEndActive("walking", start, start)
        }
        assertEquals(false, database.activityTypeDao().getById("walking")?.isArchived)
        assertEquals(null, sessionDao.getById("first")?.endedAtUtc)
    }

    private fun activityType(id: String) = ActivityTypeEntity(
        id = id,
        name = id,
        iconKey = "circle",
        colorArgb = 0xFF0000,
        isArchived = false,
        sortOrder = 0,
        createdAtUtc = start,
        updatedAtUtc = start,
    )

    private fun activitySession(id: String, activityTypeId: String) = ActivitySessionEntity(
        id = id,
        activityTypeId = activityTypeId,
        startedAtUtc = start,
        endedAtUtc = null,
        note = null,
        sourceZoneId = "Asia/Shanghai",
        createdAtUtc = start,
        updatedAtUtc = start,
    )
}
