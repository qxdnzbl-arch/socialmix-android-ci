from pathlib import Path
import re

ui = Path("app/src/main/java/com/immersive/music/MusicUi.kt")
s = ui.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f"{name}: expected {count}, replaced {n}")
    s = s2


# 1) Keep the user's pure-color rule, but deepen/saturate the theme toward
# the supplied NetEase player reference instead of leaving the flatter gray tone.
sub(
    r'@Composable\nprivate fun PlayerBackdrop\(track: Track, background: Color\) \{\n    Box\(Modifier\.fillMaxSize\(\)\.background\(background\)\)\n\}',
    '''private fun netEaseDeepColor(base: Color): Color {
    val peak = maxOf(base.red, base.green, base.blue)
    val saturation = 1.8f
    fun channel(value: Float): Float =
        ((peak - (peak - value) * saturation).coerceIn(0f, 1f) * .96f)
    return Color(
        channel(base.red),
        channel(base.green),
        channel(base.blue),
        base.alpha,
    )
}

@Composable
private fun PlayerBackdrop(track: Track, background: Color) {
    Box(Modifier.fillMaxSize().background(netEaseDeepColor(background)))
}''',
    "deeper pure player background",
)

# 2) NetEase reference progress thumb is materially smaller than the current one.
old = "radius = 4.6.dp.toPx(),"
if old not in s:
    raise SystemExit("seek thumb source missing")
s = s.replace(old, "radius = 3.0.dp.toPx(),", 1)

# 3) Time labels: lighter, narrower and slightly smaller.
old_left = 'Text(formatTime(positionMs), color = Color.White.copy(alpha = .41f), fontSize = 11.sp)'
old_right = 'Text(formatTime(durationMs), color = Color.White.copy(alpha = .41f), fontSize = 11.sp)'
if old_left not in s or old_right not in s:
    raise SystemExit("time typography source missing")
time_left = '''Text(
                    formatTime(positionMs),
                    color = Color.White.copy(alpha = .39f),
                    fontSize = 10.4.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-.18).sp,
                )'''
time_right = '''Text(
                    formatTime(durationMs),
                    color = Color.White.copy(alpha = .39f),
                    fontSize = 10.4.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-.18).sp,
                )'''
s = s.replace(old_left, time_left, 1).replace(old_right, time_right, 1)

# 4) Redraw all triangular playback glyphs with rounded corners and NetEase-like
# proportions instead of sharp polygon corners / Material defaults.
sub(
    r'@Composable\nprivate fun TrackSkipGlyph\(previous: Boolean, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun CenterPlaybackGlyph)',
    '''@Composable
private fun TrackSkipGlyph(previous: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .86f)
        val barX = if (previous) size.width * .27f else size.width * .73f
        drawLine(
            color = c,
            start = Offset(barX, size.height * .28f),
            end = Offset(barX, size.height * .72f),
            strokeWidth = 3.25.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val p = androidx.compose.ui.graphics.Path()
        if (previous) {
            p.moveTo(size.width * .31f, size.height * .47f)
            p.quadraticBezierTo(
                size.width * .28f, size.height * .50f,
                size.width * .31f, size.height * .53f,
            )
            p.lineTo(size.width * .67f, size.height * .76f)
            p.quadraticBezierTo(
                size.width * .72f, size.height * .79f,
                size.width * .72f, size.height * .72f,
            )
            p.lineTo(size.width * .72f, size.height * .28f)
            p.quadraticBezierTo(
                size.width * .72f, size.height * .21f,
                size.width * .67f, size.height * .24f,
            )
            p.close()
        } else {
            p.moveTo(size.width * .69f, size.height * .47f)
            p.quadraticBezierTo(
                size.width * .72f, size.height * .50f,
                size.width * .69f, size.height * .53f,
            )
            p.lineTo(size.width * .33f, size.height * .76f)
            p.quadraticBezierTo(
                size.width * .28f, size.height * .79f,
                size.width * .28f, size.height * .72f,
            )
            p.lineTo(size.width * .28f, size.height * .28f)
            p.quadraticBezierTo(
                size.width * .28f, size.height * .21f,
                size.width * .33f, size.height * .24f,
            )
            p.close()
        }
        drawPath(p, c)
    }
}

''',
    "rounded skip glyph",
)

