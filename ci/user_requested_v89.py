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


# Final playback-mode glyph: preserve the user's confirmed NetEase-like open-loop
# silhouette, but replace the pixel-stepped contour with a smooth anti-aliased path
# and a slightly stronger stroke so it reads like a finished app icon at 24dp.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 1.7.dp, vertical = 3.0.dp)
        ) {
            val c = Color.White.copy(alpha = .76f)
            val stroke = 1.95.dp.toPx()
            val w = size.width
            val h = size.height

            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .76f, h * .18f)
                lineTo(w * .37f, h * .18f)
                cubicTo(
                    w * .18f, h * .18f,
                    w * .08f, h * .31f,
                    w * .08f, h * .50f,
                )
                cubicTo(
                    w * .08f, h * .72f,
                    w * .23f, h * .84f,
                    w * .42f, h * .84f,
                )
                lineTo(w * .65f, h * .84f)
                cubicTo(
                    w * .82f, h * .84f,
                    w * .91f, h * .72f,
                    w * .91f, h * .56f,
                )
            }
            drawPath(
                path = loop,
                color = c,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )

            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .97f, h * .18f)
                lineTo(w * .75f, h * .045f)
                lineTo(w * .75f, h * .315f)
                close()
            }
            drawPath(head, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .76f),
                fontSize = 7.1.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

