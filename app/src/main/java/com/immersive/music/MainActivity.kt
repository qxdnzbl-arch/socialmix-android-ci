package com.immersive.music

import android.app.Activity
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val PageBackground = Color(0xFFF7F7F4)
private val MainText = Color(0xFF161714)
private val SubText = Color(0xFF92948E)

@OptIn(ExperimentalMaterial3Api::class)
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
    var durationMs by remember { mutableLongStateOf(tracks.first().durationMs) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }

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
                durationMs = max(1, it.duration).toLong()
                if (playIntent) runCatching { it.start() }
            }
            player.setOnCompletionListener {
                playIntent = false
                positionMs = durationMs
            }
            player.setOnErrorListener { _, _, _ ->
                playIntent = false
                true
            }
            player.prepareAsync()
        }.onFailure {
            playIntent = false
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
        if (playIntent) {
            runCatching { player.pause() }
            playIntent = false
        } else {
            playIntent = true
            runCatching { player.start() }
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
            savedUris.mapNotNull { extractTrack(context, Uri.parse(it)) }
        }
        imported.forEach { if (tracks.none { t -> t.id == it.id }) tracks.add(it) }
        playTrack(0, false)
    }

    LaunchedEffect(playIntent, mediaPlayer) {
        while (isActive) {
            if (playIntent) {
                mediaPlayer?.let { player -> runCatching { positionMs = player.currentPosition.toLong() } }
            }
            delay(180)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            stored.add(uri.toString())
        }
        prefs.edit().putStringSet("imported_uris", stored).apply()
        scope.launch {
            val imported = withContext(Dispatchers.IO) { uris.mapNotNull { extractTrack(context, it) } }
            imported.forEach { if (tracks.none { t -> t.id == it.id }) tracks.add(it) }
        }
    }

    val currentTrack = tracks.getOrElse(currentIndex) { tracks.first() }
    val animatedBackground by animateColorAsState(
        targetValue = currentTrack.theme,
        animationSpec = tween(850),
        label = "trackBackground"
    )

    LaunchedEffect(page) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = page != AppPage.HOME
        controller.isAppearanceLightNavigationBars = page != AppPage.HOME
    }

    Box(Modifier.fillMaxSize().background(if (page == AppPage.HOME) animatedBackground else PageBackground)) {
        when (page) {
            AppPage.HOME -> HomeScreen(
                track = currentTrack,
                background = animatedBackground,
                isPlaying = playIntent,
                positionMs = positionMs,
                durationMs = durationMs,
                isFavorite = favoriteIds.contains(currentTrack.id),
                onToggleFavorite = { toggleFavorite(currentTrack.id) },
                onPlayPause = ::togglePlayback,
                onPrevious = { playTrack(currentIndex - 1, playIntent) },
                onNext = { playTrack(currentIndex + 1, playIntent) },
                onSeek = { target ->
                    positionMs = target
                    mediaPlayer?.runCatching { seekTo(target.toInt()) }
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
                onImport = { importer.launch(arrayOf("audio/*")) },
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
                onBack = { page = AppPage.HOME },
                onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },
            )
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = Color(0xFFF9F9F6),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("播放列表", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                tracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        active = index == currentIndex,
                        onClick = { playTrack(index, true); showQueue = false },
                    )
                }
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp))
            }
        }
    }

    menuTrack?.let { selected ->
        TrackActionSheet(
            track = selected,
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
}

@Composable
private fun HomeScreen(
    track: Track,
    background: Color,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSearch: () -> Unit,
    onQueue: () -> Unit,
    onLibrary: () -> Unit,
) {
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val start = rotation.value
                rotation.animateTo(start + 360f, tween(19_000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        } else rotation.stop()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    background.mix(Color.White, .035f),
                    background,
                    background.mix(Color.Black, .20f),
                )
            )
        )
    ) {
        val compact = maxHeight < 690.dp
        val discSize = (maxWidth * .79f).coerceAtMost(if (compact) 258.dp else 286.dp)

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = status)
                .padding(bottom = nav + 62.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(if (compact) 50.dp else 58.dp)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "心动",
                        color = Color.White.copy(alpha = .97f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.width(28.dp).height(1.5.dp).background(Color.White.copy(alpha = .88f), CircleShape))
                }
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = "搜索" },
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = .96f), modifier = Modifier.size(27.dp))
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(discSize + 20.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = .075f), Color.Transparent),
                                radius = 500f,
                            ),
                            CircleShape,
                        )
                )
                VinylDisc(track, rotation.value, Modifier.size(discSize))
                ToneArm(
                    Modifier
                        .size(width = discSize * .58f, height = discSize * .45f)
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 2.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = if (compact) 5.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Color.White.copy(alpha = .95f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = .61f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.semantics { contentDescription = "收藏" }) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = Color.White.copy(alpha = .87f),
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                IconButton(onClick = onQueue, modifier = Modifier.semantics { contentDescription = "播放列表" }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = .87f), modifier = Modifier.size(27.dp))
                }
            }

            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            ThinSeekBar(positionMs, durationMs, Color.White, onSeek)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(positionMs), color = Color.White.copy(alpha = .39f), fontSize = 11.sp)
                Text(
                    "极高音质",
                    color = Color.White.copy(alpha = .46f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(formatTime(durationMs), color = Color.White.copy(alpha = .39f), fontSize = 11.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = if (compact) 3.dp else 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp).semantics { contentDescription = "上一首" }) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White.copy(alpha = .90f), modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(54.dp).semantics { contentDescription = if (isPlaying) "暂停" else "播放" },
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White.copy(alpha = .90f),
                        modifier = Modifier.size(41.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp).semantics { contentDescription = "下一首" }) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White.copy(alpha = .90f), modifier = Modifier.size(32.dp))
                }
            }
        }

        HomeBottomNav(onLibrary, Modifier.align(Alignment.BottomCenter).padding(bottom = nav))
    }
}

