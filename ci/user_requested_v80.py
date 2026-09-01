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

# Replace Material repeat glyphs with the circular-arrow geometry visible in the
# supplied NetEase screenshots. The same control toggles sequential/single-loop.
sub(
    r'''\s{20}Icon\(\n\s{24}if \(playbackMode == PlaybackMode\.SEQUENTIAL\) \{\n\s{28}Icons\.Rounded\.Repeat\n\s{24}\} else \{\n\s{28}Icons\.Rounded\.RepeatOne\n\s{24}\},\n\s{24}contentDescription = null,\n\s{24}tint = Color\.White\.copy\(alpha = \.88f\),\n\s{24}modifier = Modifier\.size\(27\.dp\),\n\s{20}\)''',
    '''                    PlaybackModeGlyph(
                        mode = playbackMode,
                        modifier = Modifier.size(27.dp),
                    )''',
    'playback mode glyph call',
)

marker = '@Composable\nprivate fun VinylDisc'
if marker not in s:
    raise SystemExit('vinyl marker missing')
playback_glyph = '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.8.dp)) {
            val c = Color.White.copy(alpha = .86f)
            val stroke = 2.15.dp.toPx()
            drawArc(
                color = c,
                startAngle = 35f,
                sweepAngle = 285f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val tip = Offset(size.width * .87f, size.height * .29f)
            drawLine(
                c,
                Offset(size.width * .70f, size.height * .25f),
                tip,
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                c,
                tip,
                Offset(size.width * .82f, size.height * .46f),
                stroke,
                StrokeCap.Round,
            )
        }
        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .88f),
                fontSize = 8.4.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

'''
s = s.replace(marker, playback_glyph + marker, 1)

# Final vinyl pass: near-black body, fine grooves and only restrained curved
# reflections. No large gray crescent blocks.
sub(
    r'@Composable\nprivate fun VinylDisc\(track: Track, rotation: Float, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun ToneArm)',
    '''@Composable
private fun VinylDisc(track: Track, rotation: Float, modifier: Modifier = Modifier) {
    val bitmap = rememberCoverBitmap(track)
    BoxWithConstraints(
        modifier
            .graphicsLayer { rotationZ = rotation }
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = .50f),
                spotColor = Color.Black.copy(alpha = .52f),
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF090A09),
                        Color(0xFF030403),
                        Color(0xFF010101),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF010201), r, center)

            repeat(78) { i ->
                val rr = r * (.486f + i * .00655f)
                drawCircle(
                    color = if (i % 6 == 0) {
                        Color.White.copy(alpha = .024f)
                    } else {
                        Color.Black.copy(alpha = .82f)
                    },
                    radius = rr,
                    center = center,
                    style = Stroke(width = .44.dp.toPx()),
                )
            }

            drawArc(
                color = Color.White.copy(alpha = .034f),
                startAngle = 205f,
                sweepAngle = 58f,
                useCenter = false,
                style = Stroke(width = 5.2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .018f),
                startAngle = 24f,
                sweepAngle = 48f,
                useCenter = false,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = Color.White.copy(alpha = .035f),
                radius = r * .970f,
                center = center,
                style = Stroke(width = .72.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = .82f),
                radius = r * .758f,
                center = center,
                style = Stroke(width = 1.0.dp.toPx()),
            )
        }

        Box(
            Modifier
                .size(maxWidth * .655f)
                .clip(CircleShape)
                .border(.7.dp, Color.Black.copy(alpha = .76f), CircleShape),
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
                .background(Color(0xFFE5E4DE).copy(alpha = .96f))
        )
        Box(
            Modifier
                .size(4.4.dp)
                .clip(CircleShape)
                .background(Color(0xFF666862))
        )
    }
}

''',
    'final restrained vinyl gloss',
)

# Remove remaining user-visible "local music" wording from the library entry semantics.
s = s.replace('contentDescription = "导入本地音乐"', 'contentDescription = "添加喜欢的音乐"', 1)
ui.write_text(s)

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# Playback queue is a separate state layer from the library. Queue deletion must
# never delete a song from the liked library or the phone.
anchor = '    val recentIds = remember { mutableStateListOf<String>() }\n'
if 'val queueIds = remember' not in m:
    if anchor not in m:
        raise SystemExit('queue state anchor missing')
    m = m.replace(
        anchor,
        anchor + '''    val queueIds = remember {
        mutableStateListOf<String>().apply {
            DemoTracks.indices.forEach { add("demo:$it") }
        }
    }
