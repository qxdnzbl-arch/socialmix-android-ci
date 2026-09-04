from pathlib import Path
import re

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'{name}: expected {count}, replaced {n}')
    s = s2


# v108 fixes ONLY the two regressions reported after v107:
# 1) the loop line must stop before the filled triangle instead of running through it;
# 2) the vinyl center must show the artwork again while keeping the extra spindle dot removed.

playback_fn = r'''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .68f)
            val vw = 20.8.dp.toPx()
            val vh = (20.8f * 41f / 45f).dp.toPx()
            val left = (size.width - vw) / 2f
            val top = (size.height - vh) / 2f
            val line = 1.22.dp.toPx()

            fun px(x: Float) = left + vw * (x / 44f)
            fun py(y: Float) = top + vh * (y / 40f)

            val loop = androidx.compose.ui.graphics.Path().apply {
                // End the upper return stroke BEFORE the triangle.  x=25 -> x=27
                // leaves ~0.95dp optical gap at the established 20.8dp glyph width.
                moveTo(px(25f), py(5f))
                lineTo(px(17f), py(5f))
                cubicTo(px(8f), py(5f), px(3f), py(11f), px(3f), py(20f))
                cubicTo(px(3f), py(30f), px(10f), py(36f), px(20f), py(36f))
                lineTo(px(27f), py(36f))
                cubicTo(px(35f), py(36f), px(40f), py(31f), px(40f), py(24f))
            }
            drawPath(loop, c, style = Stroke(width = line, cap = StrokeCap.Round))

            // Keep the approved 6.1x5.7dp triangle and right-edge alignment.
            val arrow = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(27f), py(0f))
                lineTo(px(40f), py(6f))
                lineTo(px(27f), py(12f))
                close()
            }
            drawPath(arrow, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .68f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
'''
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    playback_fn + '\n',
    'v108 loop triangle clearance',
)


vinyl_fn = r'''@Composable
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

        // Restore the artwork layer that v107 accidentally swallowed while
        // removing the center spindle decoration.  Keep exactly the established
        // 65.5% label/cover diameter and crop behavior.
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

        // Deliberately no white spindle disc and no dark center dot.
    }
}
'''
sub(
    r'''@Composable\nprivate fun VinylDisc\(track: Track, rotation: Float, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun NetEaseQueueGlyph)''',
    vinyl_fn + '\n',
    'v108 restore vinyl artwork',
)

# Hard guards: refuse to ship if either regression is still represented in source.
if 'moveTo(px(25f), py(5f))' not in s or 'moveTo(px(27f), py(0f))' not in s:
    raise SystemExit('v108 loop geometry guard failed')
vinyl_block = s[s.index('@Composable\nprivate fun VinylDisc'):s.index('@Composable\nprivate fun NetEaseQueueGlyph')]
if 'rememberCoverBitmap(track)' not in vinyl_block or 'Image(' not in vinyl_block or 'DemoArtwork(track)' not in vinyl_block:
    raise SystemExit('v108 artwork restoration guard failed')
if '.size(13.dp)' in vinyl_block or '.size(4.4.dp)' in vinyl_block:
    raise SystemExit('v108 center spindle regression detected')

path.write_text(s)
