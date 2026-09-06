package com.qxdnzbl.transferqr

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

private const val BASE_URL = "https://oppo-iphone-transfer-qr.onrender.com"
private const val DOWNLOAD_FOLDER = "手机互传"

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

internal enum class TransferMode {
    SEND_TO_IPHONE,
    RECEIVE_FROM_IPHONE
}

internal enum class Phase {
    WAITING, CONNECTED, SENDING, DONE, ERROR
}

internal data class TransferUiState(
    val mode: TransferMode = TransferMode.SEND_TO_IPHONE,
    val phase: Phase = Phase.WAITING,
    val qrBitmap: Bitmap? = null,
    val progress: Float = 0f,
    val selectedCount: Int = 0,
    val selectedBytes: Long = 0L,
    val message: String = "等待 iPhone 扫码"
)

private data class Session(val id: String, val token: String)
private data class PickedFile(val uri: android.net.Uri, val name: String, val size: Long, val mime: String?)
private data class RemoteStatus(val receiverReady: Boolean = false, val webSenderReady: Boolean = false)

internal class TransferViewModel(application: Application) : AndroidViewModel(application) {
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
    private var receiveJob: Job? = null
    private var receiveCall: Call? = null

    init {
        startMode(TransferMode.SEND_TO_IPHONE)
    }

    fun switchMode(mode: TransferMode) {
        if (ui.mode == mode && ui.phase != Phase.ERROR && ui.phase != Phase.DONE) return
        startMode(mode)
    }

    fun newSession() {
        startMode(ui.mode)
    }

    private fun startMode(mode: TransferMode) {
        pollJob?.cancel()
        receiveCall?.cancel()
        receiveJob?.cancel()
        receiveCall = null

        val id = UUID.randomUUID().toString().replace("-", "")
        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val created = Session(id, token)
        session = created

        val qrText = when (mode) {
            TransferMode.SEND_TO_IPHONE -> "$BASE_URL/instant/receive/$id"
            TransferMode.RECEIVE_FROM_IPHONE -> "$BASE_URL/instant/upload/$id?t=$token"
        }

        ui = TransferUiState(
            mode = mode,
            phase = Phase.WAITING,
            qrBitmap = makeQr(qrText),
            message = "等待 iPhone 扫码"
        )

        startPolling(id, mode)
        if (mode == TransferMode.RECEIVE_FROM_IPHONE) {
            startReceiving(created)
        }
    }

