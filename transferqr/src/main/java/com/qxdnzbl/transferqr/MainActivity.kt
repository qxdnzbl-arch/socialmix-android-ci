package com.qxdnzbl.transferqr

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.source
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://oppo-iphone-transfer-qr.onrender.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TransferTheme {
                val vm: TransferViewModel = viewModel()
                TransferScreen(vm)
            }
        }
    }
}

private enum class Phase {
    PREPARING, WAITING, CONNECTED, SENDING, DONE, ERROR
}

private data class TransferUiState(
    val phase: Phase = Phase.PREPARING,
    val qrBitmap: Bitmap? = null,
    val progress: Float = 0f,
    val selectedCount: Int = 0,
    val selectedBytes: Long = 0L,
    val message: String = "正在准备二维码…"
)

private data class Session(val code: String, val token: String)
private data class PickedFile(val uri: android.net.Uri, val name: String, val size: Long, val mime: String?)

private class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    var ui by mutableStateOf(TransferUiState())
        private set

    private var session: Session? = null
    private var pollJob: Job? = null

    init {
        newSession()
    }

    fun newSession() {
        pollJob?.cancel()
        session = null
        ui = TransferUiState()

        viewModelScope.launch {
            var lastError: Throwable? = null
            repeat(4) { attempt ->
                try {
                    val created = withContext(Dispatchers.IO) { createSession() }
                    session = created
                    val qr = withContext(Dispatchers.Default) {
                        makeQr("$BASE_URL/receive/${created.code}")
                    }
                    ui = TransferUiState(
                        phase = Phase.WAITING,
                        qrBitmap = qr,
                        message = "等待 iPhone 扫码"
                    )
                    startPolling(created.code)
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                    if (attempt < 3) delay((attempt + 1) * 1200L)
                }
            }
            ui = ui.copy(
                phase = Phase.ERROR,
                message = lastError?.message?.takeIf { it.isNotBlank() } ?: "二维码准备失败"
            )
        }
    }

    private fun createSession(): Session {
        val request = Request.Builder()
            .url("$BASE_URL/api/create")
            .post(ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(parseServerError(text, "连接服务器失败"))
            val json = JSONObject(text)
            val code = json.optString("code")
            val token = json.optString("senderToken")
            if (code.isBlank() || token.isBlank()) error("服务器没有返回有效二维码")
            return Session(code, token)
        }
    }

    private fun startPolling(expectedCode: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && session?.code == expectedCode) {
                try {
                    val ready = withContext(Dispatchers.IO) { receiverReady(expectedCode) }
                    when {
                        ready && ui.phase == Phase.WAITING -> {
                            ui = ui.copy(phase = Phase.CONNECTED, message = "iPhone 已连接")
                        }
                        !ready && ui.phase == Phase.CONNECTED -> {
                            ui = ui.copy(phase = Phase.WAITING, message = "等待 iPhone 扫码")
                        }
                    }
                } catch (_: Throwable) {
                    // A short status failure should not destroy the current QR session.
                }
                delay(1000)
            }
        }
    }

    private fun receiverReady(code: String): Boolean {
        val request = Request.Builder().url("$BASE_URL/api/status/$code").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val json = JSONObject(response.body?.string().orEmpty())
            return json.optBoolean("receiverReady", false)
        }
    }

    fun sendFiles(uris: List<android.net.Uri>) {
        val current = session ?: return
        if (ui.phase != Phase.CONNECTED || uris.isEmpty()) return

        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { uris.map { readMeta(it) } }
                val totalKnown = files.all { it.size >= 0 }
                val totalBytes = if (totalKnown) files.sumOf { it.size } else -1L

                ui = ui.copy(
                    phase = Phase.SENDING,
                    progress = 0f,
                    selectedCount = files.size,
                    selectedBytes = totalBytes,
                    message = "正在发送"
                )

                withContext(Dispatchers.IO) {
                    upload(current, files)
                }

                pollJob?.cancel()
                ui = ui.copy(
                    phase = Phase.DONE,
                    progress = 1f,
                    message = "发送完成"
                )
            } catch (t: Throwable) {
                pollJob?.cancel()
                ui = ui.copy(
                    phase = Phase.ERROR,
                    message = t.message?.takeIf { it.isNotBlank() } ?: "发送失败，请重新连接"
                )
            }
        }
    }

    private fun upload(current: Session, files: List<PickedFile>) {
        val resolver = getApplication<Application>().contentResolver
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)

        files.forEach { file ->
            val body = ContentUriRequestBody(resolver, file)
            multipart.addFormDataPart("files", file.name, body)
        }

        val rawBody = multipart.build()
        val progressBody = ProgressRequestBody(rawBody) { sent, total ->
            val p = if (total > 0) (sent.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
            ui = ui.copy(progress = p)
        }

        val request = Request.Builder()
            .url("$BASE_URL/send/${current.code}")
            .header("x-sender-token", current.token)
            .post(progressBody)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(parseServerError(text, "发送失败 (${response.code})"))
            }
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optBoolean("ok", false) != true) error("服务器没有确认发送完成")
        }
    }

    private fun readMeta(uri: android.net.Uri): PickedFile {
        val resolver = getApplication<Application>().contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return PickedFile(uri, name, size, resolver.getType(uri))
    }

    private fun makeQr(text: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 840, 840, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val dark = 0xFF19352D.toInt()
        val light = 0xFFFFFFFF.toInt()
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) dark else light)
            }
        }
        return bitmap
    }

    private fun parseServerError(text: String, fallback: String): String {
        return runCatching { JSONObject(text).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}

private class ContentUriRequestBody(
    private val resolver: android.content.ContentResolver,
    private val file: PickedFile
) : RequestBody() {
    override fun contentType() = file.mime?.toMediaTypeOrNull()
    override fun contentLength(): Long = file.size

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri) ?: error("无法读取 ${file.name}")
        input.use { stream ->
            sink.writeAll(stream.source())
        }
    }
}

