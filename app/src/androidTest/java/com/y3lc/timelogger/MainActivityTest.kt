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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.ui.TimeLoggerViewModel
import com.y3lc.timelogger.ui.ActivityTypeItem
import com.y3lc.timelogger.ui.SettingsScreen
import com.y3lc.timelogger.ui.TimeLoggerUiState
import com.y3lc.timelogger.ui.RecordScreen
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
            composeTestRule.onNodeWithTag("type-color-$selectedColor").performClick()
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
            composeTestRule.onNodeWithTag("type-color-$selectedColor").performClick()
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
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        try {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("nav-settings").performClick()
            composeTestRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("type-add"))
            composeTestRule.onNodeWithTag("type-add").performClick()
            val lastColor = 0xFFA45565
            composeTestRule.onNodeWithTag("type-color-$lastColor").performScrollTo().performClick().assertIsSelected()
        } finally {
            composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                        RecordScreen(state, {}, { _, _ -> }, Modifier.fillMaxWidth().height(800.dp))
                    }
                }
            }
            composeTestRule.waitForIdle()
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
