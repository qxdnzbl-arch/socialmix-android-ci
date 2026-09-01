package com.immersive.music

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class Phase1AcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun grantAudioPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        val pkg = composeRule.activity.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $pkg $permission")
            .close()
    }

    private fun assertToneArm(state: String) {
        composeRule.waitUntil(timeoutMillis = 4_000) {
            runCatching {
                composeRule.onAllNodesWithContentDescription(state).assertCountEquals(1)
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 4_000) {
            runCatching {
                composeRule.onAllNodesWithText(text).assertCountEquals(1)
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForControl(description: String) {
        composeRule.waitUntil(timeoutMillis = 4_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun home_isImmersiveAndMinimal() {
        composeRule.onNodeWithText("心动").assertIsDisplayed()
        composeRule.onNodeWithText("极高音质").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("音乐库").assertIsDisplayed()
        waitForControl("播放")
        waitForControl("上一首")
        waitForControl("下一首")
        assertToneArm("唱针:唱片外")
    }

    @Test
    fun previousAndNext_keepPausedButtonStable() {
        waitForText("First Light")
        waitForControl("播放")
        assertToneArm("唱针:唱片外")

        composeRule.onNodeWithContentDescription("下一首").performClick()
        waitForText("Blue Hour")
        waitForControl("播放")
        assertToneArm("唱针:唱片外")

        composeRule.onNodeWithContentDescription("上一首").performClick()
        waitForText("First Light")
        waitForControl("播放")
        assertToneArm("唱针:唱片外")

        composeRule.onNodeWithContentDescription("播放").performClick()
        waitForControl("暂停")
        assertToneArm("唱针:唱片上")

        composeRule.onNodeWithContentDescription("暂停").performClick()
        waitForControl("播放")
        assertToneArm("唱针:唱片外")
    }

    @Test
    fun favorite_andLibraryActions_areUsable() {
        composeRule.onNodeWithContentDescription("收藏").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("取消收藏").assertIsDisplayed()

        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onAllNodesWithText("最近播放").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("打开我喜欢的音乐").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("我喜欢的音乐").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("更多:First Light").performClick()
        composeRule.onNodeWithText("歌曲选项").assertIsDisplayed()
        composeRule.onNodeWithText("取消收藏").assertIsDisplayed()
    }

    @Test
    fun localImport_opensInsideAppInsteadOfSystemFolders() {
        grantAudioPermission()
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithContentDescription("导入本地音乐").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("手机音乐选择面板").assertIsDisplayed()
                composeRule.onNodeWithText("选择手机音乐").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun search_isOnlyAUtility() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithContentDescription("搜索输入框").assertIsDisplayed()
        composeRule.onNodeWithText("只搜索你的歌曲").assertIsDisplayed()
    }
}
