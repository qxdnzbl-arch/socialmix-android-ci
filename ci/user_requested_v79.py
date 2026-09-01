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

# Playback mode icons.
anchor = 'import androidx.compose.material.icons.rounded.PlayArrow\n'
if 'import androidx.compose.material.icons.rounded.Repeat\n' not in s:
    if anchor not in s:
        raise SystemExit('repeat icon import anchor missing')
    s = s.replace(
        anchor,
        anchor + 'import androidx.compose.material.icons.rounded.Repeat\nimport androidx.compose.material.icons.rounded.RepeatOne\n',
        1,
    )

# Replace the player heart with sequential/single-loop mode control.
old = '''    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,'''
new = '''    playbackMode: PlaybackMode,
    onTogglePlaybackMode: () -> Unit,'''
if old not in s:
    raise SystemExit('home favorite parameters missing')
s = s.replace(old, new, 1)

sub(
    r'''\s{16}IconButton\(\n\s{20}onClick = onToggleFavorite,.*?\n\s{16}\}\n\s{16}Spacer\(Modifier\.width\(2\.dp\)\)''',
    '''                IconButton(
                    onClick = onTogglePlaybackMode,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics {
                            contentDescription = if (playbackMode == PlaybackMode.SEQUENTIAL) {
                                "顺序播放"
                            } else {
                                "单曲循环"
                            }
                        },
                ) {
                    Icon(
                        if (playbackMode == PlaybackMode.SEQUENTIAL) {
                            Icons.Rounded.Repeat
                        } else {
                            Icons.Rounded.RepeatOne
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .88f),
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))''',
    'replace heart with playback mode',
)

# Replace the chunky arc patches with a black, fine-grooved disc and a broad soft
# directional reflection. This keeps the vinyl visibly glossy without gray crescent blocks.
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
                ambientColor = Color.Black.copy(alpha = .48f),
                spotColor = Color.Black.copy(alpha = .50f),
            )
            .clip(CircleShape)
            .background(Color(0xFF010201)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF010201), r, center)

            // Fine physical grooves: almost black with restrained ring reflections.
            repeat(70) { i ->
                val rr = r * (.490f + i * .00705f)
                drawCircle(
                    color = if (i % 5 == 0) {
                        Color.White.copy(alpha = .025f)
                    } else {
                        Color.Black.copy(alpha = .72f)
                    },
                    radius = rr,
                    center = center,
                    style = Stroke(width = .46.dp.toPx()),
                )
            }

            // A single soft diagonal reflection, closer to real black vinyl than solid gray arcs.
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = .018f),
                        Color.White.copy(alpha = .060f),
                        Color.White.copy(alpha = .026f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * .04f, size.height * .12f),
                    end = Offset(size.width * .96f, size.height * .90f),
                ),
                radius = r * .985f,
                center = center,
            )

            // Very subtle outer-edge catch light; no visible crescent patches.
            drawCircle(
                color = Color.White.copy(alpha = .040f),
                radius = r * .970f,
                center = center,
                style = Stroke(width = .75.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = .78f),
                radius = r * .758f,
                center = center,
                style = Stroke(width = 1.0.dp.toPx()),
            )
        }

        Box(
            Modifier
                .size(maxWidth * .655f)
                .clip(CircleShape)
                .border(.7.dp, Color.Black.copy(alpha = .72f), CircleShape),
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
    'refine glossy black vinyl',
)

# Library: imported phone songs are the user's liked collection. One visible concept only.
if '                item { FavoriteCard(favorites.size, onOpenFavorites) }\n' not in s:
    raise SystemExit('favorite card row missing')
s = s.replace('                item { FavoriteCard(favorites.size, onOpenFavorites) }\n', '', 1)
if 'item { SectionTitle("本地音乐", local.size) }' not in s:
    raise SystemExit('local section title missing')
s = s.replace('item { SectionTitle("本地音乐", local.size) }', 'item { SectionTitle("我喜欢的音乐", local.size) }', 1)
s = s.replace('SubtleEmpty("右上角可直接添加手机里的音乐")', 'SubtleEmpty("右上角可直接添加喜欢的音乐")', 1)