''',
        1,
    )

anchor = '    var playbackMode by remember { mutableStateOf(PlaybackMode.SEQUENTIAL) }\n'
if 'var completionRequest by remember' not in m:
    if anchor not in m:
        raise SystemExit('completion state anchor missing')
    m = m.replace(anchor, anchor + '    var completionRequest by remember { mutableStateOf<Int?>(null) }\n', 1)

anchor = '    var menuTrack by remember { mutableStateOf<Track?>(null) }\n'
if 'var queueMenuTrack by remember' not in m:
    if anchor not in m:
        raise SystemExit('queue menu anchor missing')
    m = m.replace(anchor, anchor + '    var queueMenuTrack by remember { mutableStateOf<Track?>(null) }\n', 1)

# Remove the v79 library-delete implementation from before playTrack. A corrected
# version is inserted after playTrack so local-function forward references cannot fail.
m2, n = re.subn(
    r'''    fun deleteTrack\(track: Track\) \{.*?\n    \}\n\n(?=    fun playTrack)''',
    '',
    m,
    count=1,
    flags=re.S,
)
if n != 1:
    raise SystemExit(f'remove old deleteTrack: expected 1, replaced {n}')
m = m2

# Replace playTrack with queue-based playback. Completion requests are handled by
# a Compose effect after the function has been declared, avoiding recursive local
# function resolution problems.
pattern = r'''    fun playTrack\(index: Int, requestedPlaying: Boolean, recordRecent: Boolean = true\) \{.*?\n    \}\n\n(?=    fun togglePlaybackMode)'''
replacement = '''    fun queueTracksNow(): List<Track> =
        queueIds.mapNotNull { id -> tracks.find { it.id == id } }

    fun ensureQueued(track: Track): Int {
        if (!queueIds.contains(track.id)) queueIds.add(track.id)
        return queueIds.indexOf(track.id)
    }

    fun playTrack(index: Int, requestedPlaying: Boolean, recordRecent: Boolean = true) {
        val queue = queueTracksNow()
        if (queue.isEmpty()) return
        val safeIndex = (index % queue.size + queue.size) % queue.size
        val track = queue[safeIndex]

        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null

        currentIndex = safeIndex
        positionMs = 0L
        durationMs = max(1L, track.durationMs)
        playIntent = requestedPlaying
        needleOnDisc = requestedPlaying
        if (recordRecent) markRecent(track.id)

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setDataSource(context, Uri.parse(track.uri))
            player.setOnPreparedListener {
                durationMs = max(1, it.duration).toLong()
                if (playIntent) {
                    needleOnDisc = true
                    runCatching { it.start() }
                } else {
                    needleOnDisc = false
                }
            }
            player.setOnCompletionListener {
                positionMs = durationMs
                when (playbackMode) {
                    PlaybackMode.SINGLE_LOOP -> {
                        positionMs = 0L
                        playIntent = true
                        needleOnDisc = true
                        runCatching { it.seekTo(0) }
                        runCatching { it.start() }
                    }
                    PlaybackMode.SEQUENTIAL -> {
                        completionRequest = safeIndex + 1
                    }
                }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                if (mediaPlayer === failedPlayer) mediaPlayer = null
                runCatching { failedPlayer.reset() }
                runCatching { failedPlayer.release() }
                needleOnDisc = playIntent
                true
            }
            player.prepareAsync()
        }.onFailure {
            runCatching { player.release() }
            if (mediaPlayer === player) mediaPlayer = null
            needleOnDisc = playIntent
        }
    }

    LaunchedEffect(completionRequest) {
        val next = completionRequest ?: return@LaunchedEffect
        completionRequest = null
        playTrack(next, true)
    }

    fun removeFromQueue(track: Track) {
        val index = queueIds.indexOf(track.id)
        if (index < 0) return
        val wasCurrent = index == currentIndex
        val wasPlaying = playIntent
        queueIds.removeAt(index)

        if (queueIds.isEmpty()) {
            mediaPlayer?.runCatching { pause() }
            playIntent = false
            needleOnDisc = false
            currentIndex = 0
            positionMs = 0L
            return
        }

        if (wasCurrent) {
            playTrack(index.coerceAtMost(queueIds.lastIndex), wasPlaying, recordRecent = false)
        } else if (index < currentIndex) {
            currentIndex -= 1
        }
    }

    fun deleteTrack(track: Track) {
        val queueIndex = queueIds.indexOf(track.id)
        val wasCurrent = queueIndex >= 0 && queueIndex == currentIndex
        val wasPlaying = playIntent

        favoriteIds.remove(track.id)
        saveFavorites()
        removeRecent(track.id)

        if (track.id.startsWith("local:")) {
            val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
            stored.remove(track.uri)
            prefs.edit().putStringSet("imported_uris", stored).apply()
        }

        if (queueIndex >= 0) queueIds.removeAt(queueIndex)
        tracks.removeAll { it.id == track.id }

        if (queueIds.isEmpty()) {
            mediaPlayer?.runCatching { pause() }
            playIntent = false
            needleOnDisc = false
            currentIndex = 0
            positionMs = 0L
            return
        }

        if (wasCurrent) {
            playTrack(queueIndex.coerceAtMost(queueIds.lastIndex), wasPlaying, recordRecent = false)
        } else if (queueIndex in 0 until currentIndex) {
            currentIndex -= 1
        }
    }

