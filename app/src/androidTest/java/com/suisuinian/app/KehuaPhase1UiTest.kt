package com.suisuinian.app

import androidx.compose.ui.test.assertExists
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
        rule.onNodeWithTag("login-button").assertExists().performClick()
        rule.onNodeWithTag("home-input-card").assertExists().performClick()

        rule.onNodeWithTag("publish-input").assertExists().performTextInput("今天想把这句话说出来")
        rule.onNodeWithTag("publish-button").assertExists().performClick()

        rule.onNodeWithTag("home-resonance-screen").assertExists()
        rule.onNodeWithTag("nav-1").assertExists().performClick()
        rule.onNodeWithTag("messages-screen").assertExists()

        rule.onNodeWithTag("message-row-0").assertExists().performClick()
        rule.onNodeWithTag("chat-screen").assertExists()

        rule.onNodeWithTag("chat-input").assertExists().performTextInput("你好")
        rule.onNodeWithTag("chat-send").assertExists().performClick()
        rule.onNodeWithText("你好").assertExists()
    }
}
