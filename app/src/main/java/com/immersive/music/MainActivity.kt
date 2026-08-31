package com.immersive.music

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            MaterialTheme {
                MusicApp()
            }
        }
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

enum class AppPage { HOME, LIBRARY, SEARCH }

private data class DemoSpec(val title: String, val artist: String, val frequency: Double)

private val DemoTracks = listOf(
    DemoSpec("First Light", "Mori", 220.0),
    DemoSpec("Blue Hour", "Luna", 277.18),
    DemoSpec("Night Bloom", "Aster", 329.63),
)

private val DemoColors = listOf(
    Color(0xFF486515),
    Color(0xFF4A586D),
    Color(0xFF6A433C),
)

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
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(tracks.first().durationMs) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showQueue by remember { mutableStateOf(false) }

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

    fun playTrack(index: Int, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        val safeIndex = (index % tracks.size + tracks.size) % tracks.size
        val track = tracks[safeIndex]
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        positionMs = 0L
        durationMs = max(1L, track.durationMs)
        currentIndex = safeIndex
        isPlaying = false
        markRecent(track.id)

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setDataSource(context, Uri.parse(track.uri))
            player.setOnPreparedListener {
                durationMs = max(1, it.duration).toLong()
                if (autoPlay) {
                    it.start()
                    isPlaying = true
                }
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            player.setOnErrorListener { _, _, _ ->
                isPlaying = false
                true
            }
            player.prepareAsync()
        }.onFailure {
            isPlaying = false
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
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
        playTrack(0, autoPlay = false)
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isActive) {
            if (isPlaying) {
                mediaPlayer?.let { player ->
                    runCatching { positionMs = player.currentPosition.toLong() }
                }
            }
            delay(250)
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
        val existingUris = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            existingUris.add(uri.toString())
        }
        prefs.edit().putStringSet("imported_uris", existingUris).apply()
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (page == AppPage.HOME) animatedBackground else Color(0xFFF4F5F3),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (page != AppPage.SEARCH) {
                BottomNav(
                    page = page,
                    dark = page == AppPage.HOME,
                    onHome = { page = AppPage.HOME },
                    onLibrary = { page = AppPage.LIBRARY }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (page) {
                AppPage.HOME -> HomeScreen(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isFavorite = favoriteIds.contains(currentTrack.id),
                    onToggleFavorite = {
                        if (favoriteIds.contains(currentTrack.id)) favoriteIds.remove(currentTrack.id)
                        else favoriteIds.add(currentTrack.id)
                        saveFavorites()
                    },
                    onPlayPause = {
                        val player = mediaPlayer
                        if (player == null) {
                            playTrack(currentIndex, true)
                        } else if (isPlaying) {
                            runCatching { player.pause() }
                            isPlaying = false
                        } else {
                            runCatching { player.start() }
                            isPlaying = true
                        }
                    },
                    onPrevious = { playTrack(currentIndex - 1, true) },
                    onNext = { playTrack(currentIndex + 1, true) },
                    onSeek = { target ->
                        positionMs = target
                        mediaPlayer?.runCatching { seekTo(target.toInt()) }
                    },
                    onSearch = { page = AppPage.SEARCH },
                    onQueue = { showQueue = true }
                )
                AppPage.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    favoriteIds = favoriteIds,
                    recentIds = recentIds,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onImport = { importer.launch(arrayOf("audio/*")) },
                    onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },
                    onPlayPause = {
                        val player = mediaPlayer
                        if (player == null) playTrack(currentIndex, true)
                        else if (isPlaying) { runCatching { player.pause() }; isPlaying = false }
                        else { runCatching { player.start() }; isPlaying = true }
                    },
                    onQueue = { showQueue = true }
                )
                AppPage.SEARCH -> SearchScreen(
                    tracks = tracks,
                    onBack = { page = AppPage.HOME },
                    onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME }
                )
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Color(0xFFF7F7F4)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("播放列表", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF151613))
                Spacer(Modifier.height(12.dp))
                tracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        active = index == currentIndex,
                        onClick = { playTrack(index, true); showQueue = false }
                    )
                }
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    track: Track,
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
) {
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val start = rotation.value
                rotation.animateTo(start + 360f, tween(18_000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        } else {
            rotation.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusPadding)
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(66.dp)) {
            Text(
                "心动",
                color = Color.White.copy(alpha = .96f),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onSearch,
                modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = "搜索" }
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
            VinylDisc(
                track = track,
                rotation = rotation.value,
                modifier = Modifier.padding(top = 72.dp).size(318.dp)
            )
            ToneArm(modifier = Modifier.size(width = 220.dp, height = 160.dp).align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(30.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    track.artist,
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.semantics { contentDescription = "收藏" }) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .92f),
                    modifier = Modifier.size(31.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onQueue, modifier = Modifier.semantics { contentDescription = "播放列表" }) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = .92f), modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Slider(
            value = min(positionMs.toFloat(), durationMs.toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..max(1f, durationMs.toFloat()),
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = .2f)
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), color = Color.White.copy(alpha = .46f), fontSize = 13.sp)
            Text("高音质", color = Color.White.copy(alpha = .54f), fontSize = 13.sp)
            Text(formatTime(durationMs), color = Color.White.copy(alpha = .46f), fontSize = 13.sp)
        }

        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 66.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(62.dp).semantics { contentDescription = "上一首" }) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(74.dp).semantics { contentDescription = if (isPlaying) "暂停" else "播放" }) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(62.dp).semantics { contentDescription = "下一首" }) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}

