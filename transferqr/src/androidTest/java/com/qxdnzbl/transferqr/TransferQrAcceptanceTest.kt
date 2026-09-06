package com.qxdnzbl.transferqr

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransferQrAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsSendQrImmediatelyWithoutPickupCodeOrPreparingState() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("等待 iPhone").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("传输二维码").assertIsDisplayed()
        composeRule.onNodeWithText("发送到 iPhone").assertIsDisplayed()
        composeRule.onNodeWithText("打开 iPhone 相机扫描二维码").assertIsDisplayed()
        composeRule.onNodeWithText("扫码后自动连接").assertIsDisplayed()
        composeRule.onNodeWithText("发送").assertIsDisplayed()
        composeRule.onNodeWithText("接收").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("取件码", substring = true).fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("准备", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun receiveModeAlsoShowsQrImmediately() {
        composeRule.onNodeWithText("接收").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("从 iPhone 接收").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("传输二维码").assertIsDisplayed()
        composeRule.onNodeWithText("从 iPhone 接收").assertIsDisplayed()
        composeRule.onNodeWithText("扫码后在 iPhone 选择文件发送").assertIsDisplayed()
        composeRule.onNodeWithText("扫码后选择文件发送").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("准备", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun reverseIPhoneStyleUploadArrivesInAndroidDownloads() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = TransferViewModel(app)
        vm.switchMode(TransferMode.RECEIVE_FROM_IPHONE)

        val start = System.currentTimeMillis()
        while (vm.ui.qrBitmap == null && System.currentTimeMillis() - start < 5_000) {
            Thread.sleep(50)
        }
        val bitmap = vm.ui.qrBitmap ?: error("receive QR was not created immediately")
        val qrText = decodeQr(bitmap)
        val uri = Uri.parse(qrText)
        assertEquals("instant", uri.pathSegments[0])
        assertEquals("upload", uri.pathSegments[1])
        val id = uri.pathSegments[2]
        val token = uri.getQueryParameter("t") ?: error("missing sender token")
        val base = uri.scheme + "://" + uri.authority

        val client = OkHttpClient()
        assertTrue("Android receiver did not connect to live relay", waitForReceiver(client, base, id))

        client.newCall(Request.Builder().url(qrText).get().build()).execute().use { page ->
            assertEquals(200, page.code)
            assertTrue(page.body?.string().orEmpty().contains("发送到 Android"))
        }

        val suffix = System.currentTimeMillis()
        val firstName = "iphone-to-android-$suffix.txt"
        val secondName = "苹果回传-$suffix.txt"
        val firstExpected = "reverse-transfer-ok"
        val secondExpected = "来自 iPhone 的中文内容"
        val text = "text/plain".toMediaType()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", firstName, firstExpected.toRequestBody(text))
            .addFormDataPart("files", secondName, secondExpected.toRequestBody(text))
            .build()
        val sendRequest = Request.Builder()
            .url("$base/instant/send/$id")
            .header("x-sender-token", token)
            .post(multipart)
            .build()

        client.newCall(sendRequest).execute().use { sent ->
            assertEquals(sent.body?.string(), 200, sent.code)
        }

        val doneStart = System.currentTimeMillis()
        while (vm.ui.phase != Phase.DONE && System.currentTimeMillis() - doneStart < 15_000) {
            Thread.sleep(100)
        }
        assertEquals(vm.ui.message, Phase.DONE, vm.ui.phase)
        assertEquals(2, vm.ui.selectedCount)
        assertTrue(vm.ui.message.contains("下载/手机互传"))

        val first = findDownload(app, firstName)
        val second = findDownload(app, secondName)
        assertEquals(firstExpected, app.contentResolver.openInputStream(first)?.use { String(it.readBytes(), Charsets.UTF_8) })
        assertEquals(secondExpected, app.contentResolver.openInputStream(second)?.use { String(it.readBytes(), Charsets.UTF_8) })
        app.contentResolver.delete(first, null, null)
        app.contentResolver.delete(second, null, null)
    }

    private fun findDownload(app: Application, fileName: String): Uri {
        val resolver = app.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?"
        val args = arrayOf(fileName)
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            )
        } ?: error("received file was not saved to Android Downloads: $fileName")
    }

    private fun waitForReceiver(client: OkHttpClient, base: String, id: String): Boolean {
        repeat(100) {
            try {
                client.newCall(Request.Builder().url("$base/api/instant/status/$id").get().build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        if (json.optBoolean("receiverReady", false)) return true
                    }
                }
            } catch (_: Throwable) {
            }
            Thread.sleep(100)
        }
        return false
    }

    private fun decodeQr(bitmap: android.graphics.Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader().decode(binary).text
    }
}