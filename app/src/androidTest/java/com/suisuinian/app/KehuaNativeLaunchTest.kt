package com.suisuinian.app
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
class KehuaNativeLaunchTest{
 @get:Rule val rule=createAndroidComposeRule<KehuaNativeActivity>()
 @Test fun freshLaunchShowsRealAuth(){rule.onNodeWithTag("login").assertIsDisplayed();rule.onNodeWithTag("password").assertIsDisplayed();rule.onNodeWithTag("auth-submit").assertIsDisplayed()}
}
