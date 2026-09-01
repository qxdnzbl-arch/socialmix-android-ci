from pathlib import Path
import re

ui = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = ui.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'{name}: expected {count}, replaced {n}')
    s = s2


# Compact custom search input and row overlay support.
for anchor, addition in [
    ('import androidx.compose.foundation.layout.fillMaxWidth\n', 'import androidx.compose.foundation.layout.matchParentSize\n'),
    ('import androidx.compose.foundation.shape.RoundedCornerShape\n', 'import androidx.compose.foundation.text.BasicTextField\n'),
    ('import androidx.compose.ui.draw.clip\n', 'import androidx.compose.ui.draw.clipToBounds\n'),
]:
    if addition not in s:
        if anchor not in s:
            raise SystemExit(f'import anchor missing: {anchor.strip()}')
        s = s.replace(anchor, anchor + addition, 1)

# Make the inline action easier to hit while keeping it visually just one centered word.
sub(
    r'''@Composable\nprivate fun InlineDeleteAction\(\n    description: String,\n    onClick: \(\) -> Unit,\n\) \{.*?\n\}\n\n(?=@Composable\nfun HomeScreen)''',
    '''@Composable
private fun InlineDeleteAction(
    description: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(76.dp)
            .height(42.dp)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "删除",
            color = CloudRed.copy(alpha = .92f),
            fontSize = 14.2.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

''',
    'larger centered delete hit target',
)