@Composable
private fun VinylDisc(track: Track, rotation: Float, modifier: Modifier = Modifier) {
    val coverBitmap = remember(track.id, track.coverBytes) {
        track.coverBytes?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .35f))
            .clip(CircleShape)
            .background(Color(0xFF0E0F0D)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            repeat(18) { i ->
                val r = maxR * (.58f + i * .022f)
                drawCircle(Color.White.copy(alpha = .028f + (i % 3) * .008f), r, style = Stroke(width = 1.2f))
            }
            drawCircle(Color.Black.copy(alpha = .45f), maxR * .98f, style = Stroke(width = 3f))
        }
        Box(
            modifier = Modifier
                .size(204.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .06f)),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(coverBitmap, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                DemoCover(track)
            }
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFE9E9E2)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8B8D86)))
    }
}

@Composable
private fun DemoCover(track: Track) {
    val hueA = track.theme.copy(alpha = 1f)
    val hueB = Color.White.copy(alpha = .72f)
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(hueB, hueA, Color(0xFF24251F)), radius = 380f)
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .17f), radius = size.minDimension * .23f, center = Offset(size.width * .36f, size.height * .33f))
            drawCircle(Color.Black.copy(alpha = .12f), radius = size.minDimension * .18f, center = Offset(size.width * .66f, size.height * .62f))
            drawLine(Color.White.copy(alpha = .24f), Offset(size.width*.2f,size.height*.78f), Offset(size.width*.78f,size.height*.2f), 5f, StrokeCap.Round)
        }
        Text(track.title.take(1), color = Color.White.copy(alpha = .85f), fontSize = 42.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun ToneArm(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pivot = Offset(size.width * .55f, size.height * .14f)
        drawCircle(Color.White.copy(alpha = .25f), radius = 22f, center = pivot)
        drawCircle(Color.White, radius = 11f, center = pivot)
        val bend = Offset(size.width * .62f, size.height * .55f)
        val tip = Offset(size.width * .88f, size.height * .88f)
        drawLine(Color.White, pivot, bend, strokeWidth = 12f, cap = StrokeCap.Round)
        drawLine(Color.White, bend, tip, strokeWidth = 12f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE8E8E2), tip, Offset(size.width * .94f, size.height * .94f), strokeWidth = 20f, cap = StrokeCap.Round)
    }
}

