package com.qxdnzbl.transferqr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class TransferQrAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsUsableQrImmediatelyWithoutPickupCodeOrPreparingState() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("等待 iPhone").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("接收二维码").assertIsDisplayed()
        composeRule.onNodeWithText("发送到 iPhone").assertIsDisplayed()
        composeRule.onNodeWithText("打开 iPhone 相机扫描二维码").assertIsDisplayed()
        composeRule.onNodeWithText("扫码后自动连接").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("取件码", substring = true).fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("准备", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
