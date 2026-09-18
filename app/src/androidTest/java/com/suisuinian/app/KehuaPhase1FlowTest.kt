package com.suisuinian.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KehuaPhase1FlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<KehuaActivity>()

    @Test
    fun realNativeCoreFlowWorks() {
        rule.onNodeWithTag("login-button").performClick()
        rule.onNodeWithTag("home-input").performClick()
        rule.onNodeWithTag("publish-input").performTextInput("今天也想认真说一句话")
        rule.onNodeWithTag("visibility-button").performClick()
        rule.onNodeWithText("仅自己可见").performClick()
        rule.onNodeWithTag("publish-button").performClick()
        rule.onNodeWithText("新的共鸣已经到达").assertExists()

        rule.onNodeWithTag("nav-1").performClick()
        rule.onNodeWithTag("message-row-0").performClick()
        rule.onNodeWithTag("chat-input").performTextInput("你好")
        rule.onNodeWithTag("chat-send").performClick()
        rule.onNodeWithText("你好").assertExists()
    }
}
