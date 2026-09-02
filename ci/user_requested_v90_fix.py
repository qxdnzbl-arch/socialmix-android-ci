from pathlib import Path

test = Path('app/src/androidTest/java/com/immersive/music/Phase1AcceptanceTest.kt')
test.write_text(r'''package com.immersive.music

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private val testTitle = "Queue Isolation Test"

    private fun seedPhoneAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "queue-isolation-test.wav")
            put(MediaStore.Audio.Media.TITLE, testTitle)
            put(MediaStore.Audio.Media.ARTIST, "QA")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/ImmersiveMusicQA")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val uri = requireNotNull(resolver.insert(collection, values))
        val dataSize = 8_000
        val wav = ByteArray(44 + dataSize)
        fun putAscii(offset: Int, text: String) = text.toByteArray(Charsets.US_ASCII).copyInto(wav, offset)
        fun putLe16(offset: Int, value: Int) {
            wav[offset] = (value and 0xff).toByte()
            wav[offset + 1] = ((value shr 8) and 0xff).toByte()
        }
        fun putLe32(offset: Int, value: Int) {
            repeat(4) { i -> wav[offset + i] = ((value shr (8 * i)) and 0xff).toByte() }
        }
        putAscii(0, "RIFF")
        putLe32(4, 36 + dataSize)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putLe32(16, 16)
        putLe16(20, 1)
        putLe16(22, 1)
        putLe32(24, 8_000)
        putLe32(28, 16_000)
        putLe16(32, 2)
        putLe16(34, 16)
        putAscii(36, "data")
        putLe32(40, dataSize)
        resolver.openOutputStream(uri)?.use { it.write(wav) }
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun libraryImport_doesNotCreateQueue_untilSongIsPlayed() {
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("First Light").assertCountEquals(0)
        composeRule.onAllNodesWithText("Blue Hour").assertCountEquals(0)
        composeRule.onAllNodesWithText("Night Bloom").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("播放列表").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
        composeRule.onAllNodesWithText(testTitle).assertCountEquals(0)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        seedPhoneAudio()
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithContentDescription("添加喜欢的音乐").assertIsDisplayed().performClick()
        waitForText(testTitle)
        composeRule.onNodeWithText(testTitle).performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("已添加:$testTitle").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText(testTitle)

        // Import only: the library has the song while the playback queue stays empty.
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithContentDescription("播放列表").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
        composeRule.onAllNodesWithText(testTitle).assertCountEquals(0)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Explicit play is the only action that adds it to the queue.
        composeRule.onNodeWithText("音乐库").performClick()
        waitForText(testTitle)
        composeRule.onNodeWithText(testTitle).performClick()
        waitForText("心动")
        composeRule.onNodeWithContentDescription("播放列表").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
        composeRule.onNodeWithText(testTitle).assertIsDisplayed()
    }
}
''')
