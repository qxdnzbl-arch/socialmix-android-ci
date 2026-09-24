package com.suisuinian.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
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

@RunWith(AndroidJUnit4::class)
class KehuaNativeVisualFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<KehuaNativeActivity>()

    private fun saveScreen(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KehuaQA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Unable to create screenshot media: $name")
        context.contentResolver.openOutputStream(uri).use { out ->
            assertTrue(out != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
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
