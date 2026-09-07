package com.qxdnzbl.lumichat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LumiUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchThenDrawerConnectionFlowWorks() {
        composeRule.onNodeWithText("连接 AI").assertIsDisplayed()
        composeRule.onNodeWithText("去创建密钥").assertIsDisplayed()
        composeRule.onNodeWithText("稍后").performClick()

        composeRule.onNodeWithText("想说什么就说。").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("菜单").performClick()
        composeRule.onNodeWithText("AI 连接").assertIsDisplayed()
        composeRule.onNodeWithText("未连接").assertIsDisplayed()
    }
}
