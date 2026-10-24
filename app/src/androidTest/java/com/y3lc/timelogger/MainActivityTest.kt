package com.y3lc.timelogger

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.ui.TimeLoggerViewModel
import com.y3lc.timelogger.ui.ActivityTypeItem
import com.y3lc.timelogger.ui.SettingsScreen
import com.y3lc.timelogger.ui.TimeLoggerUiState
import com.y3lc.timelogger.ui.RecordScreen
import com.y3lc.timelogger.ui.StatisticsMonthHistory
import com.y3lc.timelogger.ui.HistorySessionItem
import com.y3lc.timelogger.ui.PeriodSummary
import com.y3lc.timelogger.ui.StatisticsRange
import com.y3lc.timelogger.ui.StatisticsScreen
import com.y3lc.timelogger.domain.time.StatisticsPeriod
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
        awaitNode("record-list")
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

    private fun awaitNode(tag: String) {
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun openMonthHistory() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))
    }

    private fun openRecordedDay() {
        openMonthHistory()
        val state = viewModel.uiState.value
        val date = state.historySessions.single().startedAtUtc.atZone(state.statisticsZoneId).toLocalDate()
        composeTestRule.onNodeWithTag("history-date-$date").performClick()
    }

    private fun showMonthHistory(state: TimeLoggerUiState) {
        composeTestRule.activity.setContent {
            var anchorDate by remember { mutableStateOf(state.statisticsAnchorDate ?: LocalDate.now(state.statisticsZoneId)) }
            StatisticsMonthHistory(state.copy(statisticsAnchorDate = anchorDate), { anchorDate = it })
        }
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
        colorNode.performScrollTo().performClick()
        colorNode.assertIsSelected()
        colorNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        colorNode.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun typeIconSelectionExposesSelectedButtonSemantics() {
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("type-add").performClick()
        val iconNode = composeTestRule.onNodeWithTag("type-icon-sleep")
        iconNode.performClick().assertIsSelected()
        iconNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        iconNode.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun typeEditorCanSelectAndSaveMaterialSurfIcon() {
        val name = "冲浪${System.nanoTime()}"
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("type-add").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextInput(name)
        composeTestRule.onNodeWithTag("type-icon-surf").performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("type-save").performClick()
        awaitStableState()

        assertTrue(viewModel.uiState.value.managedActivityTypes.any { it.name == name && it.iconKey == "surf" })
    }

    @Test
    fun typeEditorKeepsDraftAfterRealInsertFailureAndCanRetry() {
        val name = "阅读${System.nanoTime()}"
        val selectedColor = 0xFF4A8D69
        val database = TimeLoggerDatabase.open(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_type_insert BEFORE INSERT ON activity_types BEGIN SELECT RAISE(ABORT, '测试新增失败'); END")
            composeTestRule.onNodeWithTag("nav-settings").performClick()
            composeTestRule.onNodeWithTag("type-add").performClick()
            composeTestRule.onNodeWithTag("type-name-input").performTextInput(name)
            composeTestRule.onNodeWithTag("type-icon-sleep").performClick()
            composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("type-save").performClick()
            composeTestRule.waitUntil(5_000) {
                !viewModel.uiState.value.isSaving && viewModel.uiState.value.errorMessage != null
            }
            composeTestRule.onNodeWithTag("type-name-input").assertTextContains(name)
            composeTestRule.onNodeWithTag("type-icon-sleep").assertIsSelected()
            composeTestRule.onNodeWithTag("type-color-$selectedColor").assertIsSelected()
            composeTestRule.onNodeWithTag("type-save-error").assertExists()
            assertFalse(viewModel.uiState.value.managedActivityTypes.any { it.name == name })

            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_type_insert")
            composeTestRule.onNodeWithTag("type-save").performClick()
            awaitStableState()
            composeTestRule.onNodeWithTag("type-name-input").assertDoesNotExist()
            assertEquals(name, viewModel.uiState.value.managedActivityTypes.single { it.name == name }.name)
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_type_insert")
            database.close()
            if (viewModel.uiState.value.errorMessage != null) {
                composeTestRule.runOnUiThread { viewModel.refresh() }
                awaitStableState()
            }
        }
    }

    @Test
    fun typeEditorKeepsDraftAfterRealUpdateFailureAndCanRetry() {
        val name = "休息${System.nanoTime()}"
        val selectedColor = 0xFF4A8D69
        val database = TimeLoggerDatabase.open(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_type_update BEFORE UPDATE ON activity_types BEGIN SELECT RAISE(ABORT, '测试更新失败'); END")
            composeTestRule.onNodeWithTag("nav-settings").performClick()
            composeTestRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("type-edit-睡觉"))
            composeTestRule.onNodeWithTag("type-edit-睡觉").performClick()
            composeTestRule.onNodeWithTag("type-name-input").performTextReplacement(name)
            composeTestRule.onNodeWithTag("type-icon-walk").performClick()
            composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("type-save").performClick()
            composeTestRule.waitUntil(5_000) {
                !viewModel.uiState.value.isSaving && viewModel.uiState.value.errorMessage != null
            }
            composeTestRule.onNodeWithTag("type-name-input").assertTextContains(name)
            composeTestRule.onNodeWithTag("type-icon-walk").assertIsSelected()
            composeTestRule.onNodeWithTag("type-color-$selectedColor").assertIsSelected()
            composeTestRule.onNodeWithTag("type-save-error").assertExists()
            assertTrue(viewModel.uiState.value.managedActivityTypes.any { it.name == "睡觉" })

            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_type_update")
            composeTestRule.onNodeWithTag("type-save").performClick()
            awaitStableState()
            composeTestRule.onNodeWithTag("type-name-input").assertDoesNotExist()
            assertTrue(viewModel.uiState.value.managedActivityTypes.any { it.name == name && it.iconKey == "walk" && it.colorArgb == selectedColor })
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_type_update")
            database.close()
            if (viewModel.uiState.value.errorMessage != null) {
                composeTestRule.runOnUiThread { viewModel.refresh() }
                awaitStableState()
            }
        }
    }

    @Test
    fun typeEditorCanReachLastColorInLandscape() {
        val originalOrientation = composeTestRule.activity.requestedOrientation
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        try {
            awaitNode("record-list")
            composeTestRule.onNodeWithTag("nav-settings").performClick()
            composeTestRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("type-add"))
            composeTestRule.onNodeWithTag("type-add").performClick()
            val lastColor = 0xFFA45565
            composeTestRule.onNodeWithTag("type-color-$lastColor").performScrollTo().performClick().assertIsSelected()
        } finally {
            composeTestRule.activity.requestedOrientation = originalOrientation
        }
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
    fun runningActivityAnnouncesItsDuration() {
        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("时长", substring = true)
    }

    @Test
    fun homeOmitsTimelineWhileDayStatisticsKeepsIt() {
        composeTestRule.onAllNodesWithText("覆盖时长").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("当日时间轴").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("timeline-track").assertCountEquals(0)

        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        awaitStableState()
        composeTestRule.onAllNodesWithText("当日时间轴").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("timeline-track").assertCountEquals(0)

        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-duration-summary"))
        composeTestRule.onNodeWithTag("statistics-duration-summary").assertExists()
        composeTestRule.onNodeWithText("覆盖时长").assertExists()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("timeline-track"))
        composeTestRule.onNodeWithText("当日时间轴").assertExists()
        composeTestRule.onNodeWithTag("timeline-track").assertExists()
    }

    @Test
    fun statisticsCanSwitchBetweenDayWeekAndMonth() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("period-day")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("period-week").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals(viewModel.uiState.value.statistics.dateLabel)
        composeTestRule.onNodeWithTag("period-month").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals(viewModel.uiState.value.statistics.dateLabel)
        composeTestRule.onNodeWithTag("period-day").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals(viewModel.uiState.value.statistics.dateLabel)
    }

    @Test
    fun statisticsTypeCanDrillDownInEveryRange() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()

        listOf("day", "week", "month").forEach { range ->
            awaitNode("period-$range")
            composeTestRule.onNodeWithTag("period-$range").performClick()
            composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-type-walk"))
            composeTestRule.onNodeWithTag("statistics-type-walk").performClick()
            composeTestRule.onNodeWithTag("statistics-type-page").assertIsDisplayed()
            composeTestRule.onNodeWithTag("type-period-$range").performClick()
            composeTestRule.onNodeWithTag("statistics-type-page-title").assertTextEquals("走路统计")
            composeTestRule.onNodeWithTag("statistics-type-session-count").assertTextEquals("记录次数 1")
            if (range == "day") {
                composeTestRule.onAllNodesWithTag("statistics-type-record-bar").assertCountEquals(1)
            } else {
                composeTestRule.onNodeWithTag("type-period-$range").assertIsSelected()
                assertTrue(composeTestRule.onAllNodesWithTag("statistics-type-day-bar").fetchSemanticsNodes().isNotEmpty())
            }
            composeTestRule.onNodeWithTag("statistics-type-page-back").performClick()
            awaitNode("statistics-list")
            composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-period-label"))
            composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals(viewModel.uiState.value.statistics.dateLabel)
        }
    }

    @Test
    fun statisticsModeSwitchKeepsDateRangeAndReturnsToTypeList() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        awaitNode("statistics-mode-date")
        composeTestRule.onNodeWithTag("statistics-mode-date").assertIsSelected()
        composeTestRule.onNodeWithTag("period-week").performClick()
        awaitStableState()
        val dateLabel = viewModel.uiState.value.statistics.dateLabel

        composeTestRule.onNodeWithTag("statistics-mode-type").performClick()
        composeTestRule.onNodeWithTag("statistics-type-list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statistics-type-list").performScrollToNode(hasTestTag("statistics-type-walk"))
        composeTestRule.onNodeWithTag("statistics-type-walk").performClick()
        composeTestRule.onNodeWithTag("statistics-type-page-title").assertTextEquals("走路统计")
        composeTestRule.onNodeWithTag("statistics-type-period-empty").assertExists()
        composeTestRule.onNodeWithTag("statistics-type-page-back").performClick()
        composeTestRule.onNodeWithTag("statistics-type-list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statistics-mode-type").assertIsSelected()

        composeTestRule.onNodeWithTag("statistics-mode-date").performClick()
        composeTestRule.onNodeWithTag("statistics-list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period-week").assertIsSelected()
        composeTestRule.onNodeWithTag("statistics-period-label").assertTextEquals(dateLabel)
    }

    @Test
    fun systemBackReturnsFromTypeDetailsToTypeList() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-mode-type").performClick()
        composeTestRule.onNodeWithTag("statistics-type-list").performScrollToNode(hasTestTag("statistics-type-walk"))
        composeTestRule.onNodeWithTag("statistics-type-walk").performClick()
        composeTestRule.onNodeWithTag("statistics-type-page-back").assertContentDescriptionContains("返回统计列表")

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeTestRule.onNodeWithTag("statistics-type-list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statistics-mode-type").assertIsSelected()
    }

    @Test
    fun statisticsDetailAndModeSurviveTabSwitch() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-mode-type").performClick()
        composeTestRule.onNodeWithTag("statistics-type-list").performScrollToNode(hasTestTag("statistics-type-walk"))
        composeTestRule.onNodeWithTag("statistics-type-walk").performClick()
        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-type-page-title").assertTextEquals("走路统计")

        composeTestRule.onNodeWithTag("statistics-type-page-back").performClick()
        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-type-list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statistics-mode-type").assertIsSelected()
    }

    @Test
    fun statisticsScrollPositionSurvivesTabSwitch() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("period-month").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-type-walk"))
        val before = composeTestRule.onNodeWithTag("statistics-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)

        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        val after = composeTestRule.onNodeWithTag("statistics-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 1f)
    }

    @Test
    fun completedTypeRecordHasVisibleDeleteAction() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("statistics-type-walk").performClick()

        composeTestRule.onNodeWithTag("statistics-type-record-delete").performClick()
        composeTestRule.onNodeWithTag("statistics-type-delete-confirm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statistics-type-delete-confirm").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-type-session-count").assertTextEquals("记录次数 0")
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
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-duration-summary"))
        composeTestRule.onNodeWithTag("statistics-duration-summary").assertExists()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("statistics-month-chart"))
        composeTestRule.onNodeWithTag("statistics-month-chart").assertExists()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-type-walk"))
        composeTestRule.onNodeWithTag("statistics-type-walk").assertExists()
    }

    @Test
    fun bottomNavigationItemsExposeDestinationDescriptions() {
        composeTestRule.onNodeWithTag("nav-record").assertContentDescriptionContains("记录")
        composeTestRule.onNodeWithTag("nav-statistics").assertContentDescriptionContains("统计")
        composeTestRule.onNodeWithTag("nav-settings").assertContentDescriptionContains("设置")
    }

    @Test
    fun monthStatisticsShowsCalendarWithoutSeparateHistoryEntry() {
        composeTestRule.onNodeWithTag("history-open").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))
        composeTestRule.onNodeWithTag("history-month-pager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-record").assertIsDisplayed()
    }

    @Test
    fun monthCalendarSwipeUpdatesStatisticsPeriodAndSupportsOlderAnchor() {
        val today = LocalDate.now(viewModel.uiState.value.statisticsZoneId)
        val initialMonth = YearMonth.from(today).minusMonths(15)
        composeTestRule.runOnUiThread { viewModel.selectStatisticsAnchorDate(initialMonth.atDay(10)) }
        awaitStableState()
        openMonthHistory()
        composeTestRule.onNodeWithTag("history-month-label")
            .assertTextEquals("${initialMonth.year}年${initialMonth.monthValue}月")
        composeTestRule.onNodeWithTag("statistics-period-label")
            .assertTextEquals("${initialMonth.atDay(1)} — ${initialMonth.atEndOfMonth()}")

        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeDown(durationMillis = 500) }
        val previousMonth = initialMonth.minusMonths(1)
        composeTestRule.waitUntil(5_000) {
            viewModel.uiState.value.statisticsAnchorDate?.let(YearMonth::from) == previousMonth
        }
        composeTestRule.onNodeWithTag("history-month-label")
            .assertTextEquals("${previousMonth.year}年${previousMonth.monthValue}月")
        composeTestRule.onNodeWithTag("statistics-period-label")
            .assertTextEquals("${previousMonth.atDay(1)} — ${previousMonth.atEndOfMonth()}")
    }

    @Test
    fun bottomNavigationSwitchesBetweenThreeScreens() {
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("记录")
        composeTestRule.onNode(hasTestTag("screen-title") and hasAnyAncestor(hasTestTag("record-list"))).assertExists()

        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("统计")
        composeTestRule.onNode(hasTestTag("screen-title") and hasAnyAncestor(hasTestTag("statistics-list"))).assertExists()

        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("设置")
        composeTestRule.onNode(hasTestTag("screen-title") and hasAnyAncestor(hasTestTag("settings-list"))).assertExists()

        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertIsDisplayed().assertTextEquals("记录")
    }

    @Test
    fun monthStatisticsKeepsNavigationAndExistingCharts() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))
        composeTestRule.onNodeWithTag("history-month-pager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-record").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history-back").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-screen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("record-list").assertIsDisplayed()
    }

    @Test
    fun monthStatisticsShowsErrorAndRetry() {
        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("period-month").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))

        try {
            composeTestRule.runOnUiThread { viewModel.saveStatisticsZone("Invalid/Timezone") }
            composeTestRule.onNodeWithText("请输入有效的 IANA 时区").assertIsDisplayed()
            composeTestRule.onNodeWithTag("retry").assertIsDisplayed().performClick()

            awaitStableState()
            composeTestRule.onNodeWithTag("retry").assertDoesNotExist()
            composeTestRule.onNodeWithTag("statistics-list").assertIsDisplayed()
        } finally {
            if (viewModel.uiState.value.errorMessage != null) {
                composeTestRule.runOnUiThread { viewModel.refresh() }
                awaitStableState()
            }
        }
    }

    @Test
    fun historyEditorRejectsMissingDstTimeAndSavesExplicitOverlapOffsets() {
        composeTestRule.runOnUiThread { viewModel.saveStatisticsZone("America/New_York") }
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        openRecordedDay()
        composeTestRule.onNodeWithTag("history-edit-0").performScrollTo()
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
        composeTestRule.runOnUiThread { viewModel.selectStatisticsAnchorDate(LocalDate.parse("2026-11-01")) }
        awaitStableState()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))
        composeTestRule.onNodeWithTag("history-date-2026-11-01").performClick()
        composeTestRule.onNodeWithTag("history-edit-0").performScrollTo()
        composeTestRule.onNodeWithTag("history-edit-0").performClick()
        composeTestRule.onNodeWithTag("session-start-input").assertTextContains("2026-11-01 01:30:00.000")
        composeTestRule.onNodeWithTag("session-end-input").assertTextContains("2026-11-01 01:15:00.000")
        composeTestRule.onNodeWithTag("session-start-offset--04:00").assertIsSelected()
        composeTestRule.onNodeWithTag("session-end-offset--05:00").assertIsSelected()
    }

    @Test
    fun historyActivityGridShowsRecordedDayAsActivitySquare() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        val state = viewModel.uiState.value
        val recordDate = state.historySessions.single().startedAtUtc.atZone(state.statisticsZoneId).toLocalDate()

        openMonthHistory()

        composeTestRule.onNodeWithTag("history-month-pager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history-date-$recordDate")
            .assertIsDisplayed()
            .assertContentDescriptionContains("$recordDate，1 条记录")
    }

    @Test
    fun historyCalendarShowsSevenWeekdaysAndSwipesBetweenMonths() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val currentMonth = YearMonth.from(today)
        val previousMonth = currentMonth.minusMonths(1)
        val previousRecordDate = previousMonth.atDay(15)
        fun session(id: String, date: LocalDate) = HistorySessionItem(
            id,
            "走路",
            date.atTime(10, 0).atZone(zone).toInstant(),
            date.atTime(11, 0).atZone(zone).toInstant(),
        )
        showMonthHistory(TimeLoggerUiState(
            statisticsZoneId = zone,
            historySessions = listOf(session("current", today), session("previous", previousRecordDate)),
        ))

        composeTestRule.onNodeWithTag("history-month-label")
            .assertTextEquals("${currentMonth.year}年${currentMonth.monthValue}月")
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            composeTestRule.onNodeWithTag("history-weekday-$index").assertTextEquals(label)
        }
        composeTestRule.onNodeWithTag("history-date-$today").assertTextEquals(today.dayOfMonth.toString())
        composeTestRule.onNodeWithTag("history-date-${currentMonth.atDay(1)}").assertExists()
        composeTestRule.onNodeWithTag("history-date-${currentMonth.atEndOfMonth()}").assertExists()
        composeTestRule.onNodeWithTag("history-date-$previousRecordDate").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-activity-scroll").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-month-previous").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-month-next").assertDoesNotExist()

        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeDown(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-month-label")
            .assertTextEquals("${previousMonth.year}年${previousMonth.monthValue}月")
        composeTestRule.onNodeWithTag("history-date-$previousRecordDate").assertTextEquals("15")
        composeTestRule.onNodeWithTag("history-date-$previousRecordDate")
            .assertContentDescriptionContains("$previousRecordDate，1 条记录")
        composeTestRule.onNodeWithTag("history-date-$today").assertDoesNotExist()

        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeUp(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-date-$today").assertExists()
    }

    @Test
    fun historyCalendarCanShowMonthsOlderThanTwelveMonths() {
        val zone = ZoneId.of("UTC")
        val currentMonth = YearMonth.now(zone)
        val olderMonth = currentMonth.minusMonths(15)
        showMonthHistory(TimeLoggerUiState(statisticsZoneId = zone, statisticsAnchorDate = olderMonth.atDay(1)))
        composeTestRule.onNodeWithTag("history-month-label").assertTextEquals(
            "${olderMonth.year}年${olderMonth.monthValue}月",
        )
        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeDown(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-month-label").assertTextEquals(
            "${olderMonth.minusMonths(1).year}年${olderMonth.minusMonths(1).monthValue}月",
        )
        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeUp(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-month-label").assertTextEquals(
            "${olderMonth.year}年${olderMonth.monthValue}月",
        )
    }

    @Test
    fun historyCalendarShowsLastDateOfSixWeekMonthWithinPager() {
        val zone = ZoneId.of("UTC")
        val currentMonth = YearMonth.now(zone)
        val monthsBack = (0..11).first { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() > 35
        }
        val month = currentMonth.minusMonths(monthsBack.toLong())
        showMonthHistory(TimeLoggerUiState(statisticsZoneId = zone, statisticsAnchorDate = month.atDay(1)))
        val lastDay = composeTestRule.onNodeWithTag("history-date-${month.atEndOfMonth()}")
        lastDay.assertIsDisplayed()
        val pagerBounds = composeTestRule.onNodeWithTag("history-month-pager").getUnclippedBoundsInRoot()
        val labelTop = composeTestRule.onNodeWithTag("history-month-label").getUnclippedBoundsInRoot().top
        val dateBottom = lastDay.getUnclippedBoundsInRoot().bottom
        assertTrue("六周月份的月份标题必须保留顶部留白", labelTop >= pagerBounds.top + 12.dp)
        assertTrue("六周月份的月底日期必须保留底部留白", dateBottom <= pagerBounds.bottom - 12.dp)
    }

    @Test
    fun historyCalendarRespectsSundayStartAndMarksFutureDates() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        showMonthHistory(TimeLoggerUiState(statisticsZoneId = zone, firstDayOfWeek = DayOfWeek.SUNDAY))

        composeTestRule.onNodeWithTag("history-weekday-0").assertTextEquals("日")
        composeTestRule.onNodeWithTag("history-weekday-1").assertTextEquals("一")
        composeTestRule.onNodeWithTag("history-weekday-6").assertTextEquals("六")
        if (today.dayOfMonth < YearMonth.from(today).lengthOfMonth()) {
            val futureDate = today.plusDays(1)
            composeTestRule.onNodeWithTag("history-date-$futureDate")
                .assertContentDescriptionContains("$futureDate，未来")
        }
    }

    @Test
    fun historyCalendarHighlightsFutureEditedRecord() {
        val zone = ZoneId.of("UTC")
        val futureDate = LocalDate.now(zone).plusMonths(1).withDayOfMonth(10)
        val session = HistorySessionItem(
            "future", "走路",
            futureDate.atTime(10, 0).atZone(zone).toInstant(),
            futureDate.atTime(11, 0).atZone(zone).toInstant(),
        )
        showMonthHistory(TimeLoggerUiState(
            statisticsZoneId = zone,
            statisticsAnchorDate = futureDate,
            historySessions = listOf(session),
        ))

        composeTestRule.onNodeWithTag("history-date-$futureDate")
            .assertContentDescriptionContains("$futureDate，1 条记录", substring = true)
    }

    @Test
    fun monthStatisticsExpandsOnlyRecordedDayAndClearsSelectionAfterMonthSwipe() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 10)
        val firstDate = month.atDay(5)
        val secondDate = month.atDay(6)
        val sessions = listOf(
            HistorySessionItem("cross", "跨日", Instant.parse("2026-10-05T23:00:00Z"), Instant.parse("2026-10-06T01:00:00Z")),
            HistorySessionItem("second", "第二条", Instant.parse("2026-10-06T10:00:00Z"), Instant.parse("2026-10-06T11:00:00Z")),
        )
        composeTestRule.activity.setContent {
            var anchorDate by remember { mutableStateOf(month.atDay(1)) }
            StatisticsScreen(
                state = TimeLoggerUiState(
                    statisticsZoneId = zone,
                    statisticsRange = StatisticsRange.MONTH,
                    statisticsAnchorDate = anchorDate,
                    statistics = PeriodSummary(interval = StatisticsPeriod.month(anchorDate, zone).interval),
                    historySessions = sessions,
                ),
                onRangeSelected = {}, onAnchorDateSelected = { anchorDate = it },
                onSessionSaved = { _, _ -> }, onSessionDeleted = {},
                modifier = Modifier.fillMaxWidth().height(800.dp),
            )
        }

        composeTestRule.onNodeWithTag("history-day-details").assertDoesNotExist()
        composeTestRule.onNodeWithText("已结束记录").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-date-${month.atDay(4)}")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeTestRule.onNodeWithTag("history-day-details").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-date-$firstDate").assertIsNotSelected()
        composeTestRule.onNodeWithTag("history-date-$firstDate").performClick()
        composeTestRule.onNodeWithTag("history-date-$firstDate").assertIsSelected()
        composeTestRule.onNodeWithTag("history-day-details").assertExists()
        composeTestRule.onNodeWithTag("history-edit-0").assertExists()
        composeTestRule.onNodeWithTag("history-edit-1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-date-$secondDate").performClick()
        composeTestRule.onNodeWithTag("history-date-$firstDate").assertIsNotSelected()
        composeTestRule.onNodeWithTag("history-date-$secondDate").assertIsSelected()
        composeTestRule.onNodeWithTag("history-edit-1").assertExists()

        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeDown(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-day-details").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-month-pager").performTouchInput { swipeUp(durationMillis = 500) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history-date-$secondDate").assertIsNotSelected()
    }

    @Test
    fun monthStatisticsCanReachLastOfManyRecordsWithoutComposingAllRows() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.now(zone)
        val start = month.atDay(1).atTime(10, 0).atZone(zone).toInstant()
        val sessions = (0..79).map { index ->
            HistorySessionItem("record-$index", "走路", start.plusSeconds(index.toLong()), start.plusSeconds(index + 1L))
        }
        composeTestRule.activity.setContent {
            StatisticsScreen(
                state = TimeLoggerUiState(
                    statisticsZoneId = zone,
                    statisticsRange = StatisticsRange.MONTH,
                    statisticsAnchorDate = month.atDay(1),
                    statistics = PeriodSummary(
                        dateLabel = "${month.atDay(1)} — ${month.atEndOfMonth()}",
                        interval = StatisticsPeriod.month(month.atDay(1), zone).interval,
                    ),
                    historySessions = sessions,
                ),
                onRangeSelected = {}, onAnchorDateSelected = {}, onSessionSaved = { _, _ -> }, onSessionDeleted = {},
                modifier = Modifier.fillMaxWidth().height(800.dp),
            )
        }

        composeTestRule.onNodeWithTag("history-edit-79").assertDoesNotExist()
        composeTestRule.onNodeWithTag("history-date-${month.atDay(1)}").performClick()
        composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-edit-79"))
        composeTestRule.onNodeWithTag("history-edit-79").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history-edit-0").assertDoesNotExist()
    }

    @Test
    fun historyEditorRejectsEndAtStartAndKeepsDraft() {
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        awaitStableState()
        openRecordedDay()
        composeTestRule.onNodeWithTag("history-edit-0").performScrollTo()
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
            openRecordedDay()
            composeTestRule.onNodeWithTag("history-edit-0").performScrollTo()
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
            composeTestRule.onNodeWithTag("history-edit-0").assertDoesNotExist()
            composeTestRule.runOnUiThread { viewModel.selectStatisticsAnchorDate(LocalDate.parse("2026-01-01")) }
            awaitStableState()
            composeTestRule.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("history-month-pager"))
            composeTestRule.onNodeWithTag("history-date-2026-01-01").performClick()
            composeTestRule.onNodeWithTag("history-edit-0").performScrollTo()
            composeTestRule.onNodeWithTag("history-edit-0").assertExists()
            assertEquals(3600L, java.time.Duration.between(viewModel.uiState.value.historySessions.single().startedAtUtc, viewModel.uiState.value.historySessions.single().endedAtUtc).seconds)
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_history_edit")
            database.close()
        }
    }

    @Test
    fun typeActionsWrapWithinNarrowSettingsWidth() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                Box(Modifier.width(240.dp).testTag("narrow-settings")) {
                    SettingsScreen(
                        state = TimeLoggerUiState(
                            managedActivityTypes = listOf(ActivityTypeItem("sleep", "睡觉", "sleep", 0xFF5266A6, false)),
                            isLoading = false,
                        ),
                        onZoneSaved = {},
                        onWeekStartChanged = {},
                        onTypeCreated = { _, _, _, _ -> },
                        onTypeUpdated = { _, _, _, _, _ -> },
                        onTypeMoved = { _, _ -> },
                        onTypeArchived = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        awaitNode("settings-list")
        composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-archive-睡觉"))
        val container = composeTestRule.onNodeWithTag("narrow-settings").fetchSemanticsNode().boundsInRoot
        val edit = composeTestRule.onNodeWithTag("type-edit-睡觉").fetchSemanticsNode().boundsInRoot
        val archive = composeTestRule.onNodeWithTag("type-archive-睡觉").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("归档操作应在窄屏换行", archive.top > edit.top)
        assertTrue("归档操作不得超出设置容器", archive.right <= container.right)
    }

    @Test
    fun activityGridKeepsTwoColumnsAt320DpAndExpandsOnWideScreens() {
        val state = viewModel.uiState.value
        fun getCardTops(width: Int): List<Float> {
            composeTestRule.runOnUiThread {
                composeTestRule.activity.setContent {
                    Box(Modifier.requiredWidth(width.dp)) {
                        RecordScreen(state, {}, Modifier.fillMaxWidth().height(800.dp))
                    }
                }
            }
            awaitNode("activity-sleep")
            return listOf("sleep", "walk", "cycle").map { type ->
                composeTestRule.onNodeWithTag("activity-$type").fetchSemanticsNode().boundsInRoot.top
            }
        }

        val narrowTops = getCardTops(320)
        assertEquals("320dp 应显示两列", narrowTops[0], narrowTops[1], 1f)
        assertTrue("第三张活动卡应进入下一行", narrowTops[2] > narrowTops[1])

        val wideTops = getCardTops(720)
        assertEquals("宽屏应增加列数", wideTops[0], wideTops[2], 1f)
    }
}
