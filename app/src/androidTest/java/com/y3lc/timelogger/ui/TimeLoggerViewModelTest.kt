package com.y3lc.timelogger.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TimeLoggerViewModelTest {
    @Test
    fun editReportsPendingLoadFailureInsteadOfIgnoringSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "history-pending-load-test.db"
        context.deleteDatabase(name)
        val database = TimeLoggerDatabase.open(context, name)
        val settings = FailingReadSettingsStore()
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel({ database }, settings)
            store.put("test", viewModel)
        }
        try {
            awaitState { !viewModel.uiState.value.isSaving }
            settings.failReads = true
            instrumentation.runOnMainSync { viewModel.refresh() }
            awaitState { !viewModel.uiState.value.isSaving }
            val callback = CountDownLatch(1)
            var editError: String? = null
            instrumentation.runOnMainSync {
                viewModel.saveSessionTimes(SessionTimeEdit("history", Instant.EPOCH, Instant.EPOCH.plusSeconds(1))) { error ->
                    editError = error
                    callback.countDown()
                }
            }
            assertTrue("保存必须返回可理解的失败提示", callback.await(1, TimeUnit.SECONDS))
            assertNotNull(editError)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
        }
    }

    @Test
    fun retryAfterCreateRefreshFailureDoesNotInsertAgain() {
        verifyRefreshRetry(
            operation = { it.createActivityType("阅读", "meeting", 0xFF112233) },
            verify = { viewModel, database ->
                assertEquals(1, database.activityTypeDao().getAll().count { it.name == "阅读" })
                assertEquals(1, viewModel.uiState.value.activityTypes.count { it.name == "阅读" })
            },
        )
    }

    @Test
    fun retryAfterMoveRefreshFailureDoesNotMoveTwice() {
        verifyRefreshRetry(
            operation = { it.moveActivityType("sleep", 1) },
            verify = { viewModel, _ ->
                assertEquals(listOf("walk", "sleep", "cycle", "meeting"), viewModel.uiState.value.activityTypes.map { it.id })
            },
        )
    }

    @Test
    fun retryAfterArchiveRefreshFailureDoesNotArchiveAgain() {
        verifyRefreshRetry(
            operation = { it.archiveActivityType("sleep") },
            verify = { viewModel, _ ->
                assertTrue(viewModel.uiState.value.managedActivityTypes.first { it.id == "sleep" }.isArchived)
                assertFalse(viewModel.uiState.value.activityTypes.any { it.id == "sleep" })
            },
        )
    }

    private fun verifyRefreshRetry(
        operation: (TimeLoggerViewModel) -> Unit,
        verify: (TimeLoggerViewModel, TimeLoggerDatabase) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "refresh-retry-view-model-test.db"
        context.deleteDatabase(name)
        val database = TimeLoggerDatabase.open(context, name)
        val settings = FailingReadSettingsStore()
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel({ database }, settings)
            store.put("test", viewModel)
        }
        try {
            awaitState { !viewModel.uiState.value.isSaving }
            settings.failReads = true
            instrumentation.runOnMainSync { operation(viewModel) }
            awaitState { !viewModel.uiState.value.isSaving }
            assertNotNull(viewModel.uiState.value.errorMessage)
            // 连续读取失败也不能恢复已经提交的写操作。
            instrumentation.runOnMainSync { viewModel.refresh() }
            awaitState { !viewModel.uiState.value.isSaving }
            assertNotNull(viewModel.uiState.value.errorMessage)
            settings.failReads = false
            instrumentation.runOnMainSync { viewModel.refresh() }
            awaitState { !viewModel.uiState.value.isSaving }
            assertNull(viewModel.uiState.value.errorMessage)
            verify(viewModel, database)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
        }
    }

    private class FailingReadSettingsStore : SettingsStore {
        @Volatile
        var failReads = false
        private val delegate = InMemorySettingsStore()

        override fun load(): AppSettings {
            check(!failReads) { "测试刷新读取失败" }
            return delegate.load()
        }

        override fun save(settings: AppSettings) = delegate.save(settings)
    }

    @Test
    fun settingsChangeStatisticsAndSurviveViewModelRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "settings-view-model-test.db"
        val preferenceName = "settings-view-model-test"
        context.deleteDatabase(name)
        context.getSharedPreferences(preferenceName, 0).edit().clear().commit()
        val database = TimeLoggerDatabase.open(context, name)
        val store = ViewModelStore()
        try {
            lateinit var viewModel: TimeLoggerViewModel
            instrumentation.runOnMainSync {
                viewModel = TimeLoggerViewModel({ database }, PreferencesSettingsStore(context, preferenceName))
                store.put("first", viewModel)
            }
            awaitState { !viewModel.uiState.value.isSaving }
            instrumentation.runOnMainSync {
                viewModel.saveStatisticsZone("America/New_York")
            }
            awaitState { viewModel.uiState.value.fixedStatisticsZoneId == "America/New_York" }
            instrumentation.runOnMainSync { viewModel.setWeekStart(DayOfWeek.SUNDAY) }
            awaitState { viewModel.uiState.value.firstDayOfWeek == DayOfWeek.SUNDAY }
            instrumentation.runOnMainSync { viewModel.selectStatisticsRange(StatisticsRange.WEEK) }
            assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.statistics.interval!!.start.atZone(ZoneId.of("America/New_York")).dayOfWeek)

            val reopened = TimeLoggerViewModel({ TimeLoggerDatabase.open(context, name) }, PreferencesSettingsStore(context, preferenceName))
            instrumentation.runOnMainSync { store.put("second", reopened) }
            awaitState { !reopened.uiState.value.isSaving }
            assertEquals("America/New_York", reopened.uiState.value.fixedStatisticsZoneId)
            assertEquals(DayOfWeek.SUNDAY, reopened.uiState.value.firstDayOfWeek)
            assertEquals(ZoneId.of("America/New_York"), reopened.uiState.value.statisticsZoneId)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
            context.getSharedPreferences(preferenceName, 0).edit().clear().commit()
        }
    }

    @Test
    fun typeCanBeCreatedEditedSortedAndArchivedWhileRunning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "type-management-view-model-test.db"
        context.deleteDatabase(name)
        val database = TimeLoggerDatabase.open(context, name)
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel { database }
            store.put("test", viewModel)
        }
        try {
            awaitState { viewModel.uiState.value.activityTypes.size == 4 }
            instrumentation.runOnMainSync { viewModel.createActivityType("阅读", "meeting", 0xFF112233) }
            awaitState { viewModel.uiState.value.activityTypes.size == 5 }
            val id = viewModel.uiState.value.activityTypes.last().id
            instrumentation.runOnMainSync { viewModel.updateActivityType(id, "学习", "walk", 0xFF445566) }
            awaitState { viewModel.uiState.value.activityTypes.last().name == "学习" }
            assertEquals("walk", viewModel.uiState.value.activityTypes.last().iconKey)
            assertEquals(0xFF445566, viewModel.uiState.value.activityTypes.last().colorArgb)
            instrumentation.runOnMainSync { viewModel.moveActivityType(id, -1) }
            awaitState { viewModel.uiState.value.activityTypes[3].id == id }
            instrumentation.runOnMainSync { viewModel.toggleActivity(id) }
            awaitState { viewModel.uiState.value.activityTypes.first { it.id == id }.isRunning }
            instrumentation.runOnMainSync { viewModel.archiveActivityType(id) }
            awaitState { viewModel.uiState.value.managedActivityTypes.first { it.id == id }.isArchived }
            assertFalse(viewModel.uiState.value.activityTypes.any { it.id == id })
            assertTrue(database.activitySessionDao().getAll().first { it.activityTypeId == id }.endedAtUtc != null)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
        }
    }

    @Test
    fun repeatedRefreshWhileLoadingDoesNotQueueAnotherDatabaseOperation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel {
                attempts.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                error("测试打开失败")
            }
            store.put("test", viewModel)
        }
        try {
            check(entered.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                viewModel.refresh()
                viewModel.refresh()
            }
            release.countDown()
            awaitState { !viewModel.uiState.value.isSaving }
            instrumentation.waitForIdleSync()
            Thread.sleep(200)
            assertEquals(1, attempts.get())
            assertNotNull(viewModel.uiState.value.errorMessage)
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test
    fun retryAfterWriteFailureReplaysTheRequestedStart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "write-retry-view-model-test.db"
        context.deleteDatabase(name)
        val database = TimeLoggerDatabase.open(context, name)
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel { database }
            store.put("test", viewModel)
        }
        try {
            awaitState { !viewModel.uiState.value.isSaving }
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_write BEFORE INSERT ON activity_sessions BEGIN SELECT RAISE(ABORT, '测试写入失败'); END")
            instrumentation.runOnMainSync { viewModel.toggleActivity("sleep") }
            awaitState { !viewModel.uiState.value.isSaving }
            assertNotNull(viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.activityTypes.first { it.id == "sleep" }.isRunning)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_test_write")
            instrumentation.runOnMainSync { viewModel.refresh() }
            awaitState { !viewModel.uiState.value.isSaving }
            assertEquals(true, viewModel.uiState.value.activityTypes.first { it.id == "sleep" }.isRunning)
            assertEquals(1, database.activitySessionDao().getActiveSessions().size)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
        }
    }

    @Test
    fun failedDatabaseInitializationCanBeRetriedWithoutLosingSelectedTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "retry-view-model-test.db"
        context.deleteDatabase(name)
        val store = ViewModelStore()
        lateinit var viewModel: TimeLoggerViewModel
        var firstAttempt = true
        instrumentation.runOnMainSync {
            viewModel = TimeLoggerViewModel {
                if (firstAttempt) {
                    firstAttempt = false
                    error("首次打开失败")
                }
                TimeLoggerDatabase.open(context, name)
            }
            store.put("test", viewModel)
            viewModel.selectTab(MainTab.STATISTICS)
        }
        try {
            awaitState { !viewModel.uiState.value.isLoading }
            assertNotNull(viewModel.uiState.value.errorMessage)
            instrumentation.runOnMainSync { viewModel.refresh() }
            awaitState { viewModel.uiState.value.activityTypes.size == 4 }
            assertEquals(null, viewModel.uiState.value.errorMessage)
            assertEquals(MainTab.STATISTICS, viewModel.uiState.value.selectedTab)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.deleteDatabase(name)
        }
    }

    private fun awaitState(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(20)
        check(predicate()) { "等待界面状态超时" }
    }
}
