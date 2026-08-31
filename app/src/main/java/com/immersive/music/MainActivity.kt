package com.immersive.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { MaterialTheme { MusicApp() } }
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long,
    val theme: Color,
    val coverBytes: ByteArray? = null,
)

data class DeviceAudio(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long,
)

enum class AppPage { HOME, LIBRARY, SEARCH, FAVORITES }

private data class DemoSpec(val title: String, val artist: String, val frequency: Double)

private val DemoTracks = listOf(
    DemoSpec("First Light", "Mori", 220.0),
    DemoSpec("Blue Hour", "Luna", 277.18),
    DemoSpec("Night Bloom", "Aster", 329.63),
)

private val DemoColors = listOf(
    Color(0xFF647A35),
    Color(0xFF536A80),
    Color(0xFF785052),
)

@Composable
fun MusicApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("immersive_music", Context.MODE_PRIVATE) }
    val demoUris = remember { ensureDemoAudio(context) }

    val tracks = remember {
        mutableStateListOf<Track>().apply {
            DemoTracks.forEachIndexed { index, item ->
                add(
                    Track(
                        id = "demo:$index",
                        title = item.title,
                        artist = item.artist,
                        uri = Uri.fromFile(demoUris[index]).toString(),
                        durationMs = 8_000L,
                        theme = DemoColors[index],
                    )
                )
            }
        }
    }
    val favoriteIds = remember { mutableStateListOf<String>() }
    val recentIds = remember { mutableStateListOf<String>() }

    var page by remember { mutableStateOf(AppPage.HOME) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var playIntent by remember { mutableStateOf(false) }
    var needleOnDisc by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(tracks.first().durationMs) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playerPrepared by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showLocalMusic by remember { mutableStateOf(false) }
    var localScanBusy by remember { mutableStateOf(false) }
    var deviceAudio by remember { mutableStateOf<List<DeviceAudio>>(emptyList()) }

    fun saveFavorites() {
        prefs.edit().putStringSet("favorites", favoriteIds.toSet()).apply()
    }

    fun saveRecent() {
        val array = JSONArray()
        recentIds.forEach { array.put(it) }
        prefs.edit().putString("recent", array.toString()).apply()
    }

    fun markRecent(id: String) {
        recentIds.remove(id)
        recentIds.add(0, id)
        while (recentIds.size > 20) recentIds.removeAt(recentIds.lastIndex)
        saveRecent()
    }

    fun removeRecent(id: String) {
        recentIds.remove(id)
        saveRecent()
    }

    fun toggleFavorite(id: String) {
        if (favoriteIds.contains(id)) favoriteIds.remove(id) else favoriteIds.add(id)
        saveFavorites()
    }

    fun playTrack(index: Int, requestedPlaying: Boolean, recordRecent: Boolean = true) {
        if (tracks.isEmpty()) return
        val safeIndex = (index % tracks.size + tracks.size) % tracks.size
        val track = tracks[safeIndex]

        needleOnDisc = false
        playerPrepared = false
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null

        currentIndex = safeIndex
        positionMs = 0L
        durationMs = max(1L, track.durationMs)
        playIntent = requestedPlaying
        if (recordRecent) markRecent(track.id)

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setDataSource(context, Uri.parse(track.uri))
            player.setOnPreparedListener {
                if (mediaPlayer !== it) return@setOnPreparedListener
                playerPrepared = true
                durationMs = max(1, it.duration).toLong()
                if (playIntent) {
                    runCatching { it.start() }
                        .onSuccess { needleOnDisc = true }
                        .onFailure {
                            playIntent = false
                            needleOnDisc = false
                        }
                } else {
                    needleOnDisc = false
                }
            }
            player.setOnCompletionListener {
                if (mediaPlayer !== it) return@setOnCompletionListener
                playIntent = false
                needleOnDisc = false
                positionMs = durationMs
            }
            player.setOnErrorListener { mp, _, _ ->
                if (mediaPlayer === mp) {
                    playerPrepared = false
                    playIntent = false
                    needleOnDisc = false
                }
                true
            }
            player.prepareAsync()
        }.onFailure {
            playerPrepared = false
            playIntent = false
            needleOnDisc = false
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
        }
    }

    fun togglePlayback() {
        val player = mediaPlayer
        if (player == null) {
            playTrack(currentIndex, true)
            return
        }
        if (!playerPrepared) {
            playIntent = !playIntent
            needleOnDisc = false
            return
        }
        if (playIntent) {
            needleOnDisc = false
            runCatching { player.pause() }
            playIntent = false
        } else {
            playIntent = true
            runCatching {
                player.start()
                needleOnDisc = true
            }.onFailure {
                needleOnDisc = false
                playIntent = false
            }
        }
    }

    fun loadDeviceAudio() {
        localScanBusy = true
        showLocalMusic = true
        scope.launch {
            deviceAudio = withContext(Dispatchers.IO) { queryDeviceAudio(context) }
            localScanBusy = false
        }
    }

    val audioPermission = remember { audioReadPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDeviceAudio()
    }

    fun openLocalMusic() {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            loadDeviceAudio()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    fun importDeviceAudio(item: DeviceAudio) {
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                extractTrack(context, item.uri) ?: Track(
                    id = "local:${item.uri}",
                    title = item.title,
                    artist = item.artist,
                    uri = item.uri.toString(),
                    durationMs = item.durationMs,
                    theme = themeFromArtwork(null, item.title),
                )
            }
            if (tracks.none { it.id == imported.id }) tracks.add(imported)
            val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
            stored.add(item.uri.toString())
            prefs.edit().putStringSet("imported_uris", stored).apply()
        }
    }

    LaunchedEffect(Unit) {
        favoriteIds.addAll(prefs.getStringSet("favorites", emptySet()).orEmpty())
        prefs.getString("recent", null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                repeat(arr.length()) { recentIds.add(arr.getString(it)) }
            }
        }
        val savedUris = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toList()
        val imported = withContext(Dispatchers.IO) {
            savedUris.mapNotNull { uri -> extractTrack(context, Uri.parse(uri)) }
        }
        imported.forEach { if (tracks.none { t -> t.id == it.id }) tracks.add(it) }
        playTrack(0, false)
    }

    LaunchedEffect(playIntent, mediaPlayer) {
        while (isActive) {
            if (playIntent && playerPrepared) {
                mediaPlayer?.let { player ->
                    runCatching { positionMs = player.currentPosition.toLong() }
                }
            }
            delay(160)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerPrepared = false
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val currentTrack = tracks.getOrElse(currentIndex) { tracks.first() }
    val animatedBackground by animateColorAsState(
        targetValue = currentTrack.theme,
        animationSpec = tween(820),
        label = "trackBackground",
    )

    LaunchedEffect(page) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = page != AppPage.HOME
        controller.isAppearanceLightNavigationBars = page != AppPage.HOME
    }

    when (page) {
        AppPage.HOME -> HomeScreen(
            track = currentTrack,
            background = animatedBackground,
            isPlaying = playIntent,
            needleOnDisc = needleOnDisc,
            positionMs = positionMs,
            durationMs = durationMs,
            isFavorite = favoriteIds.contains(currentTrack.id),
            onToggleFavorite = { toggleFavorite(currentTrack.id) },
            onPlayPause = ::togglePlayback,
            onPrevious = { playTrack(currentIndex - 1, playIntent) },
            onNext = { playTrack(currentIndex + 1, playIntent) },
            onSeek = { target ->
                positionMs = target
                if (playerPrepared) mediaPlayer?.runCatching { seekTo(target.toInt()) }
            },
            onSearch = { page = AppPage.SEARCH },
            onQueue = { showQueue = true },
            onLibrary = { page = AppPage.LIBRARY },
        )

        AppPage.LIBRARY -> LibraryScreen(
            tracks = tracks,
            favoriteIds = favoriteIds,
            recentIds = recentIds,
            currentTrack = currentTrack,
            isPlaying = playIntent,
            onImport = ::openLocalMusic,
            onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },
            onPlayPause = ::togglePlayback,
            onQueue = { showQueue = true },
            onHome = { page = AppPage.HOME },
            onOpenFavorites = { page = AppPage.FAVORITES },
            onMore = { menuTrack = it },
        )

        AppPage.FAVORITES -> FavoritesScreen(
            tracks = tracks.filter { favoriteIds.contains(it.id) },
            currentTrack = currentTrack,
            isPlaying = playIntent,
            onBack = { page = AppPage.LIBRARY },
            onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },
            onMore = { menuTrack = it },
            onPlayPause = ::togglePlayback,
            onQueue = { showQueue = true },
        )

        AppPage.SEARCH -> SearchScreen(
            tracks = tracks,
            currentTrack = currentTrack,
            onBack = { page = AppPage.HOME },
            onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },
        )
    }

    if (showQueue) {
        QueueSheet(
            tracks = tracks,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            onDismiss = { showQueue = false },
            onTrack = { index ->
                playTrack(index, true)
                showQueue = false
            },
        )
    }

    menuTrack?.let { selected ->
        TrackActionSheet(
            track = selected,
            currentTrack = currentTrack,
            favorite = favoriteIds.contains(selected.id),
            inRecent = recentIds.contains(selected.id),
            onDismiss = { menuTrack = null },
            onToggleFavorite = {
                toggleFavorite(selected.id)
                menuTrack = null
            },
            onRemoveRecent = {
                removeRecent(selected.id)
                menuTrack = null
            },
        )
    }

    if (showLocalMusic) {
        LocalMusicSheet(
            currentTrack = currentTrack,
            deviceAudio = deviceAudio,
            busy = localScanBusy,
            addedUris = tracks.filter { it.id.startsWith("local:") }.map { it.uri }.toSet(),
            onDismiss = { showLocalMusic = false },
            onAdd = ::importDeviceAudio,
        )
    }
}

