package com.y3lc.timelogger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsCanChooseFixedZoneAndSunday() {
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("settings-zone-fixed").performClick()
        composeTestRule.onNodeWithTag("settings-zone-input").performTextReplacement("America/New_York")
        composeTestRule.onNodeWithTag("settings-zone-save").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("settings-zone-current")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settings-zone-current").assertTextEquals("America/New_York")
        composeTestRule.onNodeWithTag("settings-week-sunday").performClick()
        composeTestRule.onNodeWithTag("settings-week-sunday").assertExists()
    }

    @Test
    fun settingsCanCreateEditAndArchiveType() {
        val name = "阅读${System.nanoTime()}"
        val edited = "学习${System.nanoTime()}"
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        composeTestRule.onNodeWithTag("type-add").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextInput(name)
        composeTestRule.onNodeWithTag("type-save").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-edit-$name"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-edit-$name").performClick()
        composeTestRule.onNodeWithTag("type-name-input").performTextReplacement(edited)
        composeTestRule.onNodeWithTag("type-save").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-move-up-$edited"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-move-up-$edited").performClick()
        composeTestRule.onNodeWithTag("type-archive-$edited").performClick()
        composeTestRule.onNodeWithTag("type-confirm-archive").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("type-archived-$edited"))
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("type-archived-$edited").assertExists()
    }

    @Test
    fun differentActivityTypesCanRunTogetherAndStopIndependently() {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("activity-sleep")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        composeTestRule.onNodeWithTag("activity-walk").performClick()
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-sleep").performClick()
        composeTestRule.onNodeWithTag("activity-sleep").assertContentDescriptionContains("点击开始", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").assertContentDescriptionContains("进行中", substring = true)
        composeTestRule.onNodeWithTag("activity-walk").performClick()
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