private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var lastEmit = 0L
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val now = System.nanoTime()
                if (now - lastEmit > 80_000_000L || written == total) {
                    lastEmit = now
                    onProgress(written, total)
                }
            }
        }
        val buffered = countingSink.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}

@Composable
private fun TransferScreen(vm: TransferViewModel) {
    val state = vm.ui
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.sendFiles(uris)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            Spacer(Modifier.height(28.dp))
            QrCard(state)
            Spacer(Modifier.weight(1f))
            BottomAction(
                state = state,
                onPick = { launcher.launch(arrayOf("*/*")) },
                onReset = vm::newSession
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(Modifier.size(13.dp))
        Column {
            Text(
                "手机互传",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Text(
                "扫码即可接收",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun QrCard(state: TransferUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x15000000), spotColor = Color(0x10000000))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(1.dp, Border, RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusPill(state)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(252.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE7ECE9), RoundedCornerShape(22.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            val qr = state.qrBitmap
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "接收二维码",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            when (state.phase) {
                Phase.CONNECTED, Phase.SENDING, Phase.DONE -> "iPhone 已连接"
                else -> "用 iPhone 相机扫码接收"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            when (state.phase) {
                Phase.PREPARING -> "正在创建安全连接"
                Phase.WAITING -> "扫码后会自动连接，不需要输入任何号码"
                Phase.CONNECTED -> "现在可以选择要发送的文件"
                Phase.SENDING -> fileSummary(state)
                Phase.DONE -> fileSummary(state)
                Phase.ERROR -> state.message
            },
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = if (state.phase == Phase.ERROR) Error else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp)
        )

        if (state.phase == Phase.SENDING) {
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = Primary,
                trackColor = SoftGreen
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("正在发送", fontSize = 12.sp, color = Muted)
                Text("${(state.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
        }
    }
}

@Composable
private fun StatusPill(state: TransferUiState) {
    val (icon, text, bg, fg) = when (state.phase) {
        Phase.PREPARING -> StatusVisual(Icons.Rounded.QrCode2, "准备中", Color(0xFFF1F3F2), Muted)
        Phase.WAITING -> StatusVisual(Icons.Rounded.PhoneIphone, "等待扫码", SoftGreen, PrimaryDark)
        Phase.CONNECTED -> StatusVisual(Icons.Rounded.CheckCircle, "已连接", SoftGreen, PrimaryDark)
        Phase.SENDING -> StatusVisual(Icons.Rounded.CloudUpload, "发送中", SoftGreen, PrimaryDark)
        Phase.DONE -> StatusVisual(Icons.Rounded.CheckCircle, "已完成", SoftGreen, PrimaryDark)
        Phase.ERROR -> StatusVisual(Icons.Rounded.Refresh, "需要重试", Color(0xFFFFEEEB), Error)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(7.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

private data class StatusVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String,
    val bg: Color,
    val fg: Color
)

@Composable
private fun BottomAction(
    state: TransferUiState,
    onPick: () -> Unit,
    onReset: () -> Unit
) {
    val enabled = state.phase == Phase.CONNECTED || state.phase == Phase.DONE || state.phase == Phase.ERROR
    val label = when (state.phase) {
        Phase.PREPARING -> "正在准备…"
        Phase.WAITING -> "等待 iPhone 扫码"
        Phase.CONNECTED -> "选择文件"
        Phase.SENDING -> "正在发送 ${(state.progress * 100).toInt()}%"
        Phase.DONE -> "再传一次"
        Phase.ERROR -> "重新生成二维码"
    }

    AnimatedContent(targetState = label, label = "primary-action") { current ->
        Button(
            onClick = {
                when (state.phase) {
                    Phase.CONNECTED -> onPick()
                    Phase.DONE, Phase.ERROR -> onReset()
                    else -> Unit
                }
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE6EAE7),
                disabledContentColor = Color(0xFF8A9691)
            )
        ) {
            Icon(
                when (state.phase) {
                    Phase.DONE, Phase.ERROR -> Icons.Rounded.Refresh
                    else -> Icons.Rounded.CloudUpload
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(9.dp))
            Text(current, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "文件只用于本次实时传输，不在 App 内长期保存",
        fontSize = 11.sp,
        color = Color(0xFF8C9692),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

private fun fileSummary(state: TransferUiState): String {
    val count = state.selectedCount
    val bytes = state.selectedBytes
    return if (bytes >= 0) "$count 个文件 · ${formatBytes(bytes)}" else "$count 个文件"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

private val AppBg = Color(0xFFF6F8F5)
private val Primary = Color(0xFF2F8F6B)
private val PrimaryDark = Color(0xFF226F53)
private val SoftGreen = Color(0xFFE7F4EE)
private val Ink = Color(0xFF1F2925)
private val Muted = Color(0xFF6E7A75)
private val Border = Color(0xFFE5EAE7)
private val Error = Color(0xFFB54738)

@Composable
private fun TransferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Primary,
            background = AppBg,
            surface = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}
