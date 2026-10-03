package com.y3lc.timelogger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.ui.TimeLoggerViewModel
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.time.DayOfWeek
import java.time.Instant

class MainActivityTest {
    @get:Rule(order = 0)
    val isolatedStorage = object : ExternalResource() {
        private val application
            get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IsolatedTestApplication

        override fun before() = application.beginIsolatedTest()

        override fun after() = application.deleteTestStorage()
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var viewModel: TimeLoggerViewModel

    @Before
    fun awaitInitialLoad() {
        composeTestRule.runOnUiThread {
            viewModel = ViewModelProvider(composeTestRule.activity)[TimeLoggerViewModel::class.java]
        }
        awaitStableState()
        assertEquals(4, viewModel.uiState.value.activityTypes.size)
        assertFalse(viewModel.uiState.value.activityTypes.any { it.isRunning })
        assertNull(viewModel.uiState.value.fixedStatisticsZoneId)
        assertEquals(DayOfWeek.MONDAY, viewModel.uiState.value.firstDayOfWeek)
    }

    @After
    fun awaitPendingOperations() = awaitStableState()

    private fun awaitStableState() {
        composeTestRule.waitUntil(5_000) {
            !viewModel.uiState.value.isLoading && !viewModel.uiState.value.isSaving
        }
        composeTestRule.waitForIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun settingsCanChooseFixedZoneAndSunday() {
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("settings-zone-fixed").performClick()
        composeTestRule.onNodeWithTag("settings-zone-input").performTextReplacement("America/New_York")
        composeTestRule.onNodeWithTag("settings-zone-save").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("settings-zone-current").assertTextEquals("America/New_York")
        composeTestRule.onNodeWithTag("settings-week-sunday").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag("settings-week-sunday").assertIsSelected() }.isSuccess
        }
    }

