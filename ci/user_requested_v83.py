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


# Native Compose marquee is draw-based and avoids the per-frame layout jitter of the
# previous manual translation animation.
for anchor, addition in [
    ('import androidx.compose.foundation.Canvas\n', 'import androidx.compose.foundation.ExperimentalFoundationApi\n'),
    ('import androidx.compose.foundation.ExperimentalFoundationApi\n', 'import androidx.compose.foundation.basicMarquee\n'),
]:
    if addition not in s:
        if anchor not in s:
            raise SystemExit(f'import anchor missing: {anchor.strip()}')
        s = s.replace(anchor, anchor + addition, 1)

# Shared geometry tokens: all first-level screens use the same top offset, top-bar
# height, side inset and icon sizing so switching pages never changes the visual grid.
anchor = 'private val CloudRed = Color(0xFFD84B57)\n'
if 'private val AppPageSide' not in s:
    if anchor not in s:
        raise SystemExit('visual token anchor missing')
    s = s.replace(
        anchor,
        anchor + '''\nprivate val AppPageSide = 22.dp\nprivate val AppTopGap = 8.dp\nprivate val AppTopBarHeight = 52.dp\nprivate val AppTopIconHit = 44.dp\nprivate val AppTopIconSize = 22.dp\nprivate val AppPageTitleSize = 19.sp\n''',
        1,
    )

# Replace the ping-pong title implementation with a native one-way, full-content
# marquee. It only animates when text actually overflows; short strings remain still.
sub(
    r'''@Composable\nprivate fun PingPongTrackTitle\(.*?\n\}\n\n(?=@Composable\nprivate fun PlaybackModeGlyph)''',
    '''@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerMarqueeLine(
    text: String,
    title: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = if (title) .82f else .52f),
        fontSize = if (title) 17.8.sp else 13.4.sp,
        fontWeight = if (title) FontWeight.Medium else FontWeight.Normal,
        lineHeight = if (title) 22.sp else 18.sp,
        letterSpacing = if (title) .08.sp else .04.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 650,
                initialDelayMillis = 1_150,
                spacing = androidx.compose.foundation.MarqueeSpacing.fractionOfContainer(.09f),
                velocity = 28.dp,
            )
            .semantics {
                contentDescription = if (title) {
                    "完整循环歌名:$text"
                } else {
                    "完整循环歌手:$text"
                }
            },
    )
}

''',
    'one-way native title and artist marquee',
)

# One circular stroke plus one filled arrow head. No crossed short strokes / forked
# arrow, and single-loop only adds the small 1 in the same geometry.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.8.dp)) {
            val c = Color.White.copy(alpha = .78f)
            val stroke = 1.72.dp.toPx()
            drawArc(
                color = c,
                startAngle = 42f,
                sweepAngle = 286f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .86f, size.height * .28f)
                lineTo(size.width * .73f, size.height * .25f)
                lineTo(size.width * .81f, size.height * .39f)
                close()
            }
            drawPath(head, c)
        }
        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .78f),
                fontSize = 7.8.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'clean professional playback-mode glyph',
)

# Homepage top bar: same vertical grid and title scale as the library/search top bar.
sub(
    r'''            Box\(Modifier\.fillMaxWidth\(\)\.height\(if \(compact\) 47\.dp else 54\.dp\)\) \{.*?\n            \}\n\n(?=            Box\(\n                Modifier\n                    \.weight\(1f\))''',
    '''            Spacer(Modifier.height(AppTopGap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(AppTopBarHeight)
                    .semantics { contentDescription = "页面顶部:首页" }
            ) {
                Text(
                    "心动",
                    color = Color.White.copy(alpha = .84f),
                    fontSize = AppPageTitleSize,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
                QuietIconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(AppTopIconHit)
                        .semantics { contentDescription = "搜索" },
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .84f),
                        modifier = Modifier.size(AppTopIconSize),
                    )
                }
            }

''',
    'unified home top bar',
)

# Home content uses the same side inset as first-level light screens.
old = '.padding(horizontal = 24.dp),'
if old not in s:
    raise SystemExit('home side inset missing')
s = s.replace(old, '.padding(horizontal = AppPageSide),', 1)

