package com.y3lc.timelogger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