@Composable
private fun BottomNav(page: AppPage, dark: Boolean, onHome: () -> Unit, onLibrary: () -> Unit) {
    val bg = if (dark) Color.Black.copy(alpha = .13f) else Color(0xFFF4F5F3)
    val active = if (dark) Color.White else Color(0xFF131512)
    val inactive = if (dark) Color.White.copy(alpha = .52f) else Color(0xFF8A8D87)
    NavigationBar(
        containerColor = bg,
        tonalElevation = 0.dp,
        modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        NavigationBarItem(
            selected = page == AppPage.HOME,
            onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, null, modifier = Modifier.size(25.dp)) },
            label = { Text("首页", fontWeight = if (page == AppPage.HOME) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = active, selectedTextColor = active, unselectedIconColor = inactive, unselectedTextColor = inactive, indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = page == AppPage.LIBRARY,
            onClick = onLibrary,
            icon = { Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(25.dp)) },
            label = { Text("音乐库", fontWeight = if (page == AppPage.LIBRARY) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = active, selectedTextColor = active, unselectedIconColor = inactive, unselectedTextColor = inactive, indicatorColor = Color.Transparent)
        )
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
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val favorites = tracks.filter { favoriteIds.contains(it.id) }
    val recent = recentIds.mapNotNull { id -> tracks.find { it.id == id } }
    val local = tracks.filterNot { it.id.startsWith("demo:") }

    Column(Modifier.fillMaxSize().padding(top = top)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("音乐库", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF121410), modifier = Modifier.weight(1f))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D19), contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                modifier = Modifier.semantics { contentDescription = "导入本地音乐" }
            ) {
                Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入")
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 118.dp)) {
            item { LibraryHeader("我喜欢的音乐", favorites.size) }
            if (favorites.isEmpty()) item { EmptyHint("收藏过的歌会出现在这里") }
            else items(favorites, key = { it.id }) { TrackRow(it, active = it.id == currentTrack.id, onClick = { onTrack(it) }) }

            item { LibraryHeader("最近播放", recent.size) }
            if (recent.isEmpty()) item { EmptyHint("播放过的歌会出现在这里") }
            else items(recent.take(8), key = { "recent-${it.id}" }) { TrackRow(it, active = it.id == currentTrack.id, onClick = { onTrack(it) }) }

            item { LibraryHeader("本地音乐", local.size) }
            if (local.isEmpty()) item { EmptyHint("点右上角“导入”，选择手机里的音频") }
            else items(local, key = { it.id }) { TrackRow(it, active = it.id == currentTrack.id, onClick = { onTrack(it) }) }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        MiniPlayer(currentTrack, isPlaying, onPlayPause, onQueue)
    }
}

@Composable
private fun LibraryHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF171916))
        Spacer(Modifier.width(7.dp))
        Text(count.toString(), fontSize = 13.sp, color = Color(0xFF939690))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Color(0xFF989B96), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
}

@Composable
private fun MiniPlayer(track: Track, isPlaying: Boolean, onPlayPause: () -> Unit, onQueue: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = .08f))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = .96f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(track.theme), contentAlignment = Alignment.Center) {
            Text(track.title.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color(0xFF191B18), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color(0xFF8D908A), fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF1B1D19)) }
        IconButton(onClick = onQueue) { Icon(Icons.Rounded.QueueMusic, null, tint = Color(0xFF1B1D19)) }
    }
}

@Composable
private fun SearchScreen(tracks: List<Track>, onBack: () -> Unit, onTrack: (Track) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks.size) {
        if (query.isBlank()) emptyList() else tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F3)).padding(top = top)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回" }) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color(0xFF171916))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索歌曲或歌手") },
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).semantics { contentDescription = "搜索输入框" },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
        }

        if (query.isBlank()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp)) {
                Text("找你想听的", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF151714))
                Spacer(Modifier.height(8.dp))
                Text("这里只搜索歌曲和歌手，没有榜单、社区和运营推荐。", color = Color(0xFF858983), fontSize = 14.sp)
            }
        } else if (results.isEmpty()) {
            EmptyHint("没有匹配的歌曲")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp)) {
                items(results, key = { it.id }) { TrackRow(it, active = false, onClick = { onTrack(it) }) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(track.theme), contentAlignment = Alignment.Center) {
            Text(track.title.take(1), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (active) track.theme else Color(0xFF171916), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(track.artist, color = Color(0xFF90938E), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.MoreHoriz, null, tint = Color(0xFF9A9D97))
    }
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
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                name.substringBeforeLast('.')
            } else null
        }
    }.getOrNull()
}

private fun themeFromArtwork(bytes: ByteArray?, seed: String): Color {
    if (bytes != null) {
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                val small = android.graphics.Bitmap.createScaledBitmap(bmp, 20, 20, true)
                var r = 0L; var g = 0L; var b = 0L; var count = 0L
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
                    val rr = ((r / count) * .58).toInt().coerceIn(38, 140)
                    val gg = ((g / count) * .58).toInt().coerceIn(38, 140)
                    val bb = ((b / count) * .58).toInt().coerceIn(38, 140)
                    return Color(android.graphics.Color.rgb(rr, gg, bb))
                }
            }
        }
    }
    val palette = listOf(0xFF4C5E39, 0xFF59606D, 0xFF694945, 0xFF355D62, 0xFF665A36, 0xFF554C67)
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
