package com.immersive.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.PI

private val MainText = Color(0xFF171714)
private val SubText = Color(0xFF92938E)
private val AccentRed = Color(0xFFFF3B48)
private val CloudRed = Color(0xFFD84B57)

@Composable
private fun rememberCoverBitmap(track: Track): ImageBitmap? {
    return remember(track.id, track.coverBytes) {
        track.coverBytes?.let { bytes ->
            runCatching {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
}

@Composable
private fun PlayerBackdrop(track: Track, background: Color) {
    val cover = rememberCoverBitmap(track)
    Box(Modifier.fillMaxSize().background(background)) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(52.dp)
                    .alpha(.72f),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            background.mix(Color.White, .08f),
                            background,
                            background.mix(Color.Black, .18f),
                        )
                    )
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = .14f),
                        Color.Black.copy(alpha = .20f),
                        Color.Black.copy(alpha = .34f),
                        Color.Black.copy(alpha = .46f),
                    )
                )
            )
        )
    }
}

@Composable
private fun LightBackdrop(track: Track) {
    val cover = rememberCoverBitmap(track)
    val tint by animateColorAsState(
        targetValue = track.theme.mix(Color.White, .88f),
        animationSpec = tween(700),
        label = "libraryTint",
    )
    Box(Modifier.fillMaxSize().background(tint)) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.45f
                        scaleY = 1.45f
                    }
                    .blur(68.dp)
                    .alpha(.14f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = .92f),
                        Color.White.copy(alpha = .86f),
                        Color.White.copy(alpha = .90f),
                    )
                )
            )
        )
    }
}

@Composable
fun HomeScreen(
    track: Track,
    background: Color,
    isPlaying: Boolean,
    needleOnDisc: Boolean,
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
                rotation.animateTo(start + 360f, tween(20_500, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        } else {
            rotation.stop()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        PlayerBackdrop(track, background)

        val compact = maxHeight < 690.dp
        val discSize = (maxWidth * .745f).coerceAtMost(if (compact) 248.dp else 270.dp)

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = status)
                .padding(bottom = nav + 60.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(if (compact) 47.dp else 54.dp)) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "心动",
                        color = Color.White.copy(alpha = .94f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .width(31.dp)
                            .height(1.4.dp)
                            .background(Color.White.copy(alpha = .88f), CircleShape)
                    )
                }
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .semantics { contentDescription = "搜索" },
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .93f),
                        modifier = Modifier.size(25.dp),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(discSize + 20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .035f))
                        .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                )
                VinylDisc(track, rotation.value, Modifier.size(discSize))
                ToneArm(
                    onDisc = needleOnDisc,
                    modifier = Modifier
                        .size(width = discSize * .59f, height = discSize * .47f)
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 2.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Color.White.copy(alpha = .93f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = .60f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics {
                            contentDescription = if (isFavorite) "取消收藏" else "收藏"
                        },
                ) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) AccentRed else Color.White.copy(alpha = .88f),
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onQueue,
                    modifier = Modifier.size(44.dp).semantics { contentDescription = "播放列表" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .88f),
                        modifier = Modifier.size(27.dp),
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 3.dp else 7.dp))
            NetEaseSeekBar(positionMs, durationMs, Color.White, onSeek)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(positionMs), color = Color.White.copy(alpha = .41f), fontSize = 11.sp)
                Text(
                    "极高音质",
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(formatTime(durationMs), color = Color.White.copy(alpha = .41f), fontSize = 11.sp)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp, vertical = if (compact) 3.dp else 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "上一首" },
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .88f),
                        modifier = Modifier.size(31.dp),
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(61.dp)
                        .semantics { contentDescription = if (isPlaying) "暂停" else "播放" },
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .92f),
                        modifier = Modifier.size(if (isPlaying) 48.dp else 51.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "下一首" },
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .88f),
                        modifier = Modifier.size(31.dp),
                    )
                }
            }
        }

        HomeBottomNav(onLibrary, Modifier.align(Alignment.BottomCenter).padding(bottom = nav))
    }
}

