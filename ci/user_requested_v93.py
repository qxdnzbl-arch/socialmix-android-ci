from pathlib import Path

test = Path('app/src/androidTest/java/com/immersive/music/MediaFixtureTest.kt')
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(r'''package com.immersive.music

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFixtureTest {
    @Test
    fun seedReadablePhoneMusic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val title = "queue-isolation-test"
        val displayName = "queue-isolation-test.wav"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        resolver.delete(collection, "${MediaStore.Audio.Media.DISPLAY_NAME} = ?", arrayOf(displayName))

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, "QA")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/ImmersiveMusicQA")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(collection, values))

        val sampleRate = 8_000
        val seconds = 8
        val dataSize = sampleRate * seconds * 2
        val wav = ByteArray(44 + dataSize)
        fun ascii(offset: Int, text: String) = text.toByteArray(Charsets.US_ASCII).copyInto(wav, offset)
        fun le16(offset: Int, value: Int) {
            wav[offset] = (value and 0xff).toByte()
            wav[offset + 1] = ((value shr 8) and 0xff).toByte()
        }
        fun le32(offset: Int, value: Int) {
            repeat(4) { i -> wav[offset + i] = ((value shr (8 * i)) and 0xff).toByte() }
        }
        ascii(0, "RIFF")
        le32(4, 36 + dataSize)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        le32(16, 16)
        le16(20, 1)
        le16(22, 1)
        le32(24, sampleRate)
        le32(28, sampleRate * 2)
        le16(32, 2)
        le16(34, 16)
        ascii(36, "data")
        le32(40, dataSize)
        resolver.openOutputStream(uri, "w")!!.use { it.write(wav) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
        }

        resolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )!!.use { cursor ->
            assertTrue("Seeded audio must be visible to the app", cursor.moveToFirst())
        }
    }
}
''')