private fun audioReadPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun queryDeviceAudio(context: Context): List<DeviceAudio> {
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    return buildList {
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val display = cursor.getString(displayCol).orEmpty()
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: display.substringBeforeLast('.').ifBlank { "本地音乐" }
                val artist = cursor.getString(artistCol)
                    ?.takeUnless { it.isBlank() || it == "<unknown>" }
                    ?: "未知歌手"
                val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                add(
                    DeviceAudio(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = ContentUris.withAppendedId(collection, id),
                        durationMs = duration,
                    )
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = max(0L, ms) / 1000L
    return "%d:%02d".format(total / 60, total % 60)
}

fun extractTrack(context: Context, uri: Uri): Track? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(context, uri)
            ?: "本地音乐"
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: "未知歌手"
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val art = retriever.embeddedPicture
        Track(
            id = "local:${uri}",
            title = title,
            artist = artist,
            uri = uri.toString(),
            durationMs = duration,
            theme = themeFromArtwork(art, title),
            coverBytes = art,
        )
    } catch (_: FileNotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).substringBeforeLast('.') else null
        }
    }.getOrNull()
}

fun themeFromArtwork(bytes: ByteArray?, seed: String): Color {
    if (bytes != null) {
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                val small = android.graphics.Bitmap.createScaledBitmap(bmp, 18, 18, true)
                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0L
                for (y in 0 until small.height) {
                    for (x in 0 until small.width) {
                        val c = small.getPixel(x, y)
                        r += android.graphics.Color.red(c)
                        g += android.graphics.Color.green(c)
                        b += android.graphics.Color.blue(c)
                        count++
                    }
                }
                small.recycle()
                if (small !== bmp) bmp.recycle()
                if (count > 0) {
                    val rr = ((r / count) * .60).toInt().coerceIn(42, 146)
                    val gg = ((g / count) * .60).toInt().coerceIn(42, 146)
                    val bb = ((b / count) * .60).toInt().coerceIn(42, 146)
                    return Color(android.graphics.Color.rgb(rr, gg, bb))
                }
            }
        }
    }
    val palette = listOf(0xFF647A35, 0xFF55687C, 0xFF76504E, 0xFF436D6F, 0xFF75633D, 0xFF655777)
    return Color(palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size])
}

private fun ensureDemoAudio(context: Context): List<File> {
    val sampleRate = 11_025
    val seconds = 8
    val totalSamples = sampleRate * seconds
    return DemoTracks.mapIndexed { index, spec ->
        val file = File(context.filesDir, "demo_track_$index.wav")
        if (!file.exists() || file.length() < 44) {
            val dataSize = totalSamples * 2
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1.toShort())
            buffer.putShort(1.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2.toShort())
            buffer.putShort(16.toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)
            repeat(totalSamples) { i ->
                val t = i.toDouble() / sampleRate
                val fadeIn = min(1.0, i / (sampleRate * .18))
                val fadeOut = min(1.0, (totalSamples - i) / (sampleRate * .35))
                val envelope = min(fadeIn, fadeOut)
                val value = (
                    0.16 * sin(2.0 * PI * spec.frequency * t) +
                        0.055 * sin(2.0 * PI * spec.frequency * 1.5 * t) +
                        0.035 * sin(2.0 * PI * spec.frequency * 2.0 * t)
                    ) * envelope
                buffer.putShort((value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
            }
            file.writeBytes(buffer.array())
        }
        file
    }
}
