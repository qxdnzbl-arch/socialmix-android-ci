package com.qxdnzbl.transferqr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
    fun launchCreatesQrWithoutPickupCodeUi() {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("等待扫码").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("接收二维码").assertIsDisplayed()
        composeRule.onNodeWithText("用 iPhone 相机扫码接收").assertIsDisplayed()
        composeRule.onNodeWithText("扫码后会自动连接，不需要输入任何号码").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("取件码", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
