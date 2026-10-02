package com.promptnotebook.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class PromptNotebookFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createSearchFavoritePersistAndDelete() {
        rule.activity.getSharedPreferences("prompt_notebook", 0).edit().clear().commit()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.onNodeWithText("新建第一条").performClick()
        rule.onNodeWithTag("title_input").performTextInput("测试提示词")
        rule.onNodeWithTag("category_input").performTextInput("开发")
        rule.onNodeWithTag("content_input").performTextInput("这是用于真实验收的完整提示词")
        rule.onNodeWithText("保存提示词").performClick()

        rule.onNodeWithText("测试提示词").assertExists()
        rule.onNodeWithText("复制提示词").assertExists()

        rule.onNodeWithContentDescription("收藏").performClick()
        rule.onNodeWithText("收藏").performClick()
        rule.onNodeWithText("测试提示词").assertExists()

        rule.onNodeWithTag("search_input").performTextInput("测试")
        rule.onNodeWithText("测试提示词").assertExists()
        rule.onNodeWithTag("search_input").performTextClearance()

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("测试提示词").assertExists()

        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("删除").performClick()
        rule.onNodeWithText("删除这条提示词？").assertExists()
        rule.onNodeWithText("删除").performClick()
        rule.onNodeWithText("把常用提示词都放在这里").assertExists()
    }
}