    private fun startPolling(expectedId: String, mode: TransferMode) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && session?.id == expectedId && ui.mode == mode) {
                try {
                    val status = withContext(Dispatchers.IO) { remoteStatus(expectedId) }
                    val connected = when (mode) {
                        TransferMode.SEND_TO_IPHONE -> status.receiverReady
                        TransferMode.RECEIVE_FROM_IPHONE -> status.webSenderReady
                    }
                    when {
                        connected && ui.phase == Phase.WAITING -> ui = ui.copy(phase = Phase.CONNECTED, message = "iPhone 已连接")
                        !connected && ui.phase == Phase.CONNECTED -> ui = ui.copy(phase = Phase.WAITING, message = "等待 iPhone 扫码")
                    }
                } catch (_: Throwable) {
                    // QR is created locally, so a sleeping relay never blocks the first screen.
                }
                delay(800)
            }
        }
    }

    private fun remoteStatus(id: String): RemoteStatus {
        val request = Request.Builder().url("$BASE_URL/api/instant/status/$id").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return RemoteStatus()
            val json = JSONObject(response.body?.string().orEmpty())
            return RemoteStatus(
                receiverReady = json.optBoolean("receiverReady", false),
                webSenderReady = json.optBoolean("webSenderReady", false)
            )
        }
    }

    fun sendFiles(uris: List<android.net.Uri>) {
        val current = session ?: return
        if (ui.mode != TransferMode.SEND_TO_IPHONE || ui.phase != Phase.CONNECTED || uris.isEmpty()) return

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

                withContext(Dispatchers.IO) { upload(current, files) }

                pollJob?.cancel()
                ui = ui.copy(phase = Phase.DONE, progress = 1f, message = "发送完成")
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
            multipart.addFormDataPart("files", file.name, ContentUriRequestBody(resolver, file))
        }

        val rawBody = multipart.build()
        val progressBody = ProgressRequestBody(rawBody) { sent, total ->
            val p = if (total > 0) (sent.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
            ui = ui.copy(progress = p)
        }

        val request = Request.Builder()
            .url("$BASE_URL/instant/send/${current.id}")
            .header("x-sender-token", current.token)
            .post(progressBody)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(parseServerError(text, "发送失败 (${response.code})"))
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optBoolean("ok", false) != true) error("服务器没有确认发送完成")
        }
    }

    private fun startReceiving(current: Session) {
        receiveJob?.cancel()
        receiveJob = viewModelScope.launch {
            var lastError: Throwable? = null
            var attempt = 0

            while (isActive && session?.id == current.id && ui.mode == TransferMode.RECEIVE_FROM_IPHONE) {
                try {
                    val count = receiveOnce(current)
                    if (count <= 0) error("没有收到文件")
                    pollJob?.cancel()
                    if (session?.id == current.id && ui.mode == TransferMode.RECEIVE_FROM_IPHONE) {
                        ui = ui.copy(
                            phase = Phase.DONE,
                            selectedCount = count,
                            message = "已保存到 下载/$DOWNLOAD_FOLDER"
                        )
                    }
                    return@launch
                } catch (_: CancellationException) {
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                    attempt += 1
                    if (attempt >= 10) break
                    delay((350L + attempt * 250L).coerceAtMost(2200L))
                }
            }

            if (isActive && session?.id == current.id && ui.mode == TransferMode.RECEIVE_FROM_IPHONE) {
                pollJob?.cancel()
                ui = ui.copy(
                    phase = Phase.ERROR,
                    message = lastError?.message?.takeIf { it.isNotBlank() } ?: "接收失败，请重新连接"
                )
            }
        }
    }

    private suspend fun receiveOnce(current: Session): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_URL/instant/receive/${current.id}").get().build()
        val call = client.newCall(request)
        receiveCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    error(if (text.isNotBlank()) text.take(120) else "接收连接失败 (${response.code})")
                }
                val body = response.body ?: error("接收连接没有数据")
                val zip = ZipInputStream(body.byteStream())
                var count = 0

                zip.use { stream ->
                    while (true) {
                        val entry = stream.nextEntry ?: break
                        if (!entry.isDirectory) {
                            if (count == 0) {
                                withContext(Dispatchers.Main) {
                                    if (session?.id == current.id && ui.mode == TransferMode.RECEIVE_FROM_IPHONE) {
                                        ui = ui.copy(phase = Phase.SENDING, selectedCount = 0, message = "正在接收")
                                    }
                                }
                            }
                            saveReceivedEntry(entry.name, stream)
                            count += 1
                            withContext(Dispatchers.Main) {
                                if (session?.id == current.id && ui.mode == TransferMode.RECEIVE_FROM_IPHONE) {
                                    ui = ui.copy(selectedCount = count, message = "正在接收第 $count 个文件")
                                }
                            }
                        }
                        stream.closeEntry()
                    }
                }
                count
            }
        } finally {
            if (receiveCall === call) receiveCall = null
        }
    }

    private fun saveReceivedEntry(rawName: String, input: ZipInputStream) {
        val resolver = getApplication<Application>().contentResolver
        val name = safeLocalName(rawName)
        val mime = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    input.copyTo(output, 256 * 1024)
                } ?: error("无法写入 $name")
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, ready, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val root = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val dir = File(root, DOWNLOAD_FOLDER).apply { mkdirs() }
            val target = uniqueFile(dir, name)
            FileOutputStream(target).use { output -> input.copyTo(output, 256 * 1024) }
        }
    }

    private fun safeLocalName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace("\u0000", "")
            .trim()
        return base.ifBlank { "file" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($n)$ext")
            n += 1
        }
        return candidate
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
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            640,
            640,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val dark = 0xFF18332E.toInt()
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
            .getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    override fun onCleared() {
        pollJob?.cancel()
        receiveCall?.cancel()
        receiveJob?.cancel()
        super.onCleared()
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
        input.use { stream -> sink.writeAll(stream.source()) }
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

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppHeader(onRefresh = vm::newSession)
            ModeSwitch(state.mode, vm::switchMode, enabled = state.phase != Phase.SENDING)
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (state.mode == TransferMode.SEND_TO_IPHONE) "发送到 iPhone" else "从 iPhone 接收",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            Text(
                text = if (state.mode == TransferMode.SEND_TO_IPHONE) {
                    "打开 iPhone 相机扫描二维码"
                } else {
                    "扫码后在 iPhone 选择文件发送"
                },
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
            Spacer(Modifier.height(20.dp))
            QrPanel(state)
            Spacer(Modifier.height(16.dp))
            ConnectionStatus(state)
            Spacer(Modifier.weight(1f))
            ActionArea(
                state = state,
                onPick = { launcher.launch(arrayOf("*/*")) },
                onReset = vm::newSession
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AppHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "手机互传",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = SurfaceContainer,
            tonalElevation = 0.dp
        ) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新二维码",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: TransferMode, onModeChange: (TransferMode) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .padding(4.dp)
    ) {
        ModeOption(
            text = "发送",
            selected = mode == TransferMode.SEND_TO_IPHONE,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(TransferMode.SEND_TO_IPHONE) }
        )
        ModeOption(
            text = "接收",
            selected = mode == TransferMode.RECEIVE_FROM_IPHONE,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(TransferMode.RECEIVE_FROM_IPHONE) }
        )
    }
}

@Composable
private fun ModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) OnSurface else OnSurfaceVariant
        )
    }
}