@Composable
private fun ThinSeekBar(positionMs: Long, durationMs: Long, color: Color, onSeek: (Long) -> Unit) {
    val progress = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Canvas(
        Modifier.fillMaxWidth().height(18.dp).pointerInput(durationMs) {
            detectTapGestures { offset ->
                if (durationMs > 0) onSeek((durationMs * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
            }
        }
    ) {
        val y = size.height / 2f
        val x = size.width * progress
        drawLine(color.copy(alpha = .18f), Offset(0f, y), Offset(size.width, y), 2f, StrokeCap.Round)
        drawLine(color.copy(alpha = .72f), Offset(0f, y), Offset(x, y), 2f, StrokeCap.Round)
        drawCircle(color.copy(alpha = .92f), radius = 4.5f, center = Offset(x, y))
    }
}

@Composable
private fun VinylDisc(track: Track, rotation: Float, modifier: Modifier = Modifier) {
    val bitmap = remember(track.id, track.coverBytes) {
        track.coverBytes?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    BoxWithConstraints(
        modifier
            .graphicsLayer { rotationZ = rotation }
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .22f), spotColor = Color.Black.copy(alpha = .22f))
            .clip(CircleShape)
            .background(Color(0xFF111210)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            repeat(30) { i ->
                val rr = r * (.54f + i * .014f)
                drawCircle(
                    if (i % 2 == 0) Color.White.copy(alpha = .018f) else Color.Black.copy(alpha = .20f),
                    rr,
                    style = Stroke(width = 1f),
                )
            }
            drawCircle(Color.White.copy(alpha = .04f), r * .965f, style = Stroke(width = 1.5f))
        }
        Box(Modifier.size(maxWidth * .58f).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else DemoArtwork(track)
        }
        Box(Modifier.size(15.dp).clip(CircleShape).background(Color(0xFFF0F0EA)))
        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF787A74)))
    }
}

@Composable
private fun ToneArm(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pivot = Offset(size.width * .46f, size.height * .10f)
        val bend = Offset(size.width * .56f, size.height * .54f)
        val tip = Offset(size.width * .87f, size.height * .86f)
        drawCircle(Color.White.copy(alpha = .20f), 13f, pivot)
        drawCircle(Color.White.copy(alpha = .93f), 6f, pivot)
        drawLine(Color.White.copy(alpha = .91f), pivot, bend, 6f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .91f), bend, tip, 6f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .88f), tip, Offset(size.width * .94f, size.height * .93f), 10f, StrokeCap.Round)
    }
}

