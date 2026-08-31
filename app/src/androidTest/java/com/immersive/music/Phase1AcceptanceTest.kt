package com.immersive.music

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class Phase1AcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_isImmersiveAndMinimal() {
        composeRule.onNodeWithText("心动").assertIsDisplayed()
        composeRule.onNodeWithText("极高音质").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("音乐库").assertIsDisplayed()
    }

    @Test
    fun previousAndNext_keepPausedStateStable() {
        composeRule.onNodeWithText("First Light").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("下一首").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Blue Hour").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("上一首").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First Light").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun search_isOnlyAUtility() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("搜索输入框").assertIsDisplayed()
        composeRule.onNodeWithText("只搜索你的歌曲").assertIsDisplayed()
    }

    @Test
    fun library_favoritesAndMoreAreUsable() {
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("打开我喜欢的音乐").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("我喜欢的音乐").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回音乐库").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("更多:First Light").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("歌曲选项").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
    }

    @Test
    fun library_hasLocalImport() {
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("本地音乐").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导入本地音乐").assertIsDisplayed()
    }
}