'''
m2, n = re.subn(pattern, replacement, m, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'queue-based playTrack: expected 1, replaced {n}')
m = m2

# Imported songs enter both the liked library and the current playback queue.
old = '''            if (tracks.none { it.id == imported.id }) tracks.add(imported)
            if (!favoriteIds.contains(imported.id)) {'''
new = '''            if (tracks.none { it.id == imported.id }) tracks.add(imported)
            if (!queueIds.contains(imported.id)) queueIds.add(imported.id)
            if (!favoriteIds.contains(imported.id)) {'''
if old not in m:
    raise SystemExit('import queue insertion missing')
m = m.replace(old, new, 1)

old = '''        imported.forEach { if (tracks.none { t -> t.id == it.id }) tracks.add(it) }
        playTrack(0, false)'''
new = '''        imported.forEach {
            if (tracks.none { t -> t.id == it.id }) tracks.add(it)
            if (!queueIds.contains(it.id)) queueIds.add(it.id)
            if (!favoriteIds.contains(it.id)) favoriteIds.add(it.id)
        }
        saveFavorites()
        playTrack(0, false)'''
if old not in m:
    raise SystemExit('restore queue insertion missing')
m = m.replace(old, new, 1)

# Current track is resolved from the playback queue, not from library indices.
old = '    val currentTrack = tracks.getOrElse(currentIndex) { tracks.first() }\n'
new = '''    val activeQueue = queueIds.mapNotNull { id -> tracks.find { it.id == id } }
    val currentTrack = activeQueue.getOrElse(currentIndex) { tracks.first() }
'''
if old not in m:
    raise SystemExit('current track source missing')
m = m.replace(old, new, 1)

# Any song selected from library/search is re-added to the queue if it was
# previously removed from the queue only.
m = m.replace('onTrack = { playTrack(tracks.indexOf(it), true); page = AppPage.HOME },',
              'onTrack = { playTrack(ensureQueued(it), true); page = AppPage.HOME },')

# Queue sheet receives only current queue items. Long press is queue-only delete.
old = '''        QueueSheet(
            tracks = tracks,
            currentIndex = currentIndex,'''
new = '''        QueueSheet(
            tracks = activeQueue,
            currentIndex = currentIndex,'''
if old not in m:
    raise SystemExit('queue sheet source missing')
m = m.replace(old, new, 1)

old = '            onLongPressDelete = { track -> menuTrack = track },\n'
new = '''            onLongPressDelete = { track ->
                showQueue = false
                queueMenuTrack = track
            },
'''
if old not in m:
    raise SystemExit('queue long-press callback missing')
m = m.replace(old, new, 1)

# Queue delete and library delete use the same visible sheet but different data
# mutations. This is the distinction the user explicitly required.
anchor = '    menuTrack?.let { selected ->\n'
if anchor not in m:
    raise SystemExit('library menu anchor missing')
queue_sheet = '''    queueMenuTrack?.let { selected ->
        TrackActionSheet(
            track = selected,
            currentTrack = currentTrack,
            onDismiss = { queueMenuTrack = null },
            onDelete = {
                removeFromQueue(selected)
                queueMenuTrack = null
            },
        )
    }

'''
m = m.replace(anchor, queue_sheet + anchor, 1)

main.write_text(m)