@Composable
private fun DemoArtwork(track: Track) {
    val index = track.id.substringAfter("demo:", "0").toIntOrNull() ?: 0
    Box(
        Modifier.fillMaxSize().background(
            when (index % 3) {
                0 -> Brush.verticalGradient(listOf(Color(0xFFE7E1C7), Color(0xFF8E9E61), Color(0xFF516B2A)))
                1 -> Brush.verticalGradient(listOf(Color(0xFFC7D8E7), Color(0xFF7894AD), Color(0xFF40556C)))
                else -> Brush.verticalGradient(listOf(Color(0xFFE4D2D4), Color(0xFF9A7075), Color(0xFF624147)))
            }
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (index % 3) {
                0 -> {
                    drawCircle(Color(0xFFF7F0D9).copy(alpha = .85f), size.minDimension * .20f, Offset(size.width * .64f, size.height * .30f))
                    drawCircle(Color(0xFF5A7134).copy(alpha = .74f), size.minDimension * .42f, Offset(size.width * .18f, size.height * .94f))
                    drawCircle(Color(0xFF78924B).copy(alpha = .64f), size.minDimension * .34f, Offset(size.width * .82f, size.height * .94f))
                    drawLine(Color.White.copy(alpha = .28f), Offset(size.width * .16f, size.height * .70f), Offset(size.width * .82f, size.height * .48f), 2f)
                }
                1 -> {
                    drawCircle(Color.White.copy(alpha = .75f), size.minDimension * .16f, Offset(size.width * .68f, size.height * .28f))
                    repeat(4) { i ->
                        val y = size.height * (.60f + i * .08f)
                        drawLine(Color.White.copy(alpha = .14f + i * .03f), Offset(size.width * .12f, y), Offset(size.width * .88f, y), 2f)
                    }
                }
                else -> {
                    val center = Offset(size.width * .52f, size.height * .48f)
                    repeat(6) { i ->
                        val angle = i * PI / 3.0
                        val dx = (size.minDimension * .20f * kotlin.math.cos(angle)).toFloat()
                        val dy = (size.minDimension * .20f * kotlin.math.sin(angle)).toFloat()
                        drawCircle(Color(0xFFF0D7D8).copy(alpha = .30f), size.minDimension * .15f, Offset(center.x + dx, center.y + dy))
                    }
                    drawCircle(Color(0xFFF3E3D8).copy(alpha = .78f), size.minDimension * .09f, center)
                }
            }
        }
    }
}

@Composable
private fun ArtworkSquare(track: Track, size: Dp) {
    val bitmap = remember(track.id, track.coverBytes) {
        track.coverBytes?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(9.dp))) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else DemoArtwork(track)
    }
}

@Composable
private fun HomeBottomNav(onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(62.dp).background(Color.Black.copy(alpha = .055f)).padding(horizontal = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("首页", color = Color.White.copy(alpha = .95f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "音乐库",
            color = Color.White.copy(alpha = .52f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onLibrary).padding(10.dp),
        )
    }
}

@Composable
private fun LibraryBottomNav(onHome: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(62.dp).background(PageBackground.copy(alpha = .96f)).padding(horizontal = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "首页",
            color = Color(0xFF989A94),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onHome).padding(10.dp),
        )
        Text("音乐库", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    favoriteIds: List<String>,
    recentIds: List<String>,
    currentTrack: Track,
    isPlaying: Boolean,
    onImport: () -> Unit,
    onTrack: (Track) -> Unit,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
    onHome: () -> Unit,
    onOpenFavorites: () -> Unit,
    onMore: (Track) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val favorites = tracks.filter { favoriteIds.contains(it.id) }
    val recent = recentIds.mapNotNull { id -> tracks.find { it.id == id } }.distinctBy { it.id }
    val local = tracks.filterNot { it.id.startsWith("demo:") }

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        Column(Modifier.fillMaxSize().padding(top = top).padding(bottom = nav + 62.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("音乐库", color = MainText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onImport, modifier = Modifier.semantics { contentDescription = "导入本地音乐" }) {
                    Icon(Icons.Rounded.UploadFile, null, tint = MainText, modifier = Modifier.size(22.dp))
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 94.dp),
            ) {
                item {
                    FavoriteCard(favorites.size, onOpenFavorites)
                }
                item { SectionTitle("最近播放", recent.size) }
                if (recent.isEmpty()) item { SubtleEmpty("播放过的歌会出现在这里") }
                else items(recent.take(8), key = { "recent-${it.id}" }) {
                    TrackRow(
                        track = it,
                        active = it.id == currentTrack.id,
                        onClick = { onTrack(it) },
                        onMore = { onMore(it) },
                    )
                }

                item { SectionTitle("本地音乐", local.size) }
                if (local.isEmpty()) item { SubtleEmpty("点右上角导入手机里的音频") }
                else items(local, key = { it.id }) {
                    TrackRow(
                        track = it,
                        active = it.id == currentTrack.id,
                        onClick = { onTrack(it) },
                        onMore = { onMore(it) },
                    )
                }
            }
        }

        MiniPlayer(
            track = currentTrack,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onQueue = onQueue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = nav + 65.dp),
        )
        LibraryBottomNav(onHome, Modifier.align(Alignment.BottomCenter).padding(bottom = nav))
    }
}

