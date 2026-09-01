package com.immersive.music

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
        waitForControl("顺序播放")
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
    }

    @Test
    fun playbackMode_andLibrary_haveNoRedundantLabels() {
        composeRule.onNodeWithContentDescription("顺序播放").performClick()
        waitForControl("单曲循环")
        composeRule.onNodeWithContentDescription("单曲循环").performClick()
        waitForControl("顺序播放")

        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onAllNodesWithText("最近播放").assertCountEquals(0)
        composeRule.onAllNodesWithText("本地音乐").assertCountEquals(0)
        composeRule.onAllNodesWithText("我喜欢的音乐").assertCountEquals(0)
    }

    @Test
    fun queue_longPress_revealsInlineDelete_withoutExtraSheet() {
        composeRule.onNodeWithContentDescription("播放列表").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("长按删除:First Light")
            .performTouchInput { longClick() }

        composeRule.onAllNodesWithText("歌曲选项").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("删除队列:First Light")
            .assertIsDisplayed()
            .performClick()

        composeRule.onAllNodesWithContentDescription("长按删除:First Light").assertCountEquals(0)
    }

    @Test
    fun localImport_opensInsideAppInsteadOfSystemFolders() {
        grantAudioPermission()
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithContentDescription("添加喜欢的音乐").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("手机音乐选择面板").assertIsDisplayed()
                composeRule.onNodeWithText("选择手机音乐").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun search_isCompactUtilityOnly() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithContentDescription("搜索输入框").assertIsDisplayed()
        composeRule.onNodeWithText("搜索歌曲或歌手").assertIsDisplayed()
        composeRule.onNodeWithText("只搜索你的歌曲").assertIsDisplayed()
    }
}