@Composable
private fun NetEaseSeekBar(
    positionMs: Long,
    durationMs: Long,
    color: Color,
    onSeek: (Long) -> Unit,
) {
    val progress =
        if (durationMs <= 0) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        onSeek((durationMs * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            }
    ) {
        val y = size.height / 2f
        val x = size.width * progress
        val thin = 1.15.dp.toPx()
        drawLine(
            color.copy(alpha = .19f),
            Offset(0f, y),
            Offset(size.width, y),
            thin,
            StrokeCap.Round,
        )
        drawLine(
            color.copy(alpha = .72f),
            Offset(0f, y),
            Offset(x, y),
            thin,
            StrokeCap.Round,
        )
        drawCircle(
            color.copy(alpha = .96f),
            radius = 4.6.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

@Composable
private fun VinylDisc(track: Track, rotation: Float, modifier: Modifier = Modifier) {
    val bitmap = rememberCoverBitmap(track)
    BoxWithConstraints(
        modifier
            .graphicsLayer { rotationZ = rotation }
            .shadow(
                elevation = 13.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = .30f),
                spotColor = Color.Black.copy(alpha = .30f),
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF282927),
                        Color(0xFF101110),
                        Color(0xFF060706),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0A0B0A), r)
            repeat(48) { i ->
                val rr = r * (.50f + i * .0102f)
                drawCircle(
                    color = if (i % 3 == 0) {
                        Color.White.copy(alpha = .028f)
                    } else {
                        Color.Black.copy(alpha = .28f)
                    },
                    radius = rr,
                    style = Stroke(width = .52.dp.toPx()),
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = .055f),
                radius = r * .965f,
                style = Stroke(width = .9.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = .38f),
                radius = r * .76f,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawArc(
                color = Color.White.copy(alpha = .030f),
                startAngle = 205f,
                sweepAngle = 78f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .018f),
                startAngle = 32f,
                sweepAngle = 62f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Box(
            Modifier
                .size(maxWidth * .655f)
                .clip(CircleShape)
                .border(.7.dp, Color.Black.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                DemoArtwork(track)
            }
        }

        Box(
            Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8E7E2).copy(alpha = .96f))
        )
        Box(
            Modifier
                .size(4.4.dp)
                .clip(CircleShape)
                .background(Color(0xFF74756F))
        )
    }
}

