package com.y3lc.timelogger.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TimeLoggerViewModelTest {
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