sub(
    r'@Composable\nprivate fun CenterPlaybackGlyph\(isPlaying: Boolean, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)',
    '''@Composable
private fun CenterPlaybackGlyph(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .88f)
        if (isPlaying) {
            val stroke = 5.2.dp.toPx()
            drawLine(
                color = c,
                start = Offset(size.width * .39f, size.height * .25f),
                end = Offset(size.width * .39f, size.height * .75f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = c,
                start = Offset(size.width * .61f, size.height * .25f),
                end = Offset(size.width * .61f, size.height * .75f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
            val p = androidx.compose.ui.graphics.Path()
            p.moveTo(size.width * .28f, size.height * .25f)
            p.quadraticBezierTo(
                size.width * .28f, size.height * .17f,
                size.width * .35f, size.height * .21f,
            )
            p.lineTo(size.width * .77f, size.height * .46f)
            p.quadraticBezierTo(
                size.width * .85f, size.height * .50f,
                size.width * .77f, size.height * .55f,
            )
            p.lineTo(size.width * .35f, size.height * .79f)
            p.quadraticBezierTo(
                size.width * .28f, size.height * .83f,
                size.width * .28f, size.height * .75f,
            )
            p.close()
            drawPath(p, c)
        }
    }
}

''',
    "rounded center playback glyph",
)

# 5) Blacker, glossier physical-vinyl treatment while preserving cover ratio.
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
                ambientColor = Color.Black.copy(alpha = .42f),
                spotColor = Color.Black.copy(alpha = .44f),
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF151615),
                        Color(0xFF060706),
                        Color(0xFF010201),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF020302), r)

            repeat(56) { i ->
                val rr = r * (.495f + i * .00865f)
                drawCircle(
                    color = if (i % 4 == 0) {
                        Color.White.copy(alpha = .036f)
                    } else {
                        Color.Black.copy(alpha = .58f)
                    },
                    radius = rr,
                    style = Stroke(width = .50.dp.toPx()),
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = .040f),
                radius = r * .968f,
                style = Stroke(width = .8.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = .68f),
                radius = r * .758f,
                style = Stroke(width = 1.1.dp.toPx()),
            )

            drawArc(
                color = Color.White.copy(alpha = .075f),
                startAngle = 205f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .045f),
                startAngle = 28f,
                sweepAngle = 68f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .022f),
                startAngle = 123f,
                sweepAngle = 42f,
                useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Box(
            Modifier
                .size(maxWidth * .655f)
                .clip(CircleShape)
                .border(.7.dp, Color.Black.copy(alpha = .62f), CircleShape),
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
    "physical black vinyl",
)

# 6) One shared bottom-nav geometry on both screens. Fixed-width hit areas prevent
# selected/unselected labels from shifting. clickable(indication=null) keeps accessibility
# semantics while removing the visible Material ripple rectangle.
import_line = "import androidx.compose.foundation.interaction.MutableInteractionSource\n"
if import_line not in s:
    anchor = "import androidx.compose.foundation.gestures.detectTapGestures\n"
    if anchor not in s:
        raise SystemExit("interaction import anchor missing")
    s = s.replace(anchor, anchor + import_line, 1)

sub(
    r'@Composable\nprivate fun HomeBottomNav\(onLibrary: \(\) -> Unit, modifier: Modifier = Modifier\) \{.*?\n\}\n\n@Composable\nprivate fun LibraryBottomNav\(onHome: \(\) -> Unit, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nfun LibraryScreen)',
    '''@Composable
private fun UnifiedBottomNav(
    selectedHome: Boolean,
    foreground: Color,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 57.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BottomNavLabel(
            text = "首页",
            selected = selectedHome,
            foreground = foreground,
            onClick = onHome,
        )
        BottomNavLabel(
            text = "音乐库",
            selected = !selectedHome,
            foreground = foreground,
            onClick = onLibrary,
        )
    }
}

@Composable
private fun BottomNavLabel(
    text: String,
    selected: Boolean,
    foreground: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tap = if (selected) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    }
    Box(
        Modifier
            .width(82.dp)
            .height(60.dp)
            .then(tap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = foreground.copy(alpha = if (selected) .92f else .50f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun HomeBottomNav(onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    UnifiedBottomNav(
        selectedHome = true,
        foreground = Color.White,
        onHome = {},
        onLibrary = onLibrary,
        modifier = modifier,
    )
}

@Composable
private fun LibraryBottomNav(onHome: () -> Unit, modifier: Modifier = Modifier) {
    UnifiedBottomNav(
        selectedHome = false,
        foreground = MainText,
        onHome = onHome,
        onLibrary = {},
        modifier = modifier,
    )
}

''',
    "unified ripple-free bottom nav",
)

# 7) User explicitly removed Recently Played: title, count, empty state and rows.
sub(
    r'\n\s*item \{ SectionTitle\("最近播放", recent\.size\) \}.*?(?=\n\s*item \{ SectionTitle\("本地音乐", local\.size\) \})',
    "\n",
    "remove recent played section",
)

ui.write_text(s)
