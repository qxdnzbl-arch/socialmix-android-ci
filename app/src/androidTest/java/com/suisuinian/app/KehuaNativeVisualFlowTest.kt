package com.suisuinian.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KehuaNativeVisualFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<KehuaNativeActivity>()

    private fun saveScreen(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(instrumentation.targetContext.filesDir, "$name.png")
        FileOutputStream(file).use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        assertTrue(file.length() > 0)
        val command = "run-as " + instrumentation.targetContext.packageName +
            " cat files/" + name + ".png > /sdcard/" + name + ".png"
        val pipe = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }
    }

    @Test
    fun signedInCoreNavigationAndVisualEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaProdApi(context)
        api.logout()
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val login = "visual" + suffix
        val password = "Qa_" + suffix + "_pass"

        try {
            val registered = api.register(login, password, "可话验收")
            assertTrue("VISUAL_REGISTER: " + registered.exceptionOrNull()?.message, registered.isSuccess)

            rule.activityRule.scenario.recreate()
            rule.waitUntil(15000) {
                rule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("home-screen").assertIsDisplayed()
            saveScreen("visual-home")

            rule.onNodeWithTag("nav-messages").performClick()
            rule.waitUntil(12000) {
                rule.onAllNodesWithTag("messages-screen").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("messages-screen").assertIsDisplayed()
            saveScreen("visual-messages")

            rule.onNodeWithTag("nav-me").performClick()
            rule.waitUntil(12000) {
                rule.onAllNodesWithTag("me-screen").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("me-screen").assertIsDisplayed()
            saveScreen("visual-me")

            rule.onNodeWithTag("settings-button").performClick()
            rule.waitUntil(12000) {
                rule.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("settings-screen").assertIsDisplayed()
            saveScreen("visual-settings")
        } finally {
            runCatching { api.deleteAccount() }
            api.logout()
        }
    }
}