@Composable
private fun ToneArm(onDisc: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(
        targetValue = if (onDisc) 0f else -17f,
        animationSpec = tween(430),
        label = "toneArm",
    )

    Canvas(
        modifier
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(.455f, .10f)
            }
            .semantics {
                contentDescription = if (onDisc) "唱针:唱片上" else "唱针:唱片外"
            }
    ) {
        val pivot = Offset(size.width * .455f, size.height * .10f)
        val bend = Offset(size.width * .55f, size.height * .55f)
        val tip = Offset(size.width * .86f, size.height * .86f)
        val end = Offset(size.width * .935f, size.height * .93f)

        drawCircle(Color.Black.copy(alpha = .10f), radius = 8.dp.toPx(), center = pivot)
        drawCircle(Color.White.copy(alpha = .25f), radius = 6.5.dp.toPx(), center = pivot)
        drawCircle(Color(0xFFF7F7F4), radius = 3.2.dp.toPx(), center = pivot)

        drawLine(
            Color.Black.copy(alpha = .12f),
            pivot + Offset(1.2.dp.toPx(), 1.3.dp.toPx()),
            bend + Offset(1.2.dp.toPx(), 1.3.dp.toPx()),
            3.8.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(Color(0xFFF5F5F1), pivot, bend, 3.2.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFFF5F5F1), bend, tip, 3.2.dp.toPx(), StrokeCap.Round)

        drawLine(
            Color(0xFFDCDDD8),
            tip,
            end,
            6.2.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            Color.White.copy(alpha = .92f),
            Offset(size.width * .905f, size.height * .90f),
            Offset(size.width * .95f, size.height * .945f),
            3.8.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
private fun DemoArtwork(track: Track) {
    val index = track.id.substringAfter("demo:", "0").toIntOrNull() ?: 0
    Box(
        Modifier.fillMaxSize().background(
            when (index % 3) {
                0 -> Brush.verticalGradient(
                    listOf(
                        Color(0xFFE7E1C7),
                        Color(0xFF8E9E61),
                        Color(0xFF516B2A),
                    )
                )
                1 -> Brush.verticalGradient(
                    listOf(
                        Color(0xFFC7D8E7),
                        Color(0xFF7894AD),
                        Color(0xFF40556C),
                    )
                )
                else -> Brush.verticalGradient(
                    listOf(
                        Color(0xFFE4D2D4),
                        Color(0xFF9A7075),
                        Color(0xFF624147),
                    )
                )
            }
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (index % 3) {
                0 -> {
                    drawCircle(
                        Color(0xFFF7F0D9).copy(alpha = .85f),
                        size.minDimension * .20f,
                        Offset(size.width * .64f, size.height * .30f),
                    )
                    drawCircle(
                        Color(0xFF5A7134).copy(alpha = .74f),
                        size.minDimension * .42f,
                        Offset(size.width * .18f, size.height * .94f),
                    )
                    drawCircle(
                        Color(0xFF78924B).copy(alpha = .64f),
                        size.minDimension * .34f,
                        Offset(size.width * .82f, size.height * .94f),
                    )
                    drawLine(
                        Color.White.copy(alpha = .28f),
                        Offset(size.width * .16f, size.height * .70f),
                        Offset(size.width * .82f, size.height * .48f),
                        2f,
                    )
                }

                1 -> {
                    drawCircle(
                        Color.White.copy(alpha = .75f),
                        size.minDimension * .16f,
                        Offset(size.width * .68f, size.height * .28f),
                    )
                    repeat(4) { i ->
                        val y = size.height * (.60f + i * .08f)
                        drawLine(
                            Color.White.copy(alpha = .14f + i * .03f),
                            Offset(size.width * .12f, y),
                            Offset(size.width * .88f, y),
                            2f,
                        )
                    }
                }

                else -> {
                    val center = Offset(size.width * .52f, size.height * .48f)
                    repeat(6) { i ->
                        val angle = i * PI / 3.0
                        val dx = (size.minDimension * .20f * kotlin.math.cos(angle)).toFloat()
                        val dy = (size.minDimension * .20f * kotlin.math.sin(angle)).toFloat()
                        drawCircle(
                            Color(0xFFF0D7D8).copy(alpha = .30f),
                            size.minDimension * .15f,
                            Offset(center.x + dx, center.y + dy),
                        )
                    }
                    drawCircle(
                        Color(0xFFF3E3D8).copy(alpha = .78f),
                        size.minDimension * .09f,
                        center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkSquare(track: Track, size: Dp) {
    val bitmap = rememberCoverBitmap(track)
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp))) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            DemoArtwork(track)
        }
    }
}

@Composable
private fun HomeBottomNav(onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black.copy(alpha = .045f))
            .padding(horizontal = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "首页",
            color = Color.White.copy(alpha = .93f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "音乐库",
            color = Color.White.copy(alpha = .50f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onLibrary).padding(10.dp),
        )
    }
}

@Composable
private fun LibraryBottomNav(onHome: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.White.copy(alpha = .48f))
            .padding(horizontal = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "首页",
            color = Color(0xFF9A9B96),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onHome).padding(10.dp),
        )
        Text(
            "音乐库",
            color = MainText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LibraryScreen(
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
    val local = tracks.filter { it.id.startsWith("local:") }

    Box(Modifier.fillMaxSize()) {
        LightBackdrop(currentTrack)

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = top)
                .padding(bottom = nav + 60.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "音乐库",
                    color = MainText,
                    fontSize = 20.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onImport,
                    modifier = Modifier
                        .size(43.dp)
                        .semantics { contentDescription = "导入本地音乐" },
                ) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = null,
                        tint = MainText.copy(alpha = .88f),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 92.dp),
            ) {
                item { FavoriteCard(favorites.size, onOpenFavorites) }
                item { SectionTitle("最近播放", recent.size) }
                if (recent.isEmpty()) {
                    item { SubtleEmpty("播放过的歌会出现在这里") }
                } else {
                    items(recent.take(8), key = { "recent-${it.id}" }) {
                        TrackRow(
                            track = it,
                            active = it.id == currentTrack.id,
                            onClick = { onTrack(it) },
                            onMore = { onMore(it) },
                        )
                    }
                }

                item { SectionTitle("本地音乐", local.size) }
                if (local.isEmpty()) {
                    item { SubtleEmpty("右上角可直接添加手机里的音乐") }
                } else {
                    items(local, key = { it.id }) {
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
            track = currentTrack,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onQueue = onQueue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = nav + 63.dp),
        )
        LibraryBottomNav(onHome, Modifier.align(Alignment.BottomCenter).padding(bottom = nav))
    }
}