@Composable
private fun FavoriteCard(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .88f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "打开我喜欢的音乐" }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFCA6570).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFC15E69), modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("我喜欢的音乐", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(count.toString(), color = Color(0xFF999B95), fontSize = 13.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 17.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = MainText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(7.dp))
        Text(count.toString(), color = Color(0xFF9C9E98), fontSize = 12.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun SubtleEmpty(text: String) {
    Text(text, color = Color(0xFF9C9E98), fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp))
}

@Composable
private fun FavoritesScreen(
    tracks: List<Track>,
    currentTrack: Track,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onMore: (Track) -> Unit,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        Column(Modifier.fillMaxSize().padding(top = top).padding(bottom = nav + 76.dp)) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回音乐库" }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MainText, modifier = Modifier.size(25.dp))
                }
                Text("我喜欢的音乐", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            if (tracks.isEmpty()) {
                Text("还没有收藏歌曲", color = SubText, fontSize = 13.sp, modifier = Modifier.padding(start = 22.dp, top = 22.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                    items(tracks, key = { it.id }) {
                        TrackRow(
                            track = it,
                            active = it.id == currentTrack.id,
                            onClick = { onTrack(it) },
                            onMore = { onMore(it) },
                        )
                    }
                }
            }
        }
        MiniPlayer(
            currentTrack,
            isPlaying,
            onPlayPause,
            onQueue,
            Modifier.align(Alignment.BottomCenter).padding(bottom = nav + 10.dp),
        )
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(27.dp), ambientColor = Color.Black.copy(alpha = .07f), spotColor = Color.Black.copy(alpha = .07f))
            .clip(RoundedCornerShape(27.dp))
            .background(Color.White.copy(alpha = .90f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkSquare(track, 41.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = MainText, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(track.artist, color = SubText, fontSize = 12.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.semantics { contentDescription = if (isPlaying) "迷你播放器暂停" else "迷你播放器播放" }) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MainText, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onQueue) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MainText, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun SearchScreen(tracks: List<Track>, onBack: () -> Unit, onTrack: (Track) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks.size) {
        if (query.isBlank()) emptyList()
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(PageBackground).padding(top = top)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 9.dp, end = 15.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回" }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MainText, modifier = Modifier.size(25.dp))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索歌曲或歌手", color = Color(0xFF999B96), fontSize = 15.5.sp, fontWeight = FontWeight.Normal) },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).semantics { contentDescription = "搜索输入框" },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color(0xFF777A74), modifier = Modifier.size(23.dp)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = .92f),
                    unfocusedContainerColor = Color.White.copy(alpha = .92f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MainText,
                ),
            )
        }

        if (query.isBlank()) {
            Text(
                "只搜索你的歌曲",
                color = Color(0xFF9A9C96),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 22.dp, top = 13.dp),
            )
        } else if (results.isEmpty()) {
            SubtleEmpty("没有匹配的歌曲")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                items(results, key = { it.id }) {
                    TrackRow(track = it, active = false, onClick = { onTrack(it) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkSquare(track, 44.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) track.theme.mix(Color.Black, .12f) else MainText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(track.artist, color = SubText, fontSize = 12.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            IconButton(
                onClick = onMore,
                modifier = Modifier.size(42.dp).semantics { contentDescription = "更多:${track.title}" },
            ) {
                Icon(Icons.Rounded.MoreHoriz, null, tint = Color(0xFF9B9D97), modifier = Modifier.size(21.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionSheet(
    track: Track,
    favorite: Boolean,
    inRecent: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveRecent: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF9F9F6),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("歌曲选项", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(track.title, color = SubText, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            ActionRow(if (favorite) "取消收藏" else "收藏", onToggleFavorite)
            if (inRecent) ActionRow("从最近播放移除", onRemoveRecent)
            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp))
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MainText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
    )
}

private fun Color.mix(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - a) + other.red * a,
        green = green * (1f - a) + other.green * a,
        blue = blue * (1f - a) + other.blue * a,
        alpha = 1f,
    )
}

private fun formatTime(ms: Long): String {
    val total = max(0L, ms) / 1000L
    return "%d:%02d".format(total / 60, total % 60)
}

private fun extractTrack(context: Context, uri: Uri): Track? {
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

private fun themeFromArtwork(bytes: ByteArray?, seed: String): Color {
    if (bytes != null) {
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                val small = android.graphics.Bitmap.createScaledBitmap(bmp, 20, 20, true)
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
                    val rr = ((r / count) * .62).toInt().coerceIn(44, 150)
                    val gg = ((g / count) * .62).toInt().coerceIn(44, 150)
                    val bb = ((b / count) * .62).toInt().coerceIn(44, 150)
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
