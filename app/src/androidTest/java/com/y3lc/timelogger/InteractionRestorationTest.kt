package com.y3lc.timelogger

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.timelogger.ui.HistorySessionItem
import com.y3lc.timelogger.ui.StatisticsMonthHistory
import com.y3lc.timelogger.ui.TimeLoggerUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class InteractionRestorationTest {
    @get:Rule(order = 0)
    val isolatedStorage = object : ExternalResource() {
        private val application
            get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IsolatedTestApplication

        override fun before() = application.beginIsolatedTest()

        override fun after() = application.deleteTestStorage()
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun typeEditorKeepsAddAndEditDraftAfterRotation() {
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag("nav-settings").fetchSemanticsNode() }.isSuccess
        }
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithTag("type-add").fetchSemanticsNode() }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-add").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextInput("未保存新增")
        composeTestRule.onNodeWithTag("type-icon-surf").performClick()
        val selectedColor = 0xFF4A8D69
        composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().performClick()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitUntil(5_000) {
            composeTestRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type-name-input").assertTextContains("未保存新增")
        composeTestRule.onNodeWithTag("type-icon-surf").assertIsSelected()
        composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("type-edit-睡觉"))
        composeTestRule.onNodeWithTag("type-edit-睡觉").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextReplacement("未保存编辑")
        composeTestRule.onNodeWithTag("type-icon-walk").performClick()
        composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().performClick()

        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitUntil(5_000) {
            composeTestRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type-name-input").assertTextContains("未保存编辑")
        composeTestRule.onNodeWithTag("type-icon-walk").assertIsSelected()
        composeTestRule.onNodeWithTag("type-color-$selectedColor").performScrollTo().assertIsSelected()
    }

    @Test
    fun recordedCalendarDayUsesFullCellOnNarrowScreen() {
        val date = LocalDate.of(2026, 10, 5)
        var clickedDate: LocalDate? = null
        val state = TimeLoggerUiState(
            statisticsAnchorDate = date,
            statisticsZoneId = ZoneId.of("Asia/Shanghai"),
            historySessions = listOf(HistorySessionItem("record", "阅读", Instant.parse("2026-10-05T02:00:00Z"), Instant.parse("2026-10-05T03:00:00Z"))),
        )
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                Box(Modifier.requiredWidth(320.dp)) {
                    StatisticsMonthHistory(state, {}, onDateSelected = { clickedDate = it })
                }
            }
        }
        val day = composeTestRule.onNodeWithTag("history-date-$date")
        day.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(date, clickedDate)
        val dateBounds = day.getUnclippedBoundsInRoot()
        val pagerBounds = composeTestRule.onNodeWithTag("history-month-pager").getUnclippedBoundsInRoot()
        assertTrue(dateBounds.left >= pagerBounds.left && dateBounds.right <= pagerBounds.right)
        assertTrue(dateBounds.bottom <= pagerBounds.bottom)
    }
}