@Composable
private fun FavoriteCard(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .56f))
            .border(.6.dp, Color.White.copy(alpha = .60f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "打开我喜欢的音乐" }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CloudRed.copy(alpha = .11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = CloudRed.copy(alpha = .88f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            "我喜欢的音乐",
            color = MainText,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            color = SubText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, end = 3.dp, top = 16.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            color = MainText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            count.toString(),
            color = Color(0xFF9D9E99),
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun SubtleEmpty(text: String) {
    Text(
        text,
        color = Color(0xFF999B96),
        fontSize = 12.5.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
    )
}

@Composable
fun FavoritesScreen(
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

    Box(Modifier.fillMaxSize()) {
        LightBackdrop(currentTrack)

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = top)
                .padding(bottom = nav + 74.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "返回音乐库" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MainText,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    "我喜欢的音乐",
                    color = MainText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (tracks.isEmpty()) {
                Text(
                    "还没有收藏歌曲",
                    color = SubText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 22.dp, top = 20.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp),
                ) {
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
            .shadow(
                5.dp,
                RoundedCornerShape(25.dp),
                ambientColor = Color.Black.copy(alpha = .055f),
                spotColor = Color.Black.copy(alpha = .055f),
            )
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = .70f))
            .border(.6.dp, Color.White.copy(alpha = .72f), RoundedCornerShape(25.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkSquare(track, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = MainText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = SubText,
                fontSize = 11.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(43.dp)
                .semantics {
                    contentDescription = if (isPlaying) "迷你播放器暂停" else "迷你播放器播放"
                },
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MainText,
                modifier = Modifier.size(if (isPlaying) 23.dp else 26.dp),
            )
        }
        IconButton(onClick = onQueue, modifier = Modifier.size(43.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MainText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun SearchScreen(
    tracks: List<Track>,
    currentTrack: Track,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { androidx.compose.runtime.mutableStateOf("") }
    val results = remember(query, tracks.size) {
        if (query.isBlank()) {
            emptyList()
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LightBackdrop(currentTrack)
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MainText,
                        modifier = Modifier.size(24.dp),
                    )
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "搜索歌曲或歌手",
                            color = Color(0xFF999B96),
                            fontSize = 14.5.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .semantics { contentDescription = "搜索输入框" },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF7C7E79),
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = .62f),
                        unfocusedContainerColor = Color.White.copy(alpha = .62f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MainText,
                    ),
                )
            }

            if (query.isBlank()) {
                Text(
                    "只搜索你的歌曲",
                    color = Color(0xFF999B96),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 22.dp, top = 12.dp),
                )
            } else if (results.isEmpty()) {
                SubtleEmpty("没有匹配的歌曲")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp),
                ) {
                    items(results, key = { it.id }) {
                        TrackRow(
                            track = it,
                            active = it.id == currentTrack.id,
                            onClick = { onTrack(it) },
                        )
                    }
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
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkSquare(track, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) CloudRed.copy(alpha = .88f) else MainText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                track.artist,
                color = SubText,
                fontSize = 11.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            IconButton(
                onClick = onMore,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "更多:${track.title}" },
            ) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = null,
                    tint = Color(0xFF9B9D98),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    tracks: List<Track>,
    currentIndex: Int,
    currentTrack: Track,
    onDismiss: () -> Unit,
    onTrack: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTrack.theme.mix(Color.White, .94f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text(
                "播放列表",
                color = MainText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(9.dp))
            tracks.forEachIndexed { index, track ->
                TrackRow(
                    track = track,
                    active = index == currentIndex,
                    onClick = { onTrack(index) },
                )
            }
            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    currentTrack: Track,
    favorite: Boolean,
    inRecent: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveRecent: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTrack.theme.mix(Color.White, .94f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("歌曲选项", color = MainText, fontSize = 16.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                track.title,
                color = SubText,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            ActionRow(if (favorite) "取消收藏" else "收藏", onToggleFavorite)
            if (inRecent) ActionRow("从最近播放移除", onRemoveRecent)
            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
                )
            )
        }
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MainText,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicSheet(
    currentTrack: Track,
    deviceAudio: List<DeviceAudio>,
    busy: Boolean,
    addedUris: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (DeviceAudio) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTrack.theme.mix(Color.White, .95f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.semantics { contentDescription = "手机音乐选择面板" },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp)
        ) {
            Text(
                "选择手机音乐",
                color = MainText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            when {
                busy -> {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(25.dp),
                            strokeWidth = 2.dp,
                            color = CloudRed,
                        )
                    }
                }

                deviceAudio.isEmpty() -> {
                    Text(
                        "没有扫描到手机音频",
                        color = SubText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        itemsIndexed(deviceAudio, key = { _, item -> item.id }) { _, item ->
                            val added = addedUris.contains(item.uri.toString())
                            DeviceAudioRow(
                                item = item,
                                added = added,
                                onClick = {
                                    if (!added) onAdd(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceAudioRow(
    item: DeviceAudio,
    added: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !added, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = .045f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFF777973),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = MainText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${item.artist}  ${formatTime(item.durationMs)}",
                color = SubText,
                fontSize = 11.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (added) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已添加:${item.title}",
                tint = CloudRed.copy(alpha = .82f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

fun Color.mix(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - a) + other.red * a,
        green = green * (1f - a) + other.green * a,
        blue = blue * (1f - a) + other.blue * a,
        alpha = 1f,
    )
}
