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


sub(
    r'@Composable\nprivate fun PlayerBackdrop\(track: Track, background: Color\) \{.*?\n\}\n\n(?=@Composable\nprivate fun LightBackdrop)',
    '''@Composable
private fun PlayerBackdrop(track: Track, background: Color) {
    Box(Modifier.fillMaxSize().background(background))
}

''',
    'pure player background',
)

sub(
    r'@Composable\nprivate fun LightBackdrop\(track: Track\) \{.*?\n\}\n\n(?=@Composable\nfun HomeScreen)',
    '''@Composable
private fun LightBackdrop(track: Track) {
    val tint by animateColorAsState(
        targetValue = track.theme.mix(Color.White, .935f),
        animationSpec = tween(700),
        label = "libraryTint",
    )
    Box(Modifier.fillMaxSize().background(tint))
}

''',
    'light library background',
)

sub(
    r'''\s{16}Column\(\n\s{20}Modifier\.align\(Alignment\.Center\),\n\s{20}horizontalAlignment = Alignment\.CenterHorizontally,\n\s{16}\) \{\n\s{20}Text\(\n\s{24}"心动",\n\s{24}color = Color\.White\.copy\(alpha = \.94f\),\n\s{24}fontSize = 17\.sp,\n\s{24}fontWeight = FontWeight\.Medium,\n\s{20}\)\n\s{20}Spacer\(Modifier\.height\(7\.dp\)\)\n\s{20}Box\(\n\s{24}Modifier\n\s{28}\.width\(31\.dp\)\n\s{28}\.height\(1\.4\.dp\)\n\s{28}\.background\(Color\.White\.copy\(alpha = \.88f\), CircleShape\)\n\s{20}\)\n\s{16}\}''',
    '''                Text(
                    "心动",
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.align(Alignment.Center),
                )''',
    'remove heart-mode underline',
)

old = 'val discSize = (maxWidth * .745f).coerceAtMost(if (compact) 248.dp else 270.dp)'
if old not in s:
    raise SystemExit('disc proportion source missing')
s = s.replace(old, 'val discSize = (maxWidth * .755f).coerceAtMost(if (compact) 250.dp else 274.dp)', 1)

old = '''.align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 2.dp),'''
if old not in s:
    raise SystemExit('tone-arm placement source missing')
s = s.replace(
    old,
    '''.align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 2.dp),''',
    1,
)

old = 'fontSize = 18.sp,\n                        fontWeight = FontWeight.Medium,'
if old not in s:
    raise SystemExit('home title typography source missing')
s = s.replace(old, 'fontSize = 16.5.sp,\n                        fontWeight = FontWeight.Medium,', 1)

old = 'color = Color.White.copy(alpha = .60f),\n                        fontSize = 13.5.sp,'
if old not in s:
    raise SystemExit('home artist typography source missing')
s = s.replace(old, 'color = Color.White.copy(alpha = .56f),\n                        fontSize = 12.2.sp,', 1)

sub(
    r'''Icon\(\n\s*Icons\.Rounded\.SkipPrevious,\n\s*contentDescription = null,\n\s*tint = Color\.White\.copy\(alpha = \.88f\),\n\s*modifier = Modifier\.size\(31\.dp\),\n\s*\)''',
    'TrackSkipGlyph(previous = true, modifier = Modifier.size(30.dp))',
    'custom previous glyph',
)
sub(
    r'''Icon\(\n\s*if \(isPlaying\) Icons\.Rounded\.Pause else Icons\.Rounded\.PlayArrow,\n\s*contentDescription = null,\n\s*tint = Color\.White\.copy\(alpha = \.92f\),\n\s*modifier = Modifier\.size\(if \(isPlaying\) 48\.dp else 51\.dp\),\n\s*\)''',
    'CenterPlaybackGlyph(isPlaying = isPlaying, modifier = Modifier.size(54.dp))',
    'custom center playback glyph',
)
sub(
    r'''Icon\(\n\s*Icons\.Rounded\.SkipNext,\n\s*contentDescription = null,\n\s*tint = Color\.White\.copy\(alpha = \.88f\),\n\s*modifier = Modifier\.size\(31\.dp\),\n\s*\)''',
    'TrackSkipGlyph(previous = false, modifier = Modifier.size(30.dp))',
    'custom next glyph',
)

marker = '@Composable\nprivate fun VinylDisc(track: Track, rotation: Float, modifier: Modifier = Modifier) {'
if marker not in s:
    raise SystemExit('glyph insertion marker missing')