# Rebuild player metadata: full one-way marquee for BOTH title and artist, with a
# fixed right-side control cluster and comfortable separation from text.
sub(
    r'''            Row\(\n                Modifier\n                    \.fillMaxWidth\(\)\n                    \.padding\(top = if \(compact\) 7\.dp else 11\.dp, bottom = 1\.dp\),\n                verticalAlignment = Alignment\.CenterVertically,\n            \) \{.*?\n            \}\n\n            Spacer\(Modifier\.height\(if \(compact\) 5\.dp else 8\.dp\)\)''',
    '''            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 8.dp else 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    PlayerMarqueeLine(
                        text = track.title,
                        title = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    PlayerMarqueeLine(
                        text = track.artist,
                        title = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    Modifier.width(100.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietIconButton(
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
                        PlaybackModeGlyph(
                            mode = playbackMode,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    QuietIconButton(
                        onClick = onQueue,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "播放列表" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .78f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 5.dp else 8.dp))''',
    'high-quality player metadata and controls',
)

# Remove Material ripple from the three central playback buttons as well.
s = s.replace(
    '''                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "上一首" },
                ) {''',
    '''                QuietIconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "上一首" },
                ) {''',
    1,
)
s = s.replace(
    '''                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(61.dp)
                        .semantics { contentDescription = if (isPlaying) "暂停" else "播放" },
                ) {''',
    '''                QuietIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(61.dp)
                        .semantics { contentDescription = if (isPlaying) "暂停" else "播放" },
                ) {''',
    1,
)
s = s.replace(
    '''                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "下一首" },
                ) {''',
    '''                QuietIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(47.dp).semantics { contentDescription = "下一首" },
                ) {''',
    1,
)

# Library top bar now shares the same top gap / 52dp frame / title size / icon sizes.
old = '''                    .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 7.dp),'''
if old not in s:
    raise SystemExit('library header inset missing')
s = s.replace(
    old,
    '''                    .padding(horizontal = AppPageSide)
                    .padding(top = AppTopGap)
                    .height(AppTopBarHeight)
                    .semantics { contentDescription = "页面顶部:音乐库" },''',
    1,
)
s = s.replace('fontSize = 18.5.sp,\n                    fontWeight = FontWeight.Medium,', 'fontSize = AppPageTitleSize,\n                    fontWeight = FontWeight.Medium,', 1)
s = s.replace('.size(43.dp)\n                        .semantics { contentDescription = "添加喜欢的音乐" }', '.size(AppTopIconHit)\n                        .semantics { contentDescription = "添加喜欢的音乐" }', 1)
s = s.replace('modifier = Modifier.size(21.dp),', 'modifier = Modifier.size(AppTopIconSize),', 1)

# List/search rows share one calm typography scale.
s = s.replace('fontSize = 14.sp,\n                    fontWeight = FontWeight.Medium,', 'fontSize = 15.2.sp,\n                    fontWeight = FontWeight.Medium,', 1)
s = s.replace('fontSize = 11.8.sp,', 'fontSize = 12.6.sp,', 1)

# Search top bar: same first-level screen frame. The helper sentence is removed
# completely, with no reserved blank line underneath the field.
old = '''                    .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 5.dp),'''
if old not in s:
    raise SystemExit('search header inset missing')
s = s.replace(
    old,
    '''                    .padding(horizontal = AppPageSide)
                    .padding(top = AppTopGap)
                    .height(AppTopBarHeight)
                    .semantics { contentDescription = "页面顶部:搜索" },''',
    1,
)
s = s.replace('.size(34.dp)\n                        .semantics { contentDescription = "返回" }', '.size(AppTopIconHit)\n                        .semantics { contentDescription = "返回" }', 1)
s = s.replace('modifier = Modifier.size(19.dp),', 'modifier = Modifier.size(AppTopIconSize),', 1)
s = s.replace('Spacer(Modifier.width(7.dp))', 'Spacer(Modifier.width(6.dp))', 1)
s = s.replace('.height(40.dp)\n                        .clip(RoundedCornerShape(20.dp))', '.height(42.dp)\n                        .clip(RoundedCornerShape(21.dp))', 1)
s = s.replace('modifier = Modifier.size(18.dp),', 'modifier = Modifier.size(20.dp),', 1)
s = s.replace('fontSize = 13.2.sp,', 'fontSize = 14.sp,', 2)

sub(
    r'''            if \(query\.isBlank\(\)\) \{\n                Text\(\n                    "只搜索你的歌曲",.*?\n                \)\n            \} else if \(results\.isEmpty\(\)\) \{\n                SubtleEmpty\("没有匹配的歌曲"\)\n            \} else \{''',
    '''            if (query.isNotBlank() && results.isEmpty()) {
                SubtleEmpty("没有匹配的歌曲")
            } else if (query.isNotBlank()) {''',
    'remove redundant search helper text',
)
s = s.replace('contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp),', 'contentPadding = PaddingValues(horizontal = AppPageSide, vertical = 8.dp),', 1)

ui.write_text(s)
