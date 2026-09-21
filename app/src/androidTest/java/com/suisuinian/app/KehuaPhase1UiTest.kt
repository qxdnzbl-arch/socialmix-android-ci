package com.suisuinian.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class KehuaPhase1UiTest {
    @get:Rule
    val rule = createAndroidComposeRule<KehuaNativeActivity>()

    @Test
    fun freshNativeAuthFlowUsesCurrentUi() {
        rule.onNodeWithTag("login").assertIsDisplayed()
        rule.onNodeWithTag("password").assertIsDisplayed()
        rule.onNodeWithTag("auth-submit").assertIsDisplayed()

        rule.onNodeWithText("第一次来？注册").performClick()
        rule.onNodeWithTag("nickname").assertIsDisplayed()
        rule.onNodeWithTag("login").assertIsDisplayed()
        rule.onNodeWithTag("password").assertIsDisplayed()
        rule.onNodeWithTag("auth-submit").assertIsDisplayed()
    }
}