helpers = '''@Composable
private fun TrackSkipGlyph(previous: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .86f)
        val barX = if (previous) size.width * .25f else size.width * .75f
        drawLine(
            color = c,
            start = Offset(barX, size.height * .27f),
            end = Offset(barX, size.height * .73f),
            strokeWidth = 3.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val p = androidx.compose.ui.graphics.Path()
        if (previous) {
            p.moveTo(size.width * .70f, size.height * .25f)
            p.lineTo(size.width * .34f, size.height * .50f)
            p.lineTo(size.width * .70f, size.height * .75f)
        } else {
            p.moveTo(size.width * .30f, size.height * .25f)
            p.lineTo(size.width * .66f, size.height * .50f)
            p.lineTo(size.width * .30f, size.height * .75f)
        }
        p.close()
        drawPath(p, c)
    }
}

@Composable
private fun CenterPlaybackGlyph(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .90f)
        if (isPlaying) {
            val stroke = 5.2.dp.toPx()
            drawLine(
                color = c,
                start = Offset(size.width * .39f, size.height * .28f),
                end = Offset(size.width * .39f, size.height * .72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = c,
                start = Offset(size.width * .61f, size.height * .28f),
                end = Offset(size.width * .61f, size.height * .72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
            val p = androidx.compose.ui.graphics.Path()
            p.moveTo(size.width * .35f, size.height * .23f)
            p.lineTo(size.width * .73f, size.height * .50f)
            p.lineTo(size.width * .35f, size.height * .77f)
            p.close()
            drawPath(p, c)
        }
    }
}

'''
s = s.replace(marker, helpers + marker, 1)

sub(
    r'@Composable\nprivate fun HomeBottomNav\(onLibrary: \(\) -> Unit, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun LibraryBottomNav)',
    '''@Composable
private fun HomeBottomNav(onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "首页",
            color = Color.White.copy(alpha = .92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "音乐库",
            color = Color.White.copy(alpha = .52f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable(onClick = onLibrary).padding(10.dp),
        )
    }
}

''',
    'home nav blend',
)

sub(
    r'@Composable\nprivate fun LibraryBottomNav\(onHome: \(\) -> Unit, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nfun LibraryScreen)',
    '''@Composable
private fun LibraryBottomNav(onHome: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "首页",
            color = MainText.copy(alpha = .44f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable(onClick = onHome).padding(10.dp),
        )
        Text(
            "音乐库",
            color = MainText.copy(alpha = .90f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

''',
    'library nav blend',
)

old_recent = 'val recent = recentIds.mapNotNull { id -> tracks.find { it.id == id } }.distinctBy { it.id }'
if old_recent not in s:
    raise SystemExit('recent expression missing')
s = s.replace(old_recent, old_recent + '.filterNot { it.id.startsWith("local:") }', 1)

old = 'fontSize = 20.5.sp,\n                    fontWeight = FontWeight.SemiBold,'
if old not in s:
    raise SystemExit('library title typography source missing')
s = s.replace(old, 'fontSize = 18.5.sp,\n                    fontWeight = FontWeight.Medium,', 1)

old = 'contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 92.dp),'
if old not in s:
    raise SystemExit('library bottom content padding source missing')
s = s.replace(old, 'contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),', 1)

sub(
    r'''\n\s*MiniPlayer\(\n\s*track = currentTrack,\n\s*isPlaying = isPlaying,\n\s*onPlayPause = onPlayPause,\n\s*onQueue = onQueue,\n\s*modifier = Modifier\.align\(Alignment\.BottomCenter\)\.padding\(bottom = nav \+ 63\.dp\),\n\s*\)''',
    '',
    'remove library mini player',
)

old = '.background(Color.White.copy(alpha = .56f))\n            .border(.6.dp, Color.White.copy(alpha = .60f), RoundedCornerShape(16.dp))'
if old not in s:
    raise SystemExit('favorite card background source missing')
s = s.replace(old, '.background(Color.White.copy(alpha = .34f))', 1)

old = 'fontSize = 15.5.sp,\n            fontWeight = FontWeight.Medium,'
if old not in s:
    raise SystemExit('favorite card typography source missing')
s = s.replace(old, 'fontSize = 14.2.sp,\n            fontWeight = FontWeight.Medium,', 1)

sub(
    r'(@Composable\nprivate fun SectionTitle\(title: String, count: Int\) \{.*?fontSize = )16\.sp,(\n\s*fontWeight = FontWeight\.)SemiBold,',
    r'\g<1>14.8.sp,\g<2>Medium,',
    'section title typography',
)
sub(
    r'(@Composable\nprivate fun TrackRow\(.*?track\.title,.*?fontSize = )14\.sp,(\n\s*fontWeight = FontWeight\.)Medium,',
    r'\g<1>13.4.sp,\g<2>Normal,',
    'track row title typography',
)
sub(
    r'(@Composable\nprivate fun TrackRow\(.*?track\.artist,.*?fontSize = )11\.8\.sp,',
    r'\g<1>11.3.sp,',
    'track row artist typography',
)

old = '.padding(bottom = nav + 74.dp)'
if old not in s:
    raise SystemExit('favorites bottom padding source missing')
s = s.replace(old, '.padding(bottom = nav + 12.dp)', 1)

sub(
    r'''\n\s*MiniPlayer\(\n\s*currentTrack,\n\s*isPlaying,\n\s*onPlayPause,\n\s*onQueue,\n\s*Modifier\.align\(Alignment\.BottomCenter\)\.padding\(bottom = nav \+ 10\.dp\),\n\s*\)''',
    '',
    'remove favorites mini player',
)

ui.write_text(s)

main = Path('app/src/main/java/com/immersive/music/MainActivity.kt')
m = main.read_text()
if 'needleOnDisc = needleOnDisc,' in m:
    m = m.replace('needleOnDisc = needleOnDisc,', 'needleOnDisc = playIntent,', 1)
main.write_text(m)
