package com.suisuinian.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class KehuaPhase1UiTest {
    @get:Rule
    val rule = createAndroidComposeRule<KehuaActivity>()

    @Test
    fun corePhase1FlowWorks() {
        rule.onNodeWithTag("login-button").performClick()
        rule.onNodeWithTag("home-input-card").performClick()

        rule.onNodeWithTag("publish-input").performTextInput("今天想把这句话说出来")
        rule.onNodeWithTag("visibility-button").performClick()
        rule.onNodeWithText("仅自己可见").performClick()
        rule.onNodeWithTag("publish-button").performClick()

        rule.onNodeWithTag("home-resonance-screen").assertIsDisplayed()
        rule.onNodeWithTag("nav-1").performClick()
        rule.onNodeWithTag("messages-screen").assertIsDisplayed()

        rule.onNodeWithTag("message-row-0").performClick()
        rule.onNodeWithTag("chat-screen").assertIsDisplayed()

        rule.onNodeWithTag("chat-input").performTextInput("测试消息123")
        rule.onNodeWithTag("chat-send").performClick()
        rule.onNodeWithText("测试消息123").assertIsDisplayed()
    }
}