# Track rows can optionally expose a long-press action. Queue rows use it for delete.
sub(
    r'''@Composable\nprivate fun TrackRow\(\n    track: Track,\n    active: Boolean,\n    onClick: \(\) -> Unit,\n    onMore: \(\(\) -> Unit\)\? = null,\n\) \{\n    Row\(\n        Modifier\n            \.fillMaxWidth\(\)\n            \.clickable\(onClick = onClick\)\n            \.padding\(horizontal = 3\.dp, vertical = 5\.dp\),''',
    '''@Composable
private fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val input = if (onLongPress == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
            .pointerInput(track.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .semantics { contentDescription = "长按删除:${track.title}" }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(input)
            .padding(horizontal = 3.dp, vertical = 5.dp),''',
    'track row long press support',
)

# Queue receives a long-press delete callback.
old = '''    currentTrack: Track,
    onDismiss: () -> Unit,
    onTrack: (Int) -> Unit,
) {'''
new = '''    currentTrack: Track,
    onDismiss: () -> Unit,
    onTrack: (Int) -> Unit,
    onLongPressDelete: (Track) -> Unit,
) {'''
if old not in s:
    raise SystemExit('queue signature missing')
s = s.replace(old, new, 1)
old = '''                    active = index == currentIndex,
                    onClick = { onTrack(index) },
                )'''
new = '''                    active = index == currentIndex,
                    onClick = { onTrack(index) },
                    onLongPress = { onLongPressDelete(track) },
                )'''
if old not in s:
    raise SystemExit('queue track row missing')
s = s.replace(old, new, 1)

# Song options: replace legacy recent-play removal wording/behavior with Delete.
sub(
    r'''fun TrackActionSheet\(\n    track: Track,\n    currentTrack: Track,\n    favorite: Boolean,\n    inRecent: Boolean,\n    onDismiss: \(\) -> Unit,\n    onToggleFavorite: \(\) -> Unit,\n    onRemoveRecent: \(\) -> Unit,\n\) \{''',
    '''fun TrackActionSheet(
    track: Track,
    currentTrack: Track,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {''',
    'track action signature',
)
old = '''            ActionRow(if (favorite) "取消收藏" else "收藏", onToggleFavorite)
            if (inRecent) ActionRow("从最近播放移除", onRemoveRecent)'''
if old not in s:
    raise SystemExit('legacy track actions missing')
s = s.replace(old, '            ActionRow("删除", onDelete)', 1)

ui.write_text(s)

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# Toast is used only to mirror the reference feedback when changing playback mode.
if 'import android.widget.Toast\n' not in m:
    anchor = 'import android.provider.OpenableColumns\n'
    if anchor not in m:
        raise SystemExit('toast import anchor missing')
    m = m.replace(anchor, anchor + 'import android.widget.Toast\n', 1)

# Playback mode state model.
if 'enum class PlaybackMode' not in m:
    anchor = 'enum class AppPage { HOME, LIBRARY, SEARCH, FAVORITES }\n'
    if anchor not in m:
        raise SystemExit('AppPage enum anchor missing')
    m = m.replace(anchor, anchor + 'enum class PlaybackMode { SEQUENTIAL, SINGLE_LOOP }\n', 1)

anchor = '    var playIntent by remember { mutableStateOf(false) }\n'
if 'var playbackMode by remember' not in m:
    if anchor not in m:
        raise SystemExit('playback state anchor missing')
    m = m.replace(anchor, anchor + '    var playbackMode by remember { mutableStateOf(PlaybackMode.SEQUENTIAL) }\n', 1)

# Auto-favorite every imported phone song because import itself now means "I like this".
old = '''            if (tracks.none { it.id == imported.id }) tracks.add(imported)
            val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()'''
new = '''            if (tracks.none { it.id == imported.id }) tracks.add(imported)
            if (!favoriteIds.contains(imported.id)) {
                favoriteIds.add(imported.id)
                saveFavorites()
            }
            val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()'''
if old not in m:
    raise SystemExit('import song block missing')
m = m.replace(old, new, 1)