# One continuous circular line instead of the two stacked repeat arrows.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.5.dp)) {
            val c = Color.White.copy(alpha = .79f)
            val stroke = 1.65.dp.toPx()
            drawArc(
                color = c,
                startAngle = 48f,
                sweepAngle = 286f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val tip = Offset(size.width * .88f, size.height * .30f)
            drawLine(
                color = c,
                start = Offset(size.width * .75f, size.height * .23f),
                end = tip,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = c,
                start = tip,
                end = Offset(size.width * .83f, size.height * .43f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .79f),
                fontSize = 7.8.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'single-line playback mode glyph',
)

# NetEase-like title behavior: long titles travel back and forth instead of ending in an ellipsis.
marker = '@Composable\nprivate fun PlaybackModeGlyph'
if marker not in s:
    raise SystemExit('playback glyph marker missing')
marquee = '''@Composable
private fun PingPongTrackTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = androidx.compose.ui.text.TextStyle(
        color = Color.White.copy(alpha = .80f),
        fontSize = 17.4.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .08.sp,
    )
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offset = remember(text) { Animatable(0f) }

    BoxWithConstraints(
        modifier
            .height(26.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val textWidth = measurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
        val availableWidth = with(density) { maxWidth.toPx() }
        val travel = (textWidth - availableWidth).coerceAtLeast(0f)

        LaunchedEffect(text, travel) {
            offset.snapTo(0f)
            if (travel > 1f) {
                kotlinx.coroutines.delay(900)
                val duration = (travel * 18f).toInt().coerceIn(2400, 7000)
                while (isActive) {
                    offset.animateTo(-travel, tween(duration, easing = LinearEasing))
                    kotlinx.coroutines.delay(650)
                    offset.animateTo(0f, tween(duration, easing = LinearEasing))
                    kotlinx.coroutines.delay(850)
                }
            }
        }

        Text(
            text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.graphicsLayer { translationX = offset.value },
        )
    }
}

'''
s = s.replace(marker, marquee + marker, 1)

# Re-layout the complete title / mode / queue row with more breathing room.
sub(
    r'''            Row\(\n                Modifier\.fillMaxWidth\(\)\.padding\(top = if \(compact\) 4\.dp else 8\.dp\),\n                verticalAlignment = Alignment\.CenterVertically,\n            \) \{.*?\n            \}\n\n            Spacer\(Modifier\.height\(if \(compact\) 3\.dp else 7\.dp\)\)''',
    '''            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 7.dp else 11.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 15.dp)
                ) {
                    PingPongTrackTitle(
                        text = track.title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = .44f),
                        fontSize = 12.3.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = .04.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier.width(82.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietIconButton(
                        onClick = onTogglePlaybackMode,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics {
                                contentDescription = if (playbackMode == PlaybackMode.SEQUENTIAL) {
                                    "顺序播放"
                                } else {
                                    "单曲循环"
                                }
                            },
                    ) {
                        PlaybackModeGlyph(
                            mode = playbackMode,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    QuietIconButton(
                        onClick = onQueue,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "播放列表" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .80f),
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 5.dp else 8.dp))''',
    'spacious player metadata row',
)

# Search is a custom compact field rather than a compressed Material TextField, so text cannot be clipped.
sub(
    r'''@Composable\nfun SearchScreen\(.*?\n\}\n\n(?=@Composable\nprivate fun TrackRow)''',
    '''@Composable
fun SearchScreen(
    tracks: List<Track>,
    currentTrack: Track,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { mutableStateOf("") }
    val results = remember(query, tracks.size) {
        if (query.isBlank()) emptyList()
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LightBackdrop(currentTrack)
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(34.dp)
                        .semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MainText.copy(alpha = .84f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(7.dp))

                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = .58f))
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF83857F),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MainText.copy(alpha = .88f),
                            fontSize = 13.2.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MainText.copy(alpha = .78f)),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "搜索输入框" },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索歌曲或歌手",
                                        color = Color(0xFF999B96),
                                        fontSize = 13.2.sp,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }

            if (query.isBlank()) {
                Text(
                    "只搜索你的歌曲",
                    color = Color(0xFF999B96),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 57.dp, top = 10.dp),
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

''',
    'custom compact search field',
)

# Center the delete action over the current row instead of making the hand travel to the far right.
sub(
    r'''@Composable\nprivate fun TrackRow\(.*?\n\}\n\n(?=@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun QueueSheet)''',
    '''@Composable
private fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    onLongPressDelete: (() -> Unit)? = null,
    dismissSignal: Int = 0,
) {
    var deleteVisible by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(dismissSignal) { deleteVisible = false }

    val input = if (onLongPressDelete == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
            .pointerInput(track.id, deleteVisible) {
                detectTapGestures(
                    onTap = {
                        if (deleteVisible) deleteVisible = false else onClick()
                    },
                    onLongPress = { deleteVisible = true },
                )
            }
            .semantics { contentDescription = "长按删除:${track.title}" }
    }
    val deleteAction = onLongPressDelete ?: onMore

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(input)
                .alpha(if (deleteVisible) .20f else 1f)
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
                QuietIconButton(
                    onClick = { deleteVisible = true },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "更多:${track.title}" },
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = Color(0xFF9B9D98),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        if (deleteVisible && deleteAction != null) {
            val cancelSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = cancelSource,
                        indication = null,
                        onClick = { deleteVisible = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                InlineDeleteAction(
                    description = if (onLongPressDelete != null) {
                        "删除队列:${track.title}"
                    } else {
                        "删除音乐库:${track.title}"
                    },
                    onClick = {
                        deleteVisible = false
                        deleteAction()
                    },
                )
            }
        }
    }
}

''',
    'centered inline delete overlay',
)

# Queue: tapping blank sheet space cancels a revealed delete; long-press remains queue-only deletion.
sub(
    r'''@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun QueueSheet\(.*?\n\}\n\n(?=@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun LocalMusicSheet)''',
    '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    tracks: List<Track>,
    currentIndex: Int,
    currentTrack: Track,
    onDismiss: () -> Unit,
    onTrack: (Int) -> Unit,
    onLongPressDelete: (Track) -> Unit,
) {
    var dismissSignal by remember { mutableStateOf(0) }
    val blankTapSource = remember { MutableInteractionSource() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTrack.theme.mix(Color.White, .94f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = blankTapSource,
                    indication = null,
                    onClick = { dismissSignal += 1 },
                )
                .padding(horizontal = 18.dp)
        ) {
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
                    onLongPressDelete = { onLongPressDelete(track) },
                    dismissSignal = dismissSignal,
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

''',
    'queue blank-tap delete cancellation',
)

# Library: no redundant heading; delete reveals at row center and blank list space cancels it.
sub(
    r'''@Composable\nfun LibraryScreen\(.*?\n\}\n\n(?=@Composable\nprivate fun FavoriteCard)''',
    '''@Composable
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
    val local = tracks.filter { it.id.startsWith("local:") }
    var dismissSignal by remember { mutableStateOf(0) }
    val blankTapSource = remember { MutableInteractionSource() }

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
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                QuietIconButton(
                    onClick = onImport,
                    modifier = Modifier
                        .size(43.dp)
                        .semantics { contentDescription = "添加喜欢的音乐" },
                ) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = null,
                        tint = MainText.copy(alpha = .84f),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = blankTapSource,
                        indication = null,
                        onClick = { dismissSignal += 1 },
                    ),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
            ) {
                if (local.isEmpty()) {
                    item { SubtleEmpty("右上角可直接添加喜欢的音乐") }
                } else {
                    items(local, key = { it.id }) {
                        TrackRow(
                            track = it,
                            active = it.id == currentTrack.id,
                            onClick = { onTrack(it) },
                            onMore = { onMore(it) },
                            dismissSignal = dismissSignal,
                        )
                    }
                }
            }
        }

        UnifiedBottomNav(
            selectedHome = false,
            foreground = MainText,
            onHome = onHome,
            onLibrary = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = nav),
        )
    }
}

''',
    'library direct list and centered delete',
)

ui.write_text(s)
