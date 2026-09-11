package com.suisuinian.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionUiAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductionChatActivity>()

    @Test
    fun loginSearchContactOpenChatSendAndBackThroughRealUi() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2
        }

        val loginFields = composeRule.onAllNodes(hasSetTextAction())
        loginFields[0].performTextInput("skillci_a_fe71b659@gmail.com")
        loginFields[1].performTextInput("gFxNcUHRWI91e5dIn_WJhZ4S")
        composeRule.onNode(hasText("登录") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("我") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("我") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("联系人") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("联系人") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("＋") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("＋") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("skillci_b_3d9cbd")
        composeRule.onNode(hasText("搜索") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("账号：skillci_b_3d9cbd")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("账号：skillci_b_3d9cbd")).assertExists()

        composeRule.onNode(hasText("‹") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("Skill CI B") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Skill CI B") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodes(hasText("发送") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        val marker = "ui-${System.currentTimeMillis()}"
        composeRule.onNode(hasSetTextAction()).performTextInput(marker)
        composeRule.onNode(hasText("发送") and hasClickAction()).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText(marker)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(marker)).assertExists()

        composeRule.onNode(hasText("‹") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("联系人")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("联系人")).assertExists()
    }
}
