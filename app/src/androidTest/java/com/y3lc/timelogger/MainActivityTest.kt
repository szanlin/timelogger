package com.y3lc.timelogger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