@Composable
private fun QrPanel(state: TransferUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(246.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
            ) {
                val qr = state.qrBitmap
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "传输二维码",
                        modifier = Modifier.padding(14.dp).fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (state.mode == TransferMode.SEND_TO_IPHONE) "扫码后自动连接" else "扫码后选择文件发送",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatus(state: TransferUiState) {
    val label = when (state.phase) {
        Phase.WAITING -> "等待 iPhone"
        Phase.CONNECTED -> if (state.mode == TransferMode.SEND_TO_IPHONE) "iPhone 已连接" else "iPhone 已打开"
        Phase.SENDING -> if (state.mode == TransferMode.SEND_TO_IPHONE) "正在发送" else "正在接收"
        Phase.DONE -> if (state.mode == TransferMode.SEND_TO_IPHONE) "发送完成" else "接收完成"
        Phase.ERROR -> if (state.mode == TransferMode.SEND_TO_IPHONE) "发送失败" else "接收失败"
    }
    val bg = when (state.phase) {
        Phase.WAITING -> SurfaceContainer
        Phase.ERROR -> ErrorContainer
        else -> PrimaryContainer
    }
    val fg = when (state.phase) {
        Phase.WAITING -> OnSurfaceVariant
        Phase.ERROR -> OnErrorContainer
        else -> OnPrimaryContainer
    }

    Surface(shape = RoundedCornerShape(12.dp), color = bg, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.phase == Phase.WAITING) Outline else Primary)
            )
            Spacer(Modifier.size(9.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
        }
    }
}

@Composable
private fun ActionArea(state: TransferUiState, onPick: () -> Unit, onReset: () -> Unit) {
    Crossfade(targetState = state.mode to state.phase, label = "action-state") { pair ->
        val mode = pair.first
        val phase = pair.second
        if (mode == TransferMode.SEND_TO_IPHONE) {
            when (phase) {
                Phase.WAITING -> HintRow(Icons.Rounded.PhoneIphone, "等 iPhone 扫码后即可选择文件")
                Phase.CONNECTED -> PrimaryActionButton("选择文件", Icons.Rounded.InsertDriveFile, onPick)
                Phase.SENDING -> SendingProgress(state)
                Phase.DONE -> PrimaryActionButton("再传一次", Icons.Rounded.Refresh, onReset)
                Phase.ERROR -> ErrorAction(state.message, "重新连接", onReset)
            }
        } else {
            when (phase) {
                Phase.WAITING -> HintRow(Icons.Rounded.PhoneIphone, "用 iPhone 扫码后选择文件")
                Phase.CONNECTED -> HintRow(Icons.Rounded.CloudDownload, "在 iPhone 选择文件并点发送")
                Phase.SENDING -> ReceivingProgress(state)
                Phase.DONE -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.message,
                            fontSize = 13.sp,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton("继续接收", Icons.Rounded.Refresh, onReset)
                    }
                }
                Phase.ERROR -> ErrorAction(state.message, "重新接收", onReset)
            }
        }
    }
}

@Composable
private fun HintRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(7.dp))
        Text(text, fontSize = 13.sp, color = OnSurfaceVariant)
    }
}

@Composable
private fun ErrorAction(message: String, buttonText: String, onReset: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = OnErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        PrimaryActionButton(buttonText, Icons.Rounded.Refresh, onReset)
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SendingProgress(state: TransferUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudUpload, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(fileSummary(state), fontSize = 13.sp, color = OnSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${(state.progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Primary,
            trackColor = SurfaceContainerHigh
        )
    }
}

@Composable
private fun ReceivingProgress(state: TransferUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                if (state.selectedCount > 0) "已接收 ${state.selectedCount} 个文件" else "正在接收",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Primary,
            trackColor = SurfaceContainerHigh
        )
    }
}

private fun fileSummary(state: TransferUiState): String {
    val count = state.selectedCount
    val bytes = state.selectedBytes
    return if (bytes >= 0) "$count 个文件 · ${formatBytes(bytes)}" else "$count 个文件"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val kb = bytes / 1000.0
    if (kb < 1000) return String.format("%.1f KB", kb)
    val mb = kb / 1000.0
    if (mb < 1000) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1000.0)
}

// LocalSend currently builds its default light theme from Material 3 with a teal seed.
// These fixed values mirror that visual language without copying LocalSend branding or assets.
private val Primary = Color(0xFF006A60)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFF74F8E6)
private val OnPrimaryContainer = Color(0xFF00201C)
private val AppBackground = Color(0xFFFAFDFB)
private val OnSurface = Color(0xFF191C1B)
private val OnSurfaceVariant = Color(0xFF3F4946)
private val SurfaceContainerLow = Color(0xFFF4F7F5)
private val SurfaceContainer = Color(0xFFEEF2F0)
private val SurfaceContainerHigh = Color(0xFFE8ECEA)
private val Outline = Color(0xFF6F7976)
private val OutlineVariant = Color(0xFFBEC9C5)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)

@Composable
private fun TransferTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            background = AppBackground,
            onBackground = OnSurface,
            surface = AppBackground,
            onSurface = OnSurface,
            surfaceVariant = SurfaceContainer,
            onSurfaceVariant = OnSurfaceVariant,
            outline = Outline,
            outlineVariant = OutlineVariant,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer
        ),
        content = content
    )
}