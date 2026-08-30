package com.suisuinian.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SocialExperimentActivity>()

    @Test
    fun freshInstall_launchesWithoutCrash_andShowsLogin() {
        composeRule.onNodeWithText("登录", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("进入你的消息和朋友", useUnmergedTree = true).assertExists()
    }
}
