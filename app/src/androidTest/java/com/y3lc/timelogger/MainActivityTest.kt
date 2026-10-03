package com.y3lc.timelogger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.time.DayOfWeek

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
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("点击开始", substring = true)
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
            runCatching { composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("点击开始", substring = true) }.isSuccess
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
    fun bottomNavigationSwitchesBetweenThreeScreens() {
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("记录")

        composeTestRule.onNodeWithTag("nav-statistics").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("统计")

        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertTextEquals("设置")

        composeTestRule.onNodeWithTag("nav-record").performClick()
        composeTestRule.onNodeWithTag("screen-title").assertIsDisplayed().assertTextEquals("记录")
    }
}