''',
    'smooth stronger playback-mode glyph',
)

# Empty means truly empty: the player may keep its shell, but it must never invent
# demo artwork when there is no user song.
marker = '''@Composable
private fun DemoArtwork(track: Track) {
'''
if marker not in s:
    raise SystemExit('DemoArtwork marker missing')
s = s.replace(
    marker,
    marker + '''    if (track.id == "empty") {
        Box(Modifier.fillMaxSize().background(Color(0xFF111210)))
        return
    }
''',
    1,
)

# Music library has no seeded rows and no instructional filler. The existing add
# button remains the only action in the empty state.
old = '''                if (local.isEmpty()) {
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
                }'''
new = '''                if (local.isNotEmpty()) {
                    items(local, key = { it.id }) {
                        TrackRow(
                            track = it,
                            active = it.id == currentTrack.id,
                            onClick = { onTrack(it) },
                            onMore = { onMore(it) },
                            dismissSignal = dismissSignal,
                        )
                    }
                }'''
if old not in s:
    raise SystemExit('library empty-state block missing')
s = s.replace(old, new, 1)

ui.write_text(s)

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()

# No production demo songs. Keep a private neutral Track only as a non-playable UI
# fallback so an empty installation cannot crash while screens still have a theme.
anchor = '''private val DemoColors = listOf(
    Color(0xFF647A35),
    Color(0xFF536A80),
    Color(0xFF785052),
)
'''
if anchor not in m:
    raise SystemExit('DemoColors anchor missing')
empty_track = anchor + '''
private val EmptyTrack = Track(
    id = "empty",
    title = "",
    artist = "",
    uri = "",
    durationMs = 0L,
    theme = Color(0xFF30312E),
)
'''
m = m.replace(anchor, empty_track, 1)

m = m.replace('    val demoUris = remember { ensureDemoAudio(context) }\n\n', '', 1)

tracks_pattern = r'''    val tracks = remember \{\n        mutableStateListOf<Track>\(\)\.apply \{\n            DemoTracks\.forEachIndexed \{ index, item ->\n                add\(\n                    Track\(\n                        id = "demo:\$index",\n                        title = item\.title,\n                        artist = item\.artist,\n                        uri = Uri\.fromFile\(demoUris\[index\]\)\.toString\(\),\n                        durationMs = 8_000L,\n                        theme = DemoColors\[index\],\n                    \)\n                \)\n            \}\n        \}\n    \}'''
m2, n = re.subn(
    tracks_pattern,
    '    val tracks = remember { mutableStateListOf<Track>() }',
    m,
    count=1,
    flags=re.S,
)
if n != 1:
    raise SystemExit(f'remove seeded demo tracks: expected 1, replaced {n}')
m = m2

queue_pattern = r'''    val queueIds = remember \{\n        mutableStateListOf<String>\(\)\.apply \{\n            DemoTracks\.indices\.forEach \{ add\("demo:\$it"\) \}\n        \}\n    \}'''
m2, n = re.subn(
    queue_pattern,
    '    val queueIds = remember { mutableStateListOf<String>() }',
    m,
    count=1,
    flags=re.S,
)
if n != 1:
    raise SystemExit(f'remove seeded demo queue: expected 1, replaced {n}')
m = m2

old = '    var durationMs by remember { mutableLongStateOf(tracks.first().durationMs) }\n'
if old not in m:
    raise SystemExit('initial duration line missing')
m = m.replace(old, '    var durationMs by remember { mutableLongStateOf(0L) }\n', 1)

old = '''    fun togglePlayback() {
        val player = mediaPlayer'''
new = '''    fun togglePlayback() {
        if (queueIds.isEmpty()) return
        val player = mediaPlayer'''
if old not in m:
    raise SystemExit('togglePlayback anchor missing')
m = m.replace(old, new, 1)

old = '''    val activeQueue = queueIds.mapNotNull { id -> tracks.find { it.id == id } }
    val currentTrack = activeQueue.getOrElse(currentIndex) { tracks.first() }
'''
new = '''    val activeQueue = queueIds.mapNotNull { id -> tracks.find { it.id == id } }
    val currentTrack = activeQueue.getOrNull(currentIndex)
        ?: tracks.firstOrNull()
        ?: EmptyTrack
'''
if old not in m:
    raise SystemExit('currentTrack fallback missing')
m = m.replace(old, new, 1)

main.write_text(m)

# Scenario tests now validate the real first-run state instead of relying on three
# seeded fake songs that are no longer part of the product.
test = Path('app/src/androidTest/java/com/immersive/music/Phase1AcceptanceTest.kt')
test.write_text('''package com.immersive.music

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class Phase1AcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun grantAudioPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        val pkg = composeRule.activity.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $pkg $permission")
            .close()
    }

    private fun waitForControl(description: String) {
        composeRule.waitUntil(timeoutMillis = 4_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun firstRun_isEmptyAndHasNoFakeSongs() {
        composeRule.onNodeWithText("心动").assertIsDisplayed()
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("音乐库").assertIsDisplayed()
        composeRule.onAllNodesWithText("First Light").assertCountEquals(0)
        composeRule.onAllNodesWithText("Blue Hour").assertCountEquals(0)
        composeRule.onAllNodesWithText("Night Bloom").assertCountEquals(0)
        waitForControl("播放")
        waitForControl("上一首")
        waitForControl("下一首")
        waitForControl("顺序播放")
    }

    @Test
    fun playbackMode_stillTogglesCleanlyWhenLibraryIsEmpty() {
        composeRule.onNodeWithContentDescription("顺序播放").performClick()
        waitForControl("单曲循环")
        composeRule.onNodeWithContentDescription("单曲循环").performClick()
        waitForControl("顺序播放")
    }

    @Test
    fun queue_isEmptyInsteadOfShowingSeededSongs() {
        composeRule.onNodeWithContentDescription("播放列表").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
        composeRule.onAllNodesWithText("First Light").assertCountEquals(0)
        composeRule.onAllNodesWithText("Blue Hour").assertCountEquals(0)
        composeRule.onAllNodesWithText("Night Bloom").assertCountEquals(0)
    }

    @Test
    fun library_isARealBlankStateWithAddEntry() {
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithContentDescription("页面顶部:音乐库").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加喜欢的音乐").assertIsDisplayed()
        composeRule.onAllNodesWithText("First Light").assertCountEquals(0)
        composeRule.onAllNodesWithText("最近播放").assertCountEquals(0)
        composeRule.onAllNodesWithText("本地音乐").assertCountEquals(0)
        composeRule.onAllNodesWithText("我喜欢的音乐").assertCountEquals(0)
        composeRule.onAllNodesWithText("右上角可直接添加喜欢的音乐").assertCountEquals(0)
    }

    @Test
    fun localImport_opensInsideAppInsteadOfSystemFolders() {
        grantAudioPermission()
        composeRule.onNodeWithText("音乐库").performClick()
        composeRule.onNodeWithContentDescription("添加喜欢的音乐").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("手机音乐选择面板").assertIsDisplayed()
                composeRule.onNodeWithText("选择手机音乐").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun search_isCompactAndHasNoRedundantHelperOrDemoSongs() {
        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithContentDescription("页面顶部:搜索").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索输入框").assertIsDisplayed()
        composeRule.onNodeWithText("搜索歌曲或歌手").assertIsDisplayed()
        composeRule.onAllNodesWithText("只搜索你的歌曲").assertCountEquals(0)
        composeRule.onAllNodesWithText("First Light").assertCountEquals(0)
    }
}
''')

# Keep the generated deliverable SPEC aligned with the implementation used by CI.
spec = Path('SPEC-music-phase1.md')
p = spec.read_text()
p = p.replace(
    '- Phase 1 只使用演示音轨和手机 MediaStore 本地歌曲，不接商业在线曲库。',
    '- Phase 1 正式界面不预置任何演示歌曲；只展示通过 MediaStore 导入的手机歌曲。没有歌曲时保持真实空状态，不接商业在线曲库。',
)
p = p.replace(
    '- 只搜索演示音轨和已导入歌曲。',
    '- 只搜索用户已导入歌曲；空库时不伪造任何默认歌曲。',
)
p = p.replace(
    'Given 播放列表中存在 First Light',
    'Given 用户已导入并播放过一首歌曲，播放列表中存在该歌曲',
)
p = p.replace(
    '- 当前只验证手机本地曲库体验；合法在线曲库属于后续阶段。',
    '- 当前只验证手机本地曲库体验；首次安装不预置 First Light、Blue Hour、Night Bloom 或任何其他假歌曲；合法在线曲库属于后续阶段。',
)
if '### 首次空状态' not in p:
    phase_anchor = '### 手机音乐导入\n'
    if phase_anchor not in p:
        raise SystemExit('SPEC phone import section missing')
    p = p.replace(
        phase_anchor,
        '''### 首次空状态
- 首次安装或用户尚未导入音乐时，音乐库、播放列表、搜索结果都不得出现演示歌曲、假歌名或默认收藏。
- 音乐库空时保留页面标题、右上角添加入口和底部导航，内容区保持干净空白。
- 首页使用非歌曲的中性播放器空壳，不显示假歌名、假歌手或假封面；播放键在无队列时不进入伪播放状态。

''' + phase_anchor,
        1,
    )
spec.write_text(p)