    @Test
    fun settingsCanCreateEditAndArchiveType() {
        val name = "阅读${System.nanoTime()}"
        val edited = "学习${System.nanoTime()}"
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("type-add").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextInput(name)
        composeTestRule.onNodeWithTag("type-save").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-edit-$name"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-edit-$name").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextReplacement(edited)
        composeTestRule.onNodeWithTag("type-save").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-move-up-$edited"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-move-up-$edited").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("type-archive-$edited").performClick()
        composeTestRule.onNodeWithTag("type-confirm-archive").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-archived-$edited"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-archived-$edited").assertExists()
    }

    @Test
    fun typeColorSelectionExposesSelectedButtonSemantics() {
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("type-add").performClick()
        val selectedColor = 0xFF4A8D69
        val colorNode = composeTestRule.onNodeWithTag("type-color-$selectedColor")
        colorNode.performClick()
        colorNode.assertIsSelected()
        colorNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun differentActivityTypesCanRunTogetherAndStopIndependently() {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("activity-sleep")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("睡觉", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
    }

    @Test
    fun statisticsCanSwitchBetweenDayWeekAndMonth() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("period-day")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("period-week").performClick()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals("本周")
        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals("本月")
        composeTestRule.onNodeWithTag("period-day").performClick()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals("今天")
    }

    @Test
    fun recordedTimeAppearsInWeekAndMonthCharts() {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("activity-walk")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("进行中", substring = true) }.isSuccess
        }
        Thread.sleep(100)
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("走路", substring = true) }.isSuccess
        }
        composeTestRule.onNodeWithTag("nav-statistics").performClick()

        composeTestRule.onNodeWithTag("period-week").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("statistics-week-chart"))
        composeTestRule.onNodeWithTag("statistics-week-chart").assertExists()

        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("statistics-month-chart"))
        composeTestRule.onNodeWithTag("statistics-month-chart").assertExists()
    }

    @Test
    fun bottomNavigationItemsExposeDestinationDescriptions() {
        composeTestRule.onNodeWithTag("nav-record").assertContentDescriptionContains("记录")
        composeTestRule.onNodeWithTag("nav-statistics").assertContentDescriptionContains("统计")
        composeTestRule.onNodeWithTag("nav-settings").assertContentDescriptionContains("设置")
    }

    @Test
    fun bottomNavigationSwitchesBetweenThreeScreens() {
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("记录")

        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("统计")

        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("设置")

        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertIsDisplayed().assertTextEquals("记录")
    }

    @Test
    fun historyEditorRejectsMissingDstTimeAndSavesExplicitOverlapOffsets() {
        composeTestRule.runOnUiThread { viewModel.saveStatisticsZone("America/New_York") }
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("record-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("history-open"))
        composeTestRule.onNodeWithTag("history-open").performClick()
        composeTestRule.onNodeWithTag("history-edit-0").performClick()
        composeTestRule.onNodeWithTag("session-start-input").performTextReplacement("2026-03-08 02:30:00")
        composeTestRule.onNodeWithTag("session-start-error").assertTextEquals("此时刻因夏令时跳变不存在，请重新选择")
        composeTestRule.onNodeWithTag("session-save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("session-start-input").performTextReplacement("2026-11-01 01:30:00")
        composeTestRule.onNodeWithTag("session-end-input").performTextReplacement("2026-11-01 01:15:00")
        composeTestRule.onNodeWithTag("session-save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("session-start-offset--04:00").performClick()
        composeTestRule.onNodeWithTag("session-end-offset--05:00").performClick()
        composeTestRule.onNodeWithTag("session-save").performClick()
        awaitStableState()
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), viewModel.uiState.value.historySessions.single().startedAtUtc)
        assertEquals(Instant.parse("2026-11-01T06:15:00Z"), viewModel.uiState.value.historySessions.single().endedAtUtc)
        composeTestRule.onNodeWithTag("history-edit-0").performClick()
        composeTestRule.onNodeWithTag("session-start-input").assertTextContains("2026-11-01 01:30:00.000")
        composeTestRule.onNodeWithTag("session-end-input").assertTextContains("2026-11-01 01:15:00.000")
        composeTestRule.onNodeWithTag("session-start-offset--04:00").assertIsSelected()
        composeTestRule.onNodeWithTag("session-end-offset--05:00").assertIsSelected()
    }

    @Test
    fun historyEditorRejectsEndAtStartAndKeepsDraft() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("record-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("history-open"))
        composeTestRule.onNodeWithTag("history-open").performClick()
        composeTestRule.onNodeWithTag("history-edit-0").performClick()
        composeTestRule.onNodeWithTag("session-start-input").performTextReplacement("2026-01-01 10:00:00")
        composeTestRule.onNodeWithTag("session-end-input").performTextReplacement("2026-01-01 10:00:00")
        composeTestRule.onNodeWithTag("session-range-error").assertTextEquals("结束时间必须晚于开始时间")
        composeTestRule.onNodeWithTag("session-save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("session-start-input").assertTextContains("2026-01-01 10:00:00")
    }

    @Test
    fun historyEditorRetainsDraftAfterDatabaseFailureAndCanRetry() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        val original = viewModel.uiState.value.historySessions.single()
        val database = TimeLoggerDatabase.open(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_history_edit BEFORE UPDATE ON activity_sessions BEGIN SELECT RAISE(ABORT, '测试编辑失败'); END")
            composeTestRule.onNodeWithTag("record-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("history-open"))
            composeTestRule.onNodeWithTag("history-open").performClick()
            composeTestRule.onNodeWithTag("history-edit-0").performClick()
            composeTestRule.onNodeWithTag("session-start-input").performTextReplacement("2026-01-01 10:00:00")
            composeTestRule.onNodeWithTag("session-end-input").performTextReplacement("2026-01-01 11:00:00")
            composeTestRule.onNodeWithTag("session-save").performClick()
            awaitStableState()
            composeTestRule.onNodeWithTag("session-save-error").assertExists()
            composeTestRule.onNodeWithTag("session-start-input").assertTextContains("2026-01-01 10:00:00")
            assertEquals(original, viewModel.uiState.value.historySessions.single())
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history_edit")
            composeTestRule.onNodeWithTag("session-save").performClick()
            awaitStableState()
            composeTestRule.onNodeWithTag("history-edit-0").assertExists()
            assertEquals(3600L, java.time.Duration.between(viewModel.uiState.value.historySessions.single().startedAtUtc, viewModel.uiState.value.historySessions.single().endedAtUtc).seconds)
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_history_edit")
            database.close()
        }
    }
}
