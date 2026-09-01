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

# Vertical overflow dots are visually lighter and cleaner than the horizontal glyph.
s = s.replace(
    'import androidx.compose.material.icons.rounded.MoreHoriz\n',
    'import androidx.compose.material.icons.rounded.MoreVert\n',
    1,
)

# State used by inline delete affordances.
if 'import androidx.compose.runtime.mutableStateOf\n' not in s:
    anchor = 'import androidx.compose.runtime.remember\n'
    if anchor not in s:
        raise SystemExit('runtime remember import missing')
    s = s.replace(anchor, anchor + 'import androidx.compose.runtime.mutableStateOf\n', 1)

# No-ripple icon button: keep full click semantics/hit target while removing the circular
# Material press halo the user explicitly rejected.
marker = '@Composable\nfun HomeScreen'
if marker not in s:
    raise SystemExit('HomeScreen marker missing')
helpers = '''@Composable
private fun QuietIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun InlineDeleteAction(
    description: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(52.dp)
            .height(40.dp)
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
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

'''
s = s.replace(marker, helpers + marker, 1)

# Playback mode icon: use the clean rounded repeat glyph family so its visual weight,
# dimensions and line language match the adjacent queue icon.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (mode == PlaybackMode.SEQUENTIAL) {
            Icons.Rounded.Repeat
        } else {
            Icons.Rounded.RepeatOne
        },
        contentDescription = null,
        tint = Color.White.copy(alpha = .84f),
        modifier = modifier,
    )
}

''',
    'simplify playback mode glyph',
)

# Give playback-mode and queue glyphs the same visual size.
s = s.replace(
    '''                    PlaybackModeGlyph(
                        mode = playbackMode,
                        modifier = Modifier.size(27.dp),
                    )''',
    '''                    PlaybackModeGlyph(
                        mode = playbackMode,
                        modifier = Modifier.size(25.dp),
                    )''',
    1,
)
sub(
    r'''(Icons\.AutoMirrored\.Rounded\.QueueMusic,\n\s+contentDescription = null,\n\s+tint = Color\.White\.copy\(alpha = \.88f\),\n\s+modifier = Modifier\.size\()27(\.dp\),)''',
    r'\g<1>25\g<2>',
    'match home queue icon size',
)

# Music library itself already means the user's kept music, so remove the redundant
# "我喜欢的音乐" section label/count. The list begins directly under the page header.
old = '                item { SectionTitle("我喜欢的音乐", local.size) }\n'
if old not in s:
    raise SystemExit('redundant liked section title missing')
s = s.replace(old, '', 1)

# Compact search: same visual scale as the rest of the app instead of a large pill/header.
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
                    .padding(start = 13.dp, end = 20.dp, top = 7.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MainText.copy(alpha = .90f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(5.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MainText,
                        fontSize = 13.2.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    placeholder = {
                        Text(
                            "搜索歌曲或歌手",
                            color = Color(0xFF999B96),
                            fontSize = 13.2.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .semantics { contentDescription = "搜索输入框" },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF80827D),
                            modifier = Modifier.size(18.5.dp),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = .58f),
                        unfocusedContainerColor = Color.White.copy(alpha = .58f),
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
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 54.dp, top = 9.dp),
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
    'compact search screen',
)

# Direct, inline delete. Tapping the vertical dots or long-pressing a queue row reveals
# "删除" on that same row; there is no second song-options bottom sheet.
sub(
    r'''@Composable\nprivate fun TrackRow\(.*?\n\}\n\n(?=@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun QueueSheet)''',
    '''@Composable
private fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    onLongPressDelete: (() -> Unit)? = null,
) {
    var deleteVisible by remember(track.id) { mutableStateOf(false) }

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

    Row(
        Modifier
            .fillMaxWidth()
            .then(input)
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

        val deleteAction = onLongPressDelete ?: onMore
        if (deleteVisible && deleteAction != null) {
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
        } else if (onMore != null) {
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
}

''',
    'inline row delete',
)

# Queue keeps the long-press gesture but now delegates directly to queue removal after
# the inline delete label is tapped.
sub(
    r'''@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun QueueSheet\(.*?\n\}\n\n(?=@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun TrackActionSheet)''',
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
                    onLongPressDelete = { onLongPressDelete(track) },
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
    'direct queue delete',
)

# The extra "歌曲选项" sheet is deliberately removed: both delete entry points are inline.
sub(
    r'''@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun TrackActionSheet\(.*?\n\}\n\n@Composable\nprivate fun ActionRow\(.*?\n\}\n\n(?=@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun LocalMusicSheet)''',
    '',
    'remove redundant song options sheet',
)

# Remove circular Material press feedback from all icon buttons in this UI file.
# QuietIconButton keeps semantic click actions so automated/accessibility interaction remains valid.
s = s.replace('IconButton(', 'QuietIconButton(')

ui.write_text(s)

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# No intermediate sheet state is needed anymore.
m = m.replace('    var menuTrack by remember { mutableStateOf<Track?>(null) }\n', '', 1)
m = m.replace('    var queueMenuTrack by remember { mutableStateOf<Track?>(null) }\n', '', 1)

# In the library, tapping vertical dots now reveals the inline delete on that row;
# the callback itself performs the persistent App-library deletion.
m = m.replace('onMore = { menuTrack = it },', 'onMore = { deleteTrack(it) },')

# Queue long-press reveals inline delete and removes only from queue when tapped.
old = '''            onLongPressDelete = { track ->
                showQueue = false
                queueMenuTrack = track
            },'''
if old not in m:
    raise SystemExit('queue menu callback missing')
m = m.replace(old, '            onLongPressDelete = ::removeFromQueue,', 1)

# Remove both intermediate TrackActionSheet call sites left by v79/v80.
m2, n = re.subn(
    r'''\n    queueMenuTrack\?\.let \{ selected ->.*?\n    \}\n\n    menuTrack\?\.let \{ selected ->.*?\n    \}\n(?=\n    if \(showLocalMusic\))''',
    '',
    m,
    count=1,
    flags=re.S,
)
if n != 1:
    raise SystemExit(f'remove action-sheet call sites: expected 1, replaced {n}')
m = m2

main.write_text(m)
