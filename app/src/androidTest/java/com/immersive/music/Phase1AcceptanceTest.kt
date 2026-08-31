package com.immersive.music

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
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
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("音乐库").assertIsDisplayed()
        composeRule.onNodeWithText("笔记").assertDoesNotExist()
    }

    @Test
    fun nextTrack_changesSong() {
        composeRule.onNodeWithText("First Light").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一首").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Blue Hour")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Blue Hour").assertIsDisplayed()
    }

    @Test
    fun search_isOnlyAUtility() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithContentDescription("搜索输入框").assertIsDisplayed()
        composeRule.onNodeWithText("找你想听的").assertIsDisplayed()
    }

    @Test
    fun library_hasLocalImport() {
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithText("我喜欢的音乐").assertIsDisplayed()
        composeRule.onNodeWithText("本地音乐").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导入本地音乐").assertIsDisplayed()
    }
}