# Playback-mode toggle with NetEase-like immediate feedback.
anchor = '''    fun togglePlayback() {
        val player = mediaPlayer'''
if anchor not in m:
    raise SystemExit('togglePlayback anchor missing')
insert = '''    fun togglePlaybackMode() {
        playbackMode = if (playbackMode == PlaybackMode.SEQUENTIAL) {
            PlaybackMode.SINGLE_LOOP
        } else {
            PlaybackMode.SEQUENTIAL
        }
        Toast.makeText(
            context,
            if (playbackMode == PlaybackMode.SEQUENTIAL) "顺序播放" else "单曲循环",
            Toast.LENGTH_SHORT,
        ).show()
    }

'''
m = m.replace(anchor, insert + anchor, 1)

# Completion obeys sequential play / single-track loop instead of always stopping.
old = '''            player.setOnCompletionListener {
                playIntent = false
                needleOnDisc = false
                positionMs = durationMs
            }'''
new = '''            player.setOnCompletionListener {
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
                        playTrack(safeIndex + 1, true)
                    }
                }
            }'''
if old not in m:
    raise SystemExit('completion listener missing')
m = m.replace(old, new, 1)

# Delete means remove from this app/library, favorites and queue; never delete the phone original.
anchor = '''    fun toggleFavorite(id: String) {
        if (favoriteIds.contains(id)) favoriteIds.remove(id) else favoriteIds.add(id)
        saveFavorites()
    }

'''
if anchor not in m:
    raise SystemExit('delete insertion anchor missing')
delete_fn = '''    fun deleteTrack(track: Track) {
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index < 0) return

        val wasCurrent = index == currentIndex
        val wasPlaying = playIntent

        favoriteIds.remove(track.id)
        saveFavorites()
        removeRecent(track.id)

        if (track.id.startsWith("local:")) {
            val stored = prefs.getStringSet("imported_uris", emptySet()).orEmpty().toMutableSet()
            stored.remove(track.uri)
            prefs.edit().putStringSet("imported_uris", stored).apply()
        }

        tracks.removeAt(index)
        if (tracks.isEmpty()) return

        if (wasCurrent) {
            playTrack(index.coerceAtMost(tracks.lastIndex), wasPlaying, recordRecent = false)
        } else if (index < currentIndex) {
            currentIndex -= 1
        }
    }

'''
m = m.replace(anchor, anchor + delete_fn, 1)

# Home now receives playback mode instead of favorite state/action.
old = '''            isFavorite = favoriteIds.contains(currentTrack.id),
            onToggleFavorite = { toggleFavorite(currentTrack.id) },'''
new = '''            playbackMode = playbackMode,
            onTogglePlaybackMode = ::togglePlaybackMode,'''
if old not in m:
    raise SystemExit('home favorite call missing')
m = m.replace(old, new, 1)

# Queue long press opens the same song options sheet with Delete.
old = '''            onTrack = { index ->
                playTrack(index, true)
                showQueue = false
            },
        )'''
new = '''            onTrack = { index ->
                playTrack(index, true)
                showQueue = false
            },
            onLongPressDelete = { track -> menuTrack = track },
        )'''
if old not in m:
    raise SystemExit('queue call missing')
m = m.replace(old, new, 1)

# Song action sheet no longer exposes favorite/recent actions; only Delete.
sub_pattern = r'''        TrackActionSheet\(\n            track = selected,\n            currentTrack = currentTrack,\n            favorite = favoriteIds\.contains\(selected\.id\),\n            inRecent = recentIds\.contains\(selected\.id\),\n            onDismiss = \{ menuTrack = null \},\n            onToggleFavorite = \{.*?\n            \},\n            onRemoveRecent = \{.*?\n            \},\n        \)'''
repl = '''        TrackActionSheet(
            track = selected,
            currentTrack = currentTrack,
            onDismiss = { menuTrack = null },
            onDelete = {
                deleteTrack(selected)
                menuTrack = null
            },
        )'''
m2, n = re.subn(sub_pattern, repl, m, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'track action call: expected 1, replaced {n}')
m = m2

main.write_text(m)